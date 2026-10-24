package com.gopro.rebuild.media

import android.util.Log
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFmpegSession
import com.antonkarpenko.ffmpegkit.ReturnCode
import com.gopro.rebuild.tiktok.RelayStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RtmpStreamer"

data class StreamerStats(
    val status: RelayStatus = RelayStatus.IDLE,
    val bitrateKbps: Double = 0.0,
    val fps: Double = 0.0,
    val durationSeconds: Long = 0,
    val error: String? = null
)

@Singleton
class RtmpStreamer @Inject constructor(
    private val udpRelay: GoProUdpRelay
) {
    private var currentSession: FFmpegSession? = null

    private val _streamStats = MutableStateFlow(StreamerStats())
    val streamStats: StateFlow<StreamerStats> = _streamStats.asStateFlow()

    fun startStream(rtmpUrl: String, onFinished: (Boolean, String?) -> Unit = { _, _ -> }) {
        if (_streamStats.value.status == RelayStatus.STREAMING ||
            _streamStats.value.status == RelayStatus.STARTING) {
            Log.w(TAG, "Stream already active or starting")
            return
        }

        _streamStats.value = StreamerStats(status = RelayStatus.STARTING)

        // 1. Ensure UDP relay is running
        val relayOk = udpRelay.start()
        if (!relayOk) {
            _streamStats.value = StreamerStats(
                status = RelayStatus.ERROR,
                error = "Gagal menyalakan UDP relay GoPro (port 8554)"
            )
            onFinished(false, "Relay start failed")
            return
        }

        // 2. Build FFmpeg command to push GoPro Video & Internal Mic Audio to TikTok RTMP
        // Input: GoPro MPEG-TS (port 8555)
        // Video 0:v:0: pass-through copy
        // Audio 0:a:0: GoPro internal mic encoded to TikTok-compliant 44.1kHz AAC stereo
        val cmd = "-analyzeduration 2000000 -probesize 2000000 " +
                "-i udp://127.0.0.1:8555?overrun_nonfatal=1&fifo_size=5000000 " +
                "-map 0:v:0 -map 0:a:0? " +
                "-c:v copy " +
                "-c:a aac -b:a 128k -ar 44100 -ac 2 " +
                "-af aresample=async=1000 " +
                "-flvflags no_duration_filesize " +
                "-f flv \"$rtmpUrl\""

        Log.i(TAG, "Executing FFmpegKit async: $cmd")

        currentSession = FFmpegKit.executeAsync(
            cmd,
            { session ->
                val returnCode = session.returnCode
                val success = ReturnCode.isSuccess(returnCode)
                val fail = session.failStackTrace

                Log.i(TAG, "FFmpeg session ended: returnCode=$returnCode")

                if (success) {
                    _streamStats.value = StreamerStats(status = RelayStatus.IDLE)
                    onFinished(true, null)
                } else if (ReturnCode.isCancel(returnCode)) {
                    _streamStats.value = StreamerStats(status = RelayStatus.IDLE)
                    onFinished(true, "Stream cancelled")
                } else {
                    val errMsg = fail ?: "Stream terputus (code $returnCode)"
                    _streamStats.value = StreamerStats(
                        status = RelayStatus.ERROR,
                        error = errMsg
                    )
                    onFinished(false, errMsg)
                }
            },
            { log ->
                val text = log.message
                if (text.contains("error", ignoreCase = true) || text.contains("fatal", ignoreCase = true)) {
                    Log.w(TAG, "[FFmpeg Error] $text")
                } else {
                    Log.d(TAG, "[FFmpeg] $text")
                }
            },
            { stats ->
                if (_streamStats.value.status != RelayStatus.STREAMING) {
                    _streamStats.value = _streamStats.value.copy(status = RelayStatus.STREAMING)
                }
                val durationSec = (stats.time / 1000.0).toLong().coerceAtLeast(0L)
                _streamStats.value = _streamStats.value.copy(
                    bitrateKbps = stats.bitrate,
                    fps = stats.videoFps.toDouble(),
                    durationSeconds = durationSec
                )
            }
        )
    }

    fun stopStream() {
        _streamStats.value = _streamStats.value.copy(status = RelayStatus.STOPPING)
        try {
            currentSession?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling FFmpeg session: ${e.message}")
        }
        currentSession = null
        _streamStats.value = StreamerStats(status = RelayStatus.IDLE)
        Log.i(TAG, "Stream stopped by user")
    }
}
