package com.gopro.rebuild.media

import android.net.Network
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

private const val TAG = "GoProUdpDataSource"

/** GoPro preview datagram header: 12 bytes, then MPEG-TS. */
private const val GOPRO_HEADER_SIZE = 12
private const val MAX_DATAGRAM = 2048
private const val TS_SYNC = 0x47.toByte()

/**
 * Reads the GoPro Hero 6 (gpControl) live preview stream.
 *
 * The camera sends fixed 1328-byte UDP datagrams to the client's port 8554.
 * Each datagram is a 12-byte GoPro header followed by MPEG-TS packets:
 *  - byte[9]     : packet sequence counter (wraps 0..255)
 *  - byte[10..11]: big-endian payload length in bytes (a multiple of 188)
 *  - byte[12..]  : MPEG-TS payload (7 x 188 bytes max), zero-padded
 *
 * Raw datagrams are not a valid MPEG-TS stream (the header breaks the 0x47
 * sync pattern), so the header is stripped here before handing bytes to the
 * TsExtractor.
 */
@UnstableApi
class GoProUdpDataSource(
    private val port: Int,
    private val network: Network?,
    private val socketTimeoutMs: Int = 1000,
    private val dumpDir: java.io.File? = null,
    private val isRawTs: Boolean = false
) : BaseDataSource(/* isNetwork = */ true) {

    private var socket: DatagramSocket? = null
    private var uri: Uri? = null

    private val packetBuffer = ByteArray(MAX_DATAGRAM)
    private val receivePacket = DatagramPacket(packetBuffer, packetBuffer.size)

    private var payloadPos = 0
    private var payloadEnd = 0
    private var datagramCount = 0L
    private var bytesRead = 0L
    private var dumpStream: java.io.OutputStream? = null
    private var dumpedBytes = 0L

    @Volatile
    private var closed = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)

        val sock = DatagramSocket(null)
        sock.reuseAddress = true
        network?.bindSocket(sock)
        sock.bind(InetSocketAddress(port))
        sock.soTimeout = socketTimeoutMs

        socket = sock
        closed = false
        payloadPos = 0
        payloadEnd = 0
        datagramCount = 0
        bytesRead = 0
        dumpedBytes = 0
        try {
            val dir = dumpDir ?: throw IllegalStateException("no dump dir")
            val f = java.io.File(dir, "delivered_${System.currentTimeMillis()}.ts")
            dumpStream = java.io.BufferedOutputStream(java.io.FileOutputStream(f, false))
            Log.d(TAG, "dumping delivered payload to ${f.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "cannot open dump file: ${e.message}")
        }

        transferStarted(dataSpec)
        Log.d(TAG, "opened UDP $port (networkBound=${network != null})")
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        while (true) {
            if (closed) return C.RESULT_END_OF_INPUT

            if (payloadPos < payloadEnd) {
                val n = minOf(length, payloadEnd - payloadPos)
                System.arraycopy(packetBuffer, payloadPos, buffer, offset, n)
                payloadPos += n
                bytesRead += n
                if (dumpedBytes < 800_000) {
                    try {
                        val dumpLen = minOf(n.toLong(), 800_000 - dumpedBytes).toInt()
                        dumpStream?.write(buffer, offset, dumpLen)
                        dumpedBytes += dumpLen
                    } catch (_: Exception) {}
                }
                bytesTransferred(n)
                if (bytesRead <= n || bytesRead % 188000 < n) {
                    Log.d(TAG, "read: datagrams=$datagramCount bytes=$bytesRead lastDeliver=$n")
                }
                return n
            }

            try {
                socket?.receive(receivePacket)
            } catch (e: SocketTimeoutException) {
                continue
            } catch (e: IOException) {
                if (closed) return C.RESULT_END_OF_INPUT
                throw e
            }

            val len = receivePacket.length
            datagramCount++
            if (datagramCount == 1L) {
                Log.d(TAG, "first datagram len=$len hdr=${packetBuffer.copyOfRange(0, 12).joinToString(" ") { String.format("%02x", it) }}")
            }
            if (!isRawTs && len > GOPRO_HEADER_SIZE && packetBuffer[GOPRO_HEADER_SIZE] == TS_SYNC) {
                val declared = ((packetBuffer[10].toInt() and 0xFF) shl 8) or
                    (packetBuffer[11].toInt() and 0xFF)
                val available = len - GOPRO_HEADER_SIZE
                val payloadLen = if (declared in 1..available) declared else available
                payloadPos = GOPRO_HEADER_SIZE
                payloadEnd = GOPRO_HEADER_SIZE + payloadLen
            } else {
                // Unexpected framing or raw TS: pass the datagram through untouched.
                payloadPos = 0
                payloadEnd = len
            }
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        closed = true
        socket?.close()
        socket = null
        uri = null
        try { dumpStream?.flush(); dumpStream?.close() } catch (_: Exception) {}
        dumpStream = null
        transferEnded()
    }
}
