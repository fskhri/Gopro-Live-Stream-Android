package com.gopro.rebuild.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gopro.rebuild.camera.CameraRepository
import com.gopro.rebuild.camera.StatusIds
import com.gopro.rebuild.wifi.GoProWifiApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FullCameraInfo(
    val model: String = "",
    val serialNumber: String = "",
    val firmware: String = "",
    val apSsid: String = "",
    val apMac: String = "",
    val sdCardPresent: Boolean = false,
    val remainingSpace: Long = 0,
    val totalPhotos: Int = 0,
    val totalVideos: Int = 0,
    val batteryLevel: Int = 0,
    val externalBattery: Boolean = false
)

@HiltViewModel
class CameraInfoViewModel @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val wifiApi: GoProWifiApi
) : ViewModel() {

    private val camera get() = cameraRepository.getCurrentCamera()

    private val _cameraInfo = MutableStateFlow<FullCameraInfo?>(null)
    val cameraInfo: StateFlow<FullCameraInfo?> = _cameraInfo.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadInfo() {
        viewModelScope.launch {
            _isLoading.value = true

            val statusVal = camera?.status?.value
            val cameraInfoBle = statusVal?.cameraInfo

            var info = FullCameraInfo(
                model = com.gopro.rebuild.camera.GoProModel.getDisplayName(cameraInfoBle?.model ?: camera?.device?.name),
                serialNumber = cameraInfoBle?.serialNumber ?: "Unknown",
                firmware = cameraInfoBle?.firmware ?: "Unknown",
                batteryLevel = statusVal?.batteryLevel ?: 0,
                remainingSpace = statusVal?.sdCardSpaceRemaining ?: 0,
                sdCardPresent = (statusVal?.sdCardSpaceRemaining ?: 0) > 0
            )

            // Try to fetch hardware info via WiFi if available
            wifiApi.getHardwareInfo().onSuccess { hwInfo ->
                info = info.copy(
                    model = com.gopro.rebuild.camera.GoProModel.getDisplayName(
                        hwInfo["model_name"] ?: hwInfo["model_number"] ?: info.model
                    ),
                    serialNumber = hwInfo["serial_number"]?.takeIf { it.isNotBlank() } ?: info.serialNumber,
                    firmware = hwInfo["firmware_version"]?.takeIf { it.isNotBlank() } ?: info.firmware,
                    apSsid = hwInfo["ap_ssid"] ?: info.apSsid,
                    apMac = hwInfo["ap_mac"] ?: info.apMac
                )
            }

            // Try to fetch detailed status via WiFi if available
            wifiApi.getState().onSuccess { state ->
                val status = state.status
                info = info.copy(
                    sdCardPresent = status["${StatusIds.SD_CARD_STATUS}"] == 0,
                    remainingSpace = (status["${StatusIds.REMAINING_SPACE}"] ?: 0).toLong() * 1024,
                    totalPhotos = status["${StatusIds.NUM_TOTAL_PHOTOS}"] ?: 0,
                    totalVideos = status["${StatusIds.NUM_TOTAL_VIDEOS}"] ?: 0,
                    batteryLevel = status["70"] ?: status["2"]?.let { bars ->
                        when (bars) {
                            3 -> 100
                            2 -> 65
                            1 -> 35
                            else -> 10
                        }
                    } ?: info.batteryLevel,
                    externalBattery = status["${StatusIds.EXTERNAL_BATTERY_PRESENT}"] == 1
                )
            }

            _cameraInfo.value = info
            _isLoading.value = false
        }
    }
}
