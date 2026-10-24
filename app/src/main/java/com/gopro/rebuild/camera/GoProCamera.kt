package com.gopro.rebuild.camera

import android.util.Log
import com.gopro.rebuild.ble.CameraInfo
import com.gopro.rebuild.ble.ConnectionState
import com.gopro.rebuild.ble.GoProBleConnection
import com.gopro.rebuild.ble.GoProDevice
import com.gopro.rebuild.ble.WifiCredentials
import com.gopro.rebuild.wifi.GoProWifiApi
import com.gopro.rebuild.wifi.MediaList
import com.gopro.rebuild.wifi.WifiConnectionManager
import com.gopro.rebuild.wifi.WifiConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

private const val TAG = "GoProCamera"

data class CameraStatus(
    val isConnected: Boolean = false,
    val isRecording: Boolean = false,
    val batteryLevel: Int = 0,
    val sdCardSpaceRemaining: Long = 0,
    val currentMode: PresetGroup = PresetGroup.VIDEO,
    val cameraInfo: CameraInfo? = null
)

class GoProCamera(
    val device: GoProDevice,
    private val wifiManager: WifiConnectionManager,
    private val wifiApi: GoProWifiApi
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val bleConnection = GoProBleConnection(device, scope)

    private val _status = MutableStateFlow(CameraStatus())
    val status: StateFlow<CameraStatus> = _status.asStateFlow()

    private val _settings = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val settings: StateFlow<Map<Int, Long>> = _settings.asStateFlow()

    val bleConnectionState: StateFlow<ConnectionState> = bleConnection.connectionState
    val wifiConnectionState: StateFlow<WifiConnectionState> = wifiManager.connectionState

    private var wifiCredentials: WifiCredentials? = null

    init {
        observeBleUpdates()
    }

    private fun observeBleUpdates() {
        // Observe status updates from BLE
        bleConnection.statusMap.onEach { statusMap ->
            if (statusMap.isNotEmpty()) {
                val batteryPct: Int = when {
                    statusMap.containsKey(70) -> statusMap[70]?.toInt() ?: 0
                    statusMap.containsKey(2) -> {
                        when (statusMap[2]?.toInt() ?: 0) {
                            3 -> 100
                            2 -> 65
                            1 -> 35
                            else -> 10
                        }
                    }
                    else -> _status.value.batteryLevel
                }

                val recording = if (statusMap.containsKey(10)) {
                    statusMap[10] == 1L
                } else {
                    _status.value.isRecording
                }

                val mode = if (statusMap.containsKey(43)) {
                    when (statusMap[43]?.toInt()) {
                        0 -> PresetGroup.VIDEO
                        1 -> PresetGroup.PHOTO
                        2 -> PresetGroup.TIMELAPSE
                        else -> _status.value.currentMode
                    }
                } else {
                    _status.value.currentMode
                }

                val space = statusMap[54] ?: _status.value.sdCardSpaceRemaining

                _status.value = _status.value.copy(
                    batteryLevel = batteryPct,
                    isRecording = recording,
                    currentMode = mode,
                    sdCardSpaceRemaining = space
                )
            }
        }.launchIn(scope)

        // Observe settings updates from BLE
        bleConnection.settingsMap.onEach { settingsMap ->
            if (settingsMap.isNotEmpty()) {
                _settings.value = _settings.value + settingsMap
            }
        }.launchIn(scope)
    }

    suspend fun connect(): Result<Unit> {
        if (device.address == "10.5.5.9") {
            // Direct WiFi mode (no BLE)
            wifiManager.checkAndBindActiveWifi()
            _status.value = _status.value.copy(
                isConnected = true,
                cameraInfo = CameraInfo(model = device.name, firmware = "Unknown", serialNumber = "Unknown")
            )
            refreshStatus()
            return Result.success(Unit)
        }

        val bleResult = bleConnection.connect()
        if (bleResult.isFailure) return bleResult

        val cameraInfo = bleConnection.getCameraInfo().getOrNull()
        _status.value = _status.value.copy(
            isConnected = true,
            cameraInfo = cameraInfo
        )

        val credentials = bleConnection.getWifiCredentials()
        if (credentials.isSuccess) {
            wifiCredentials = credentials.getOrNull()
        }

        return Result.success(Unit)
    }

    suspend fun connectWifi(): Result<Unit> {
        var creds = wifiCredentials
        if (creds == null) {
            creds = bleConnection.getWifiCredentials().getOrNull()
            wifiCredentials = creds
        }
        if (creds == null) {
            return Result.failure(Exception("No WiFi credentials. Turn on camera WiFi first."))
        }

        Log.d(TAG, "Enabling WiFi AP via BLE for ${creds.ssid}...")
        bleConnection.enableWifiAp()
        kotlinx.coroutines.delay(1000)

        val result = wifiManager.connectToGoProWifi(creds.ssid, creds.password)
        if (result.isSuccess) {
            refreshStatus()
        }
        return result
    }

    suspend fun disconnect() {
        wifiManager.disconnect()
        bleConnection.disconnect()
        _status.value = CameraStatus()
    }

    suspend fun startRecording(): Result<Unit> {
        if (!wifiManager.isConnectedToGoPro()) {
            val wifiResult = connectWifi()
            if (wifiResult.isFailure) return wifiResult
        }
        return wifiApi.shutterStart().also {
            if (it.isSuccess) {
                _status.value = _status.value.copy(isRecording = true)
            }
        }
    }

    suspend fun stopRecording(): Result<Unit> {
        if (!wifiManager.isConnectedToGoPro()) {
            val wifiResult = connectWifi()
            if (wifiResult.isFailure) return wifiResult
        }
        return wifiApi.shutterStop().also {
            if (it.isSuccess) {
                _status.value = _status.value.copy(isRecording = false)
            }
        }
    }

    suspend fun setMode(mode: PresetGroup): Result<Unit> {
        return if (wifiManager.isConnectedToGoPro()) {
            wifiApi.setPresetGroup(mode).also {
                if (it.isSuccess) {
                    _status.value = _status.value.copy(currentMode = mode)
                }
            }
        } else {
            when (mode) {
                PresetGroup.VIDEO -> bleConnection.setModeVideo()
                PresetGroup.PHOTO -> bleConnection.setModePhoto()
                PresetGroup.TIMELAPSE -> bleConnection.setModeTimelapse()
            }.map {
                _status.value = _status.value.copy(currentMode = mode)
            }
        }
    }

    suspend fun setSetting(settingId: Int, value: Int): Result<Unit> {
        return if (wifiManager.isConnectedToGoPro()) {
            wifiApi.setSetting(settingId, value).also {
                if (it.isSuccess) {
                    _settings.value = _settings.value + (settingId to value.toLong())
                }
            }
        } else {
            bleConnection.setSettingBle(settingId, value).also {
                if (it.isSuccess) {
                    _settings.value = _settings.value + (settingId to value.toLong())
                }
            }
        }
    }

    suspend fun powerOff(): Result<Unit> {
        return if (wifiManager.isConnectedToGoPro()) {
            wifiApi.sleep()
        } else {
            bleConnection.powerOff().map { }
        }
    }

    suspend fun keepAlive() {
        if (!wifiManager.isConnectedToGoPro()) {
            bleConnection.keepAlive()
        }
    }

    suspend fun getMediaList(): Result<MediaList> {
        if (!wifiManager.isConnectedToGoPro()) {
            return Result.failure(Exception("WiFi required"))
        }
        return wifiApi.getMediaList()
    }

    fun getMediaUrl(directory: String, filename: String): String =
        wifiApi.getMediaUrl(directory, filename)

    fun getThumbnailUrl(directory: String, filename: String): String =
        wifiApi.getThumbnailUrl(directory, filename)

    suspend fun deleteMedia(directory: String, filename: String): Result<Unit> =
        wifiApi.deleteMedia(directory, filename)

    suspend fun startPreview(): Result<Unit> {
        if (!wifiManager.isConnectedToGoPro()) {
            return Result.failure(Exception("WiFi required"))
        }
        return wifiApi.startPreview()
    }

    suspend fun stopPreview(): Result<Unit> = wifiApi.stopPreview()

    fun getPreviewUrl(): String = wifiApi.getPreviewUrl()

    suspend fun refreshStatus() {
        if (wifiManager.isConnectedToGoPro()) {
            wifiApi.getState().onSuccess { state ->
                val status = state.status
                val batteryPct = status["70"] ?: status["2"]?.let { bars ->
                    when (bars) {
                        3 -> 100
                        2 -> 65
                        1 -> 35
                        else -> 10
                    }
                } ?: _status.value.batteryLevel

                _status.value = _status.value.copy(
                    batteryLevel = batteryPct,
                    isRecording = status["10"] == 1,
                    sdCardSpaceRemaining = (status["54"] ?: 0).toLong() * 1024
                )

                // Update settings map from WiFi state
                val convertedSettings = state.settings.mapKeys { (k, _) -> k.toIntOrNull() ?: -1 }
                    .filterKeys { it != -1 }
                    .mapValues { (_, v) -> v.toLong() }
                _settings.value = _settings.value + convertedSettings
            }

            // Also refresh hardware info if not yet set
            if (_status.value.cameraInfo?.firmware == "Unknown") {
                wifiApi.getHardwareInfo().onSuccess { hwInfo ->
                    val updatedInfo = CameraInfo(
                        model = GoProModel.getDisplayName(hwInfo["model_name"] ?: _status.value.cameraInfo?.model),
                        firmware = hwInfo["firmware_version"] ?: "Unknown",
                        serialNumber = hwInfo["serial_number"] ?: "Unknown"
                    )
                    _status.value = _status.value.copy(cameraInfo = updatedInfo)
                }
            }
        } else {
            bleConnection.queryAllStatus()
            bleConnection.queryAllSettings()
        }
    }
}
