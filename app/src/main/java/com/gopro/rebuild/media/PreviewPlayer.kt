package com.gopro.rebuild.media

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.gopro.rebuild.wifi.WifiConnectionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PreviewPlayer"

/**
 * Live preview player for the GoPro Hero 6 UDP 8554 MPEG-TS stream.
 *
 * Uses a custom [GoProUdpDataSource] (the camera wraps TS packets in a 12-byte
 * GoPro header) plus low-latency buffer tuning and a UDP keepalive heartbeat.
 */
@Singleton
class PreviewPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wifiConnectionManager: WifiConnectionManager
) {
    private var player: ExoPlayer? = null
    private var keepAliveJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _exoPlayerFlow = kotlinx.coroutines.flow.MutableStateFlow<ExoPlayer?>(null)
    val exoPlayerFlow: kotlinx.coroutines.flow.StateFlow<ExoPlayer?> = _exoPlayerFlow.asStateFlow()

    private val _audioVolumeLevel = kotlinx.coroutines.flow.MutableStateFlow(0f)
    val audioVolumeLevel: kotlinx.coroutines.flow.StateFlow<Float> = _audioVolumeLevel.asStateFlow()

    private val _isSpeakerMuted = kotlinx.coroutines.flow.MutableStateFlow(true)
    val isSpeakerMuted: kotlinx.coroutines.flow.StateFlow<Boolean> = _isSpeakerMuted.asStateFlow()

    fun toggleSpeakerMute() {
        val next = !_isSpeakerMuted.value
        _isSpeakerMuted.value = next
        player?.volume = if (next) 0f else 1f
    }

    val exoPlayer: ExoPlayer?
        get() = player

    /** External files dir used for the debug local-file preview harness. */
    fun debugFileDir(): java.io.File? = context.getExternalFilesDir(null)

    @OptIn(UnstableApi::class)
    fun initialize() {
        if (player == null) {
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 300,
                    /* maxBufferMs = */ 1000,
                    /* bufferForPlaybackMs = */ 100,
                    /* bufferForPlaybackAfterRebufferMs = */ 200
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val teeProcessor = TeeAudioProcessor(object : TeeAudioProcessor.AudioBufferSink {
                override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {}
                override fun handleBuffer(buffer: java.nio.ByteBuffer) {
                    val remaining = buffer.remaining()
                    if (remaining <= 0) return
                    val pos = buffer.position()
                    var maxPeak = 0
                    var i = pos
                    while (i < pos + remaining - 1) {
                        val low = buffer.get(i).toInt() and 0xFF
                        val high = buffer.get(i + 1).toInt()
                        val sample = ((high shl 8) or low).toShort()
                        val peak = kotlin.math.abs(sample.toInt())
                        if (peak > maxPeak) maxPeak = peak
                        i += 2
                    }
                    val normalized = (maxPeak / 32767f).coerceIn(0f, 1f)
                    val prev = _audioVolumeLevel.value
                    val smoothed = if (normalized > prev) {
                        normalized
                    } else {
                        prev * 0.7f + normalized * 0.3f
                    }
                    _audioVolumeLevel.value = smoothed
                }
            })

            val renderersFactory = object : DefaultRenderersFactory(context) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean
                ): AudioSink {
                    return DefaultAudioSink.Builder(context)
                        .setAudioProcessors(arrayOf(teeProcessor))
                        .build()
                }
            }

            player = ExoPlayer.Builder(context, renderersFactory)
                .setLoadControl(loadControl)
                .build()
            player?.volume = if (_isSpeakerMuted.value) 0f else 1f
            player?.addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e(TAG, "ExoPlayer error: ${error.message}", error)
                }

                override fun onPlaybackStateChanged(state: Int) {
                    Log.d(TAG, "ExoPlayer state changed: $state")
                }

                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    Log.d(TAG, "tracks changed: groups=${tracks.groups.size} " +
                        tracks.groups.joinToString { g ->
                            "type=${g.type} len=${g.length}"
                        })
                }

                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    Log.d(TAG, "videoSize: ${videoSize.width}x${videoSize.height}")
                }

                override fun onRenderedFirstFrame() {
                    Log.d(TAG, "rendered first frame")
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Log.d(TAG, "ExoPlayer isPlaying=$isPlaying")
                }
            })
            _exoPlayerFlow.value = player
        }
    }

    @OptIn(UnstableApi::class)
    fun startRelayedPreview(port: Int = RELAY_PORT_PREVIEW) {
        initialize()
        stopKeepAlive() // Keepalive already handled by GoProUdpRelay

        val dataSourceFactory = DataSource.Factory {
            GoProUdpDataSource(
                port = port,
                network = null,
                dumpDir = null,
                isRawTs = true
            )
        }

        val effectiveUrl = "udp://127.0.0.1:$port"
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(effectiveUrl))
            .setMimeType(MimeTypes.VIDEO_MP2T)
            .build()

        val mediaSource: MediaSource = ProgressiveMediaSource.Factory(
            dataSourceFactory,
            GoProTsExtractorFactory()
        ).createMediaSource(mediaItem)

        player?.apply {
            Log.d(TAG, "startRelayedPreview url=$effectiveUrl")
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
    }

    @OptIn(UnstableApi::class)
    fun startPreview(streamUrl: String = "udp://0.0.0.0:8554", cameraIp: String = "10.5.5.9", port: Int = 8554) {
        initialize()
        startKeepAlive(cameraIp, port)

        val localDebugFile = java.io.File(context.getExternalFilesDir(null), "debug_local.ts")
        val useLocalFile = localDebugFile.exists()
        if (useLocalFile) Log.w(TAG, "DEBUG: playing local file $localDebugFile instead of live stream")

        val dataSourceFactory = if (useLocalFile) {
            DefaultDataSource.Factory(context)
        } else {
            DataSource.Factory {
                GoProUdpDataSource(
                    port = port,
                    network = wifiConnectionManager.getActiveNetwork(),
                    dumpDir = context.getExternalFilesDir(null)
                )
            }
        }

        val effectiveUrl = if (useLocalFile) Uri.fromFile(localDebugFile).toString() else streamUrl

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(effectiveUrl))
            .setMimeType(MimeTypes.VIDEO_MP2T)
            .build()

        val mediaSource: MediaSource = ProgressiveMediaSource.Factory(
            dataSourceFactory,
            GoProTsExtractorFactory()
        )
            .createMediaSource(mediaItem)

        player?.apply {
            Log.d(TAG, "setMediaSource url=$effectiveUrl mime=${MimeTypes.VIDEO_MP2T}")
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }

        scope.launch {
            delay(8000)
            withContext(Dispatchers.Main) {
                val p = player ?: return@withContext
                Log.d(
                    TAG,
                    "diag: state=${p.playbackState} tracks=${p.currentTracks.groups.size} " +
                        "dur=${p.duration} videoSize=${p.videoSize.width}x${p.videoSize.height} " +
                        "playWhenReady=${p.playWhenReady} isPlaying=${p.isPlaying} " +
                        "buffered=${p.bufferedPosition} playing=${p.currentPosition}"
                )
            }
        }
    }

    /**
     * GoPro Hero 4/5/6/7 requires a periodic UDP heartbeat on port 8554 to keep streaming.
     */
    private fun startKeepAlive(cameraIp: String, port: Int) {
        stopKeepAlive()
        keepAliveJob = scope.launch {
            var socket: DatagramSocket? = null
            try {
                val targetAddr = InetSocketAddress(cameraIp, port)
                val payload = "_GPHD_:0:0:2:0.000000\n".toByteArray(Charsets.US_ASCII)
                val packet = DatagramPacket(payload, payload.size, targetAddr)

                Log.d(TAG, "Starting UDP stream keepalive to $cameraIp:$port")
                while (isActive) {
                    try {
                        if (socket == null || socket.isClosed) {
                            socket = DatagramSocket()
                            wifiConnectionManager.getActiveNetwork()?.bindSocket(socket)
                        }
                        socket.send(packet)
                    } catch (e: Exception) {
                        Log.w(TAG, "Keepalive packet send error: ${e.message}")
                        socket?.close()
                        socket = null
                    }
                    delay(2000)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Keepalive socket setup failed: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    fun stopPreview() {
        stopKeepAlive()
        player?.stop()
        player?.clearMediaItems()
    }

    fun release() {
        stopKeepAlive()
        player?.release()
        player = null
        _exoPlayerFlow.value = null
    }

    fun isPlaying(): Boolean = player?.isPlaying == true
}
