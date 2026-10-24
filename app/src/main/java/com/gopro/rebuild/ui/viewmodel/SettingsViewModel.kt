package com.gopro.rebuild.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gopro.rebuild.camera.*
import com.gopro.rebuild.wifi.GoProWifiApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CameraSettingsState(
    val resolution: VideoResolution = VideoResolution.RES_4K,
    val frameRate: FrameRate = FrameRate.FPS_30,
    val fov: FieldOfView = FieldOfView.WIDE,
    val hyperSmooth: HyperSmooth = HyperSmooth.ON,
    val hindsight: Hindsight = Hindsight.OFF,
    val color: ProtuneColor = ProtuneColor.VIBRANT,
    val whiteBalance: WhiteBalance = WhiteBalance.AUTO,
    val isoMax: ISO = ISO.ISO_1600,
    val isoMin: ISO = ISO.ISO_100,
    val sharpness: Sharpness = Sharpness.HIGH,
    val evComp: EVComp = EVComp.ZERO,
    val timer: Timer = Timer.OFF
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val wifiApi: GoProWifiApi
) : ViewModel() {

    private val camera get() = cameraRepository.getCurrentCamera()

    private val _currentSettings = MutableStateFlow(CameraSettingsState())
    val currentSettings: StateFlow<CameraSettingsState> = _currentSettings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        // Observe settings updates from active camera (BLE or WiFi)
        camera?.settings?.onEach { settingsMap ->
            if (settingsMap.isNotEmpty()) {
                _currentSettings.value = parseSettingsFromLong(settingsMap)
            }
        }?.launchIn(viewModelScope)
    }

    fun loadSettings() {
        viewModelScope.launch {
            _isLoading.value = true

            // If we have BLE settings, use them immediately
            val initialSettings = camera?.settings?.value
            if (!initialSettings.isNullOrEmpty()) {
                _currentSettings.value = parseSettingsFromLong(initialSettings)
            }

            // If WiFi is available, query fresh state
            wifiApi.getState().onSuccess { state ->
                val combined = state.settings.mapKeys { it.key.toIntOrNull() ?: -1 }
                    .filterKeys { it != -1 }
                    .mapValues { it.value.toLong() }
                if (combined.isNotEmpty()) {
                    _currentSettings.value = parseSettingsFromLong(combined)
                }
            }

            // Also trigger BLE query
            camera?.refreshStatus()

            _isLoading.value = false
        }
    }

    private fun parseSettingsFromLong(settings: Map<Int, Long>): CameraSettingsState {
        // FOV can be ID 4 (gpControl) or ID 121 (Open GoPro)
        val fovId = (settings[4] ?: settings[SettingIds.FOV] ?: 0).toInt()
        // ISO can be ID 13 (gpControl) or ID 102 (Open GoPro)
        val isoId = (settings[13] ?: settings[SettingIds.ISO_MAX] ?: 4).toInt()

        return CameraSettingsState(
            resolution = VideoResolution.fromId((settings[SettingIds.RESOLUTION] ?: 1).toInt()),
            frameRate = FrameRate.fromId((settings[SettingIds.FPS] ?: 8).toInt()),
            fov = FieldOfView.fromId(fovId),
            hyperSmooth = HyperSmooth.fromId((settings[SettingIds.HYPERSMOOTH] ?: 1).toInt()),
            hindsight = Hindsight.fromId((settings[SettingIds.HINDSIGHT] ?: 0).toInt()),
            isoMax = ISO.entries.find { it.id == isoId } ?: ISO.ISO_1600
        )
    }

    fun setResolution(res: VideoResolution) {
        viewModelScope.launch {
            camera?.setSetting(SettingIds.RESOLUTION, res.id)?.onSuccess {
                _currentSettings.value = _currentSettings.value.copy(resolution = res)
            }
        }
    }

    fun setFrameRate(fps: FrameRate) {
        viewModelScope.launch {
            camera?.setSetting(SettingIds.FPS, fps.id)?.onSuccess {
                _currentSettings.value = _currentSettings.value.copy(frameRate = fps)
            }
        }
    }

    fun setFov(fov: FieldOfView) {
        viewModelScope.launch {
            // Try both setting 4 and setting 121
            camera?.setSetting(4, fov.id)
            camera?.setSetting(SettingIds.FOV, fov.id)?.onSuccess {
                _currentSettings.value = _currentSettings.value.copy(fov = fov)
            }
        }
    }

    fun setHyperSmooth(hs: HyperSmooth) {
        viewModelScope.launch {
            camera?.setSetting(SettingIds.HYPERSMOOTH, hs.id)?.onSuccess {
                _currentSettings.value = _currentSettings.value.copy(hyperSmooth = hs)
            }
        }
    }

    fun setHindsight(hindsight: Hindsight) {
        viewModelScope.launch {
            camera?.setSetting(SettingIds.HINDSIGHT, hindsight.id)?.onSuccess {
                _currentSettings.value = _currentSettings.value.copy(hindsight = hindsight)
            }
        }
    }

    fun setWhiteBalance(wb: WhiteBalance) {
        viewModelScope.launch {
            camera?.setSetting(SettingIds.WHITE_BALANCE, wb.id)?.onSuccess {
                _currentSettings.value = _currentSettings.value.copy(whiteBalance = wb)
            }
        }
    }

    fun setIsoMax(iso: ISO) {
        viewModelScope.launch {
            camera?.setSetting(13, iso.id)
            camera?.setSetting(SettingIds.ISO_MAX, iso.id)?.onSuccess {
                _currentSettings.value = _currentSettings.value.copy(isoMax = iso)
            }
        }
    }

    fun setSharpness(sharpness: Sharpness) {
        viewModelScope.launch {
            camera?.setSetting(SettingIds.SHARPNESS, sharpness.id)?.onSuccess {
                _currentSettings.value = _currentSettings.value.copy(sharpness = sharpness)
            }
        }
    }

    fun setEvComp(ev: EVComp) {
        viewModelScope.launch {
            camera?.setSetting(SettingIds.EV_COMP, ev.id)?.onSuccess {
                _currentSettings.value = _currentSettings.value.copy(evComp = ev)
            }
        }
    }

    fun setColor(color: ProtuneColor) {
        viewModelScope.launch {
            camera?.setSetting(SettingIds.COLOR, color.id)?.onSuccess {
                _currentSettings.value = _currentSettings.value.copy(color = color)
            }
        }
    }

    fun setIsoMin(iso: ISO) {
        viewModelScope.launch {
            camera?.setSetting(SettingIds.ISO_MIN, iso.id)?.onSuccess {
                _currentSettings.value = _currentSettings.value.copy(isoMin = iso)
            }
        }
    }

    fun setTimer(timer: Timer) {
        viewModelScope.launch {
            camera?.setSetting(SettingIds.TIMER, timer.id)?.onSuccess {
                _currentSettings.value = _currentSettings.value.copy(timer = timer)
            }
        }
    }

    fun syncDateTime() {
        viewModelScope.launch {
            wifiApi.setDateTime()
        }
    }

    fun locateCamera(enable: Boolean = true) {
        viewModelScope.launch {
            wifiApi.locate(enable)
        }
    }
}
