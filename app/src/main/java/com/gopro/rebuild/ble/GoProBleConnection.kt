package com.gopro.rebuild.ble

import android.annotation.SuppressLint
import android.util.Log
import com.juul.kable.Peripheral
import com.juul.kable.State
import com.juul.kable.WriteType
import com.juul.kable.peripheral
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "GoProBle"

data class WifiCredentials(
    val ssid: String,
    val password: String
)

data class CameraInfo(
    val model: String,
    val firmware: String,
    val serialNumber: String
)

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

class GoProBleConnection(
    private val device: GoProDevice,
    private val scope: CoroutineScope
) {
    private var peripheral: Peripheral? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _commandResponse = MutableStateFlow<ByteArray?>(null)
    val commandResponse: StateFlow<ByteArray?> = _commandResponse.asStateFlow()

    // Status map: ID -> Value
    private val _statusMap = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val statusMap: StateFlow<Map<Int, Long>> = _statusMap.asStateFlow()

    // Settings map: ID -> Value
    private val _settingsMap = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val settingsMap: StateFlow<Map<Int, Long>> = _settingsMap.asStateFlow()

    private val queryAccumulator = GoProBlePacketAccumulator()
    private val settingAccumulator = GoProBlePacketAccumulator()

    @SuppressLint("MissingPermission")
    suspend fun connect(): Result<Unit> {
        return try {
            _connectionState.value = ConnectionState.Connecting
            Log.d(TAG, "Connecting to ${device.name} (${device.address})...")

            peripheral = if (device.advertisement != null) {
                scope.peripheral(device.advertisement)
            } else if (device.bluetoothDevice != null) {
                scope.peripheral(device.bluetoothDevice)
            } else {
                scope.peripheral(device.address)
            }

            peripheral?.connect()

            // Wait for connected state
            peripheral?.state?.first { it is State.Connected }

            // Setup notifications
            setupNotifications()

            // Short delay to ensure GATT readiness
            delay(500)

            // Register and query initial status and settings
            queryAllStatus()
            queryAllSettings()

            _connectionState.value = ConnectionState.Connected
            Log.d(TAG, "Connected to ${device.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    private suspend fun setupNotifications() {
        // Subscribe to command responses
        try {
            peripheral?.observe(GoProBleConstants.commandResponseChar)
                ?.onEach { data ->
                    Log.d(TAG, "Command response: ${data.toHexString()}")
                    _commandResponse.value = data
                }
                ?.launchIn(scope)
        } catch (e: Exception) {
            Log.w(TAG, "Could not observe command response: ${e.message}")
        }

        // Subscribe to query responses (Status & Settings updates)
        try {
            peripheral?.observe(GoProBleConstants.queryResponseChar)
                ?.onEach { data ->
                    val fullFrame = queryAccumulator.feed(data)
                    if (fullFrame != null) {
                        parseQueryResponse(fullFrame)
                    }
                }
                ?.launchIn(scope)
        } catch (e: Exception) {
            Log.w(TAG, "Could not observe query response: ${e.message}")
        }

        // Subscribe to setting responses
        try {
            peripheral?.observe(GoProBleConstants.settingResponseChar)
                ?.onEach { data ->
                    val fullFrame = settingAccumulator.feed(data)
                    if (fullFrame != null) {
                        parseSettingResponse(fullFrame)
                    }
                }
                ?.launchIn(scope)
        } catch (e: Exception) {
            Log.w(TAG, "Could not observe setting response: ${e.message}")
        }
    }

    /**
     * Parse defragmented query response frame
     */
    private fun parseQueryResponse(frame: ByteArray) {
        if (frame.isEmpty()) return
        val queryType = frame[0].toInt() and 0xFF
        Log.d(TAG, "Received complete query frame: queryType=0x${"%02X".format(queryType)}, len=${frame.size}")

        when (queryType) {
            // Status updates: 0x13 (19), 0x53 (83), 0x93 (147)
            0x13, 0x53, 0x93 -> {
                val newStatus = parseTlvData(frame, startIndex = 1)
                if (newStatus.isNotEmpty()) {
                    _statusMap.value = _statusMap.value + newStatus
                    Log.i(TAG, "Updated Status Map: $newStatus")
                }
            }
            // Settings updates: 0x12 (18), 0x52 (82), 0x92 (146)
            0x12, 0x52, 0x92 -> {
                val newSettings = parseTlvData(frame, startIndex = 1)
                if (newSettings.isNotEmpty()) {
                    _settingsMap.value = _settingsMap.value + newSettings
                    Log.i(TAG, "Updated Settings Map: $newSettings")
                }
            }
            else -> {
                Log.d(TAG, "Other query response: 0x${"%02X".format(queryType)}")
            }
        }
    }

    private fun parseSettingResponse(frame: ByteArray) {
        if (frame.isEmpty()) return
        Log.d(TAG, "Setting response frame: ${frame.toHexString()}")
        val settings = parseTlvData(frame, startIndex = 1)
        if (settings.isNotEmpty()) {
            _settingsMap.value = _settingsMap.value + settings
        }
    }

    /**
     * Parses Type-Length-Value entries from GoPro BLE packet
     */
    private fun parseTlvData(frame: ByteArray, startIndex: Int): Map<Int, Long> {
        val result = mutableMapOf<Int, Long>()
        var index = startIndex

        // Some responses have a status code (0x00 = success) right after queryType
        if (index < frame.size && frame[index].toInt() == 0) {
            index++
        }

        while (index + 2 <= frame.size) {
            val id = frame[index].toInt() and 0xFF
            index++
            val len = frame[index].toInt() and 0xFF
            index++

            if (index + len > frame.size) {
                break
            }

            var value = 0L
            for (i in 0 until len) {
                value = (value shl 8) or ((frame[index + i].toLong()) and 0xFF)
            }
            index += len

            result[id] = value
        }

        return result
    }

    suspend fun disconnect() {
        try {
            peripheral?.disconnect()
            _connectionState.value = ConnectionState.Disconnected
        } catch (e: Exception) {
            Log.e(TAG, "Disconnect error", e)
        }
    }

    // === Commands ===

    suspend fun sendCommand(command: ByteArray): Result<ByteArray?> {
        return try {
            Log.d(TAG, "Sending command: ${command.toHexString()}")
            peripheral?.write(GoProBleConstants.commandChar, command, WriteType.WithResponse)

            // Wait for response with timeout
            val response = withTimeoutOrNull(3000) {
                commandResponse.first { it != null }
            }
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "Command failed", e)
            Result.failure(e)
        }
    }

    suspend fun shutterOn(): Result<ByteArray?> = sendCommand(GoProCommands.SHUTTER_ON)
    suspend fun shutterOff(): Result<ByteArray?> = sendCommand(GoProCommands.SHUTTER_OFF)
    suspend fun powerOff(): Result<ByteArray?> = sendCommand(GoProCommands.POWER_OFF)
    suspend fun keepAlive(): Result<ByteArray?> = sendCommand(GoProCommands.KEEP_ALIVE)
    suspend fun enableWifi(): Result<ByteArray?> = sendCommand(GoProCommands.WIFI_AP_ON)
    suspend fun disableWifi(): Result<ByteArray?> = sendCommand(GoProCommands.WIFI_AP_OFF)

    suspend fun setModeVideo(): Result<ByteArray?> = sendCommand(GoProCommands.MODE_VIDEO)
    suspend fun setModePhoto(): Result<ByteArray?> = sendCommand(GoProCommands.MODE_PHOTO)
    suspend fun setModeTimelapse(): Result<ByteArray?> = sendCommand(GoProCommands.MODE_TIMELAPSE)

    // === Settings over BLE ===

    suspend fun setSettingBle(settingId: Int, value: Int): Result<Unit> {
        return try {
            val cmd = byteArrayOf(
                0x03,
                settingId.toByte(),
                0x01,
                value.toByte()
            )
            Log.d(TAG, "Sending setting over BLE: ID=$settingId, val=$value")
            peripheral?.write(GoProBleConstants.settingChar, cmd, WriteType.WithResponse)
            // Optimistically update settings map
            _settingsMap.value = _settingsMap.value + (settingId to value.toLong())
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send setting over BLE", e)
            Result.failure(e)
        }
    }

    // === WiFi Credentials ===

    suspend fun getWifiCredentials(): Result<WifiCredentials> {
        return try {
            val ssidBytes = peripheral?.read(GoProBleConstants.wifiSsidChar)
            val passwordBytes = peripheral?.read(GoProBleConstants.wifiPasswordChar)

            if (ssidBytes != null && passwordBytes != null) {
                val credentials = WifiCredentials(
                    ssid = String(ssidBytes, Charsets.UTF_8).trim(),
                    password = String(passwordBytes, Charsets.UTF_8).trim()
                )
                Log.d(TAG, "WiFi credentials: SSID=${credentials.ssid}")
                Result.success(credentials)
            } else {
                Result.failure(Exception("Failed to read WiFi credentials"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get WiFi credentials", e)
            Result.failure(e)
        }
    }

    suspend fun enableWifiAp(): Result<Unit> {
        return try {
            peripheral?.write(GoProBleConstants.wifiSwitchChar, byteArrayOf(0x01), WriteType.WithResponse)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // === Camera Info ===

    suspend fun getCameraInfo(): Result<CameraInfo> {
        // Infer default model name from device name (e.g. "GoPro 1470" or "HERO6 Black")
        var model = device.name
        var firmware = "Unknown"
        var serial = "Unknown"

        try {
            val modelChar = com.juul.kable.characteristicOf(
                service = GoProBleConstants.SERVICE_DEVICE_INFO.toString(),
                characteristic = GoProBleConstants.CHAR_MODEL_NUMBER.toString()
            )
            val readModel = peripheral?.read(modelChar)?.let { String(it).trim() }
            if (!readModel.isNullOrBlank()) {
                model = com.gopro.rebuild.camera.GoProModel.getDisplayName(readModel, device.name)
            }

            val firmwareChar = com.juul.kable.characteristicOf(
                service = GoProBleConstants.SERVICE_DEVICE_INFO.toString(),
                characteristic = GoProBleConstants.CHAR_FIRMWARE_REV.toString()
            )
            val readFw = peripheral?.read(firmwareChar)?.let { String(it).trim() }
            if (!readFw.isNullOrBlank()) firmware = readFw

            val serialChar = com.juul.kable.characteristicOf(
                service = GoProBleConstants.SERVICE_DEVICE_INFO.toString(),
                characteristic = GoProBleConstants.CHAR_SERIAL_NUMBER.toString()
            )
            val readSerial = peripheral?.read(serialChar)?.let { String(it).trim() }
            if (!readSerial.isNullOrBlank()) serial = readSerial
        } catch (e: Exception) {
            Log.w(TAG, "Standard Device Info characteristic not readable (expected on older GoPros): ${e.message}")
        }

        return Result.success(CameraInfo(model, firmware, serial))
    }

    // === Query Status & Settings ===

    suspend fun queryAllStatus(): Result<Unit> {
        return try {
            // [0x01, 0x13] = Query all status values
            peripheral?.write(GoProBleConstants.queryChar, byteArrayOf(0x01, 0x13), WriteType.WithResponse)
            // [0x01, 0x53] = Register for status updates
            peripheral?.write(GoProBleConstants.queryChar, byteArrayOf(0x01, 0x53), WriteType.WithResponse)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Query status error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun queryAllSettings(): Result<Unit> {
        return try {
            // [0x01, 0x12] = Query all settings values
            peripheral?.write(GoProBleConstants.queryChar, byteArrayOf(0x01, 0x12), WriteType.WithResponse)
            // [0x01, 0x52] = Register for settings updates
            peripheral?.write(GoProBleConstants.queryChar, byteArrayOf(0x01, 0x52), WriteType.WithResponse)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Query settings error: ${e.message}")
            Result.failure(e)
        }
    }
}

// Extension to convert ByteArray to hex string for logging
fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }
