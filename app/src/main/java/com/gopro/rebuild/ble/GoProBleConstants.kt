package com.gopro.rebuild.ble

import com.juul.kable.characteristicOf
import java.util.UUID

/**
 * GoPro BLE Service and Characteristic UUIDs
 * Extracted from official GoPro Quik app
 */
object GoProBleConstants {

    // === Bluetooth SIG Company Identifier ===
    /** GoPro, Inc. Company Identifier (0x02F2 = 754 decimal) */
    const val COMPANY_ID_GOPRO = 0x02F2

    // === BLE Status Flags (from Manufacturer Data byte 1) ===
    object StatusFlags {
        const val HOST_POWER_ON = 0x01
        const val WIFI_ON = 0x02
        const val PAIRING = 0x04
        const val CENTRAL_ROLE = 0x08
    }

    // === BLE Services ===

    /** Control Point Service - Main command interface */
    val SERVICE_CONTROL_POINT = UUID.fromString("0000fea6-0000-1000-8000-00805f9b34fb")

    /** WiFi Access Point Service - Get WiFi credentials */
    val SERVICE_WIFI_AP = UUID.fromString("b5f90001-aa8d-11e3-9046-0002a5d5c51b")

    /** Camera Management Service */
    val SERVICE_CAMERA_MGMT = UUID.fromString("b5f90090-aa8d-11e3-9046-0002a5d5c51b")

    /** Standard Device Information Service */
    val SERVICE_DEVICE_INFO = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")

    // === Control Point Characteristics (Service FEA6) ===

    /** Write commands here */
    val CHAR_COMMAND = UUID.fromString("b5f90072-aa8d-11e3-9046-0002a5d5c51b")

    /** Subscribe for command responses */
    val CHAR_COMMAND_RESPONSE = UUID.fromString("b5f90073-aa8d-11e3-9046-0002a5d5c51b")

    /** Write settings here */
    val CHAR_SETTING = UUID.fromString("b5f90074-aa8d-11e3-9046-0002a5d5c51b")

    /** Subscribe for setting responses */
    val CHAR_SETTING_RESPONSE = UUID.fromString("b5f90075-aa8d-11e3-9046-0002a5d5c51b")

    /** Write queries here */
    val CHAR_QUERY = UUID.fromString("b5f90076-aa8d-11e3-9046-0002a5d5c51b")

    /** Subscribe for query responses */
    val CHAR_QUERY_RESPONSE = UUID.fromString("b5f90077-aa8d-11e3-9046-0002a5d5c51b")

    // === WiFi AP Characteristics (Service B5F90001) ===

    /** Read camera WiFi SSID */
    val CHAR_WIFI_SSID = UUID.fromString("b5f90002-aa8d-11e3-9046-0002a5d5c51b")

    /** Read camera WiFi password */
    val CHAR_WIFI_PASSWORD = UUID.fromString("b5f90003-aa8d-11e3-9046-0002a5d5c51b")

    /** Write to enable/disable WiFi AP */
    val CHAR_WIFI_SWITCH = UUID.fromString("b5f90004-aa8d-11e3-9046-0002a5d5c51b")

    /** Read WiFi AP state */
    val CHAR_WIFI_STATE = UUID.fromString("b5f90005-aa8d-11e3-9046-0002a5d5c51b")

    // === Camera Management Characteristics (Service B5F90090) ===

    val CHAR_MGMT_COMMAND = UUID.fromString("b5f90091-aa8d-11e3-9046-0002a5d5c51b")
    val CHAR_MGMT_RESPONSE = UUID.fromString("b5f90092-aa8d-11e3-9046-0002a5d5c51b")

    // === Device Info Characteristics ===

    val CHAR_MANUFACTURER = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
    val CHAR_MODEL_NUMBER = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
    val CHAR_SERIAL_NUMBER = UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb")
    val CHAR_FIRMWARE_REV = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
    val CHAR_HARDWARE_REV = UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb")

    // === Kable Characteristic Helpers ===

    val commandChar = characteristicOf(
        service = SERVICE_CONTROL_POINT.toString(),
        characteristic = CHAR_COMMAND.toString()
    )

    val commandResponseChar = characteristicOf(
        service = SERVICE_CONTROL_POINT.toString(),
        characteristic = CHAR_COMMAND_RESPONSE.toString()
    )

    val settingChar = characteristicOf(
        service = SERVICE_CONTROL_POINT.toString(),
        characteristic = CHAR_SETTING.toString()
    )

    val settingResponseChar = characteristicOf(
        service = SERVICE_CONTROL_POINT.toString(),
        characteristic = CHAR_SETTING_RESPONSE.toString()
    )

    val queryChar = characteristicOf(
        service = SERVICE_CONTROL_POINT.toString(),
        characteristic = CHAR_QUERY.toString()
    )

    val queryResponseChar = characteristicOf(
        service = SERVICE_CONTROL_POINT.toString(),
        characteristic = CHAR_QUERY_RESPONSE.toString()
    )

    val wifiSsidChar = characteristicOf(
        service = SERVICE_WIFI_AP.toString(),
        characteristic = CHAR_WIFI_SSID.toString()
    )

    val wifiPasswordChar = characteristicOf(
        service = SERVICE_WIFI_AP.toString(),
        characteristic = CHAR_WIFI_PASSWORD.toString()
    )

    val wifiSwitchChar = characteristicOf(
        service = SERVICE_WIFI_AP.toString(),
        characteristic = CHAR_WIFI_SWITCH.toString()
    )
}
