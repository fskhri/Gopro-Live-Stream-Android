package com.gopro.rebuild.media

import android.net.Network
import android.util.Log
import com.gopro.rebuild.wifi.WifiConnectionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GoProUdpRelay"
private const val CAMERA_IP = "10.5.5.9"
const val CAMERA_PORT = 8554
const val RELAY_PORT_FFMPEG = 8555
const val RELAY_PORT_PREVIEW = 8556
private const val KEEPALIVE_CMD = "_GPHD_:0:0:2:0.000000\n"
private const val KEEPALIVE_INTERVAL_MS = 2500L
private const val GOPRO_HEADER_SIZE = 12
private const val TS_SYNC = 0x47.toByte()
private const val MAX_DATAGRAM_SIZE = 2048

data class RelayStats(
    val isRunning: Boolean = false,
    val packetsReceived: Long = 0,
    val bytesRelayed: Long = 0,
    val error: String? = null
)

@Singleton
class GoProUdpRelay @Inject constructor(
    private val wifiConnectionManager: WifiConnectionManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var inSocket: DatagramSocket? = null
    private var outSocket: DatagramSocket? = null
    private var relayJob: Job? = null
    private var keepAliveJob: Job? = null

    private val _stats = MutableStateFlow(RelayStats())
    val stats: StateFlow<RelayStats> = _stats.asStateFlow()

    @Synchronized
    fun start(): Boolean {
        if (relayJob?.isActive == true) return true
        stop()

        val cameraNetwork = wifiConnectionManager.getActiveNetwork()
        if (cameraNetwork == null) {
            Log.w(TAG, "No GoPro active network available to bind UDP socket")
        }

        try {
            val sockIn = DatagramSocket(null).apply {
                reuseAddress = true
                cameraNetwork?.bindSocket(this)
                bind(InetSocketAddress(CAMERA_PORT))
                soTimeout = 1000
            }
            val sockOut = DatagramSocket()

            inSocket = sockIn
            outSocket = sockOut
            _stats.value = RelayStats(isRunning = true)

            startKeepAlive(cameraNetwork)
            startRelayLoop(sockIn, sockOut)

            Log.i(TAG, "GoPro UDP Relay started (port $CAMERA_PORT -> $RELAY_PORT_FFMPEG)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start UDP relay: ${e.message}", e)
            _stats.value = RelayStats(isRunning = false, error = e.message)
            stop()
            return false
        }
    }

    private fun startKeepAlive(network: Network?) {
        keepAliveJob = scope.launch {
            val raw = KEEPALIVE_CMD.toByteArray(Charsets.US_ASCII)
            val addr = InetAddress.getByName(CAMERA_IP)
            val packet = DatagramPacket(raw, raw.size, addr, CAMERA_PORT)

            while (isActive) {
                try {
                    val kaSocket = DatagramSocket()
                    try {
                        network?.bindSocket(kaSocket)
                        kaSocket.send(packet)
                    } finally {
                        kaSocket.close()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Keepalive ping failed: ${e.message}")
                }
                delay(KEEPALIVE_INTERVAL_MS)
            }
        }
    }

    private fun startRelayLoop(sockIn: DatagramSocket, sockOut: DatagramSocket) {
        relayJob = scope.launch {
            val recvBuf = ByteArray(MAX_DATAGRAM_SIZE)
            val packet = DatagramPacket(recvBuf, recvBuf.size)
            val loopback = InetAddress.getByName("127.0.0.1")

            var totalPackets = 0L
            var totalBytes = 0L

            while (isActive) {
                try {
                    sockIn.receive(packet)
                    val len = packet.length
                    if (len <= GOPRO_HEADER_SIZE) continue

                    // Parse GoPro header
                    // byte[10..11]: payload length big-endian
                    val declaredLen = ((recvBuf[10].toInt() and 0xFF) shl 8) or
                            (recvBuf[11].toInt() and 0xFF)
                    val available = len - GOPRO_HEADER_SIZE
                    val payloadLen = if (declaredLen in 1..available) declaredLen else available

                    // Verify MPEG-TS sync byte 0x47
                    if (recvBuf[GOPRO_HEADER_SIZE] == TS_SYNC) {
                        val outPacketFfmpeg = DatagramPacket(
                            recvBuf,
                            GOPRO_HEADER_SIZE,
                            payloadLen,
                            loopback,
                            RELAY_PORT_FFMPEG
                        )
                        sockOut.send(outPacketFfmpeg)

                        val outPacketPreview = DatagramPacket(
                            recvBuf,
                            GOPRO_HEADER_SIZE,
                            payloadLen,
                            loopback,
                            RELAY_PORT_PREVIEW
                        )
                        try {
                            sockOut.send(outPacketPreview)
                        } catch (_: Exception) {}

                        totalPackets++
                        totalBytes += payloadLen

                        if (totalPackets % 200L == 0L) {
                            _stats.value = _stats.value.copy(
                                packetsReceived = totalPackets,
                                bytesRelayed = totalBytes
                            )
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    // Normal when camera hasn't sent data yet
                } catch (e: Exception) {
                    if (isActive) {
                        Log.e(TAG, "Relay loop exception: ${e.message}")
                        delay(200)
                    }
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        relayJob?.cancel()
        relayJob = null
        keepAliveJob?.cancel()
        keepAliveJob = null

        try { inSocket?.close() } catch (_: Exception) {}
        try { outSocket?.close() } catch (_: Exception) {}
        inSocket = null
        outSocket = null

        _stats.value = _stats.value.copy(isRunning = false)
        Log.i(TAG, "GoPro UDP Relay stopped")
    }
}
