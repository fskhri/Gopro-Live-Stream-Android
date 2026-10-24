package com.gopro.rebuild.media

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

private const val TAG = "MicrophoneStreamer"
const val MIC_RELAY_PORT = 8557
const val MIC_SAMPLE_RATE = 44100

@Singleton
class MicrophoneStreamer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recordJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var udpSocket: DatagramSocket? = null

    private val _volumeLevel = MutableStateFlow(0f)
    val volumeLevel: StateFlow<Float> = _volumeLevel.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
    }

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(): Boolean {
        if (_isRecording.value) return true
        if (!hasPermission()) {
            Log.w(TAG, "RECORD_AUDIO permission not granted")
            return false
        }

        try {
            val minBufSize = AudioRecord.getMinBufferSize(
                MIC_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufSize, 2048)

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                MIC_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                record.release()
                return false
            }

            record.startRecording()
            audioRecord = record

            val socket = DatagramSocket()
            udpSocket = socket

            _isRecording.value = true
            Log.i(TAG, "Microphone recording started (44100Hz, mono, port $MIC_RELAY_PORT)")

            recordJob = scope.launch {
                val loopback = InetAddress.getByName("127.0.0.1")
                val pcmBuffer = ByteArray(bufferSize)

                while (isActive && _isRecording.value) {
                    val readBytes = record.read(pcmBuffer, 0, pcmBuffer.size)
                    if (readBytes > 0) {
                        // Calculate peak amplitude for volume VU meter
                        var maxPeak = 0
                        for (i in 0 until readBytes step 2) {
                            val sample = ((pcmBuffer[i + 1].toInt() shl 8) or (pcmBuffer[i].toInt() and 0xFF)).toShort()
                            val peak = abs(sample.toInt())
                            if (peak > maxPeak) maxPeak = peak
                        }

                        val muted = _isMuted.value
                        val normalized = if (muted) 0f else (maxPeak / 32767f).coerceIn(0f, 1f)

                        // Smooth response: fast attack, smooth decay
                        val prev = _volumeLevel.value
                        val smoothed = if (normalized > prev) {
                            normalized
                        } else {
                            prev * 0.75f + normalized * 0.25f
                        }
                        _volumeLevel.value = smoothed

                        // If muted, silence buffer before sending to FFmpeg
                        if (muted) {
                            pcmBuffer.fill(0, 0, readBytes)
                        }

                        try {
                            val packet = DatagramPacket(pcmBuffer, 0, readBytes, loopback, MIC_RELAY_PORT)
                            socket.send(packet)
                        } catch (e: Exception) {
                            if (isActive) Log.w(TAG, "UDP send failed: ${e.message}")
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start microphone streaming: ${e.message}", e)
            stop()
            return false
        }
    }

    @Synchronized
    fun stop() {
        _isRecording.value = false
        _volumeLevel.value = 0f

        recordJob?.cancel()
        recordJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        audioRecord = null

        try {
            udpSocket?.close()
        } catch (_: Exception) {}
        udpSocket = null

        Log.i(TAG, "Microphone recording stopped")
    }
}
