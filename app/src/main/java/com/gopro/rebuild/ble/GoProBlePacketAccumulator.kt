package com.gopro.rebuild.ble

import java.io.ByteArrayOutputStream

/**
 * GoPro BLE frame defragmenter.
 * Handles single-packet and multi-packet fragmented frames
 * according to GoPro BLE specification.
 */
class GoProBlePacketAccumulator {

    private val buffer = ByteArrayOutputStream()
    private var expectedLength = -1

    /**
     * Feed a raw BLE packet.
     * Returns a fully reassembled frame ByteArray when complete, or null if more fragments are needed.
     */
    fun feed(packet: ByteArray): ByteArray? {
        if (packet.isEmpty()) return null

        val firstByte = packet[0].toInt() and 0xFF

        // Check if this is a continuation packet (bit 7 set: 0x80..0xFF)
        if ((firstByte and 0x80) != 0) {
            // Continuation packet: drop 1-byte continuation header, append remainder
            if (expectedLength > 0 && packet.size > 1) {
                buffer.write(packet, 1, packet.size - 1)
                if (buffer.size() >= expectedLength) {
                    val full = buffer.toByteArray()
                    reset()
                    return full
                }
            }
            return null
        }

        // Otherwise, it's a start packet
        reset()

        val lengthType = firstByte and 0xE0
        val headerLen: Int
        val totalPayloadLen: Int

        when (lengthType) {
            0x00 -> {
                // 5-bit length in byte 0
                headerLen = 1
                totalPayloadLen = firstByte and 0x1F
            }
            0x20 -> {
                // 13-bit length in byte 0 and 1
                if (packet.size < 2) return null
                headerLen = 2
                totalPayloadLen = ((firstByte and 0x1F) shl 8) or (packet[1].toInt() and 0xFF)
            }
            0x40 -> {
                // 16-bit length in byte 1 and 2
                if (packet.size < 3) return null
                headerLen = 3
                totalPayloadLen = ((packet[1].toInt() and 0xFF) shl 8) or (packet[2].toInt() and 0xFF)
            }
            else -> {
                // Fallback: direct payload
                headerLen = 0
                totalPayloadLen = packet.size
            }
        }

        expectedLength = totalPayloadLen
        val initialDataLen = packet.size - headerLen
        if (initialDataLen > 0) {
            buffer.write(packet, headerLen, initialDataLen)
        }

        if (buffer.size() >= expectedLength) {
            val full = buffer.toByteArray()
            reset()
            return full
        }

        return null
    }

    fun reset() {
        buffer.reset()
        expectedLength = -1
    }
}
