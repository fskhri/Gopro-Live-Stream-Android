package com.gopro.rebuild.ble

/**
 * GoPro BLE Command Bytes
 * Based on reverse engineering of official app
 */
object GoProCommands {

    // === Shutter Commands ===

    /** Start recording / take photo */
    val SHUTTER_ON = byteArrayOf(0x01, 0x01, 0x01)

    /** Stop recording */
    val SHUTTER_OFF = byteArrayOf(0x01, 0x01, 0x00)

    // === Power Commands ===

    /** Power off / sleep */
    val POWER_OFF = byteArrayOf(0x01, 0x05)

    /** Keep alive ping */
    val KEEP_ALIVE = byteArrayOf(0x01, 0x5B)

    // === WiFi Commands ===

    /** Enable WiFi AP */
    val WIFI_AP_ON = byteArrayOf(0x03, 0x17, 0x01, 0x01)

    /** Disable WiFi AP */
    val WIFI_AP_OFF = byteArrayOf(0x03, 0x17, 0x01, 0x00)

    // === Mode Commands ===

    /** Set to Video mode */
    val MODE_VIDEO = byteArrayOf(0x04, 0x3E, 0x02, 0x03, 0xE8.toByte())

    /** Set to Photo mode */
    val MODE_PHOTO = byteArrayOf(0x04, 0x3E, 0x02, 0x03, 0xE9.toByte())

    /** Set to Timelapse mode */
    val MODE_TIMELAPSE = byteArrayOf(0x04, 0x3E, 0x02, 0x03, 0xEA.toByte())

    // === Query Commands ===

    /** Get all status values */
    val QUERY_ALL_STATUS = byteArrayOf(0x01, 0x13)

    /** Get all settings */
    val QUERY_ALL_SETTINGS = byteArrayOf(0x01, 0x12)

    /** Register for status updates */
    val REGISTER_STATUS_UPDATES = byteArrayOf(0x01, 0x53)

    /** Unregister from status updates */
    val UNREGISTER_STATUS_UPDATES = byteArrayOf(0x01, 0x73)

    // === Setting IDs (for SET_SETTING command) ===
    object SettingId {
        const val RESOLUTION = 2
        const val FPS = 3
        const val FOV = 121
        const val HYPERSMOOTH = 135
        const val HINDSIGHT = 167
    }

    // === Status IDs ===
    object StatusId {
        const val BATTERY_LEVEL = 70
        const val ENCODING_ACTIVE = 8
        const val SYSTEM_BUSY = 76
        const val SD_CARD_STATUS = 33
        const val REMAINING_SPACE = 54
        const val REMAINING_PHOTOS = 34
        const val REMAINING_VIDEO_TIME = 35
        const val CURRENT_MODE = 89
        const val CURRENT_PRESET = 97
    }

    /**
     * Build a set setting command
     * Format: [length, 0x03, setting_id, value_length, value...]
     */
    fun setSetting(settingId: Int, value: Int): ByteArray {
        return byteArrayOf(
            0x03,
            settingId.toByte(),
            0x01,
            value.toByte()
        )
    }

    /**
     * Build a query specific status command
     */
    fun queryStatus(vararg statusIds: Int): ByteArray {
        val cmd = mutableListOf<Byte>()
        cmd.add((statusIds.size + 1).toByte())
        cmd.add(0x13)
        statusIds.forEach { cmd.add(it.toByte()) }
        return cmd.toByteArray()
    }
}
