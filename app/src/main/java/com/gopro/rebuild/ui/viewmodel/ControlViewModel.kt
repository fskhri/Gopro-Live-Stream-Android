package com.gopro.rebuild.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gopro.rebuild.camera.CameraRepository
import com.gopro.rebuild.camera.CameraStatus
import com.gopro.rebuild.camera.PresetGroup
import com.gopro.rebuild.wifi.WifiConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ControlViewModel @Inject constructor(
    private val cameraRepository: CameraRepository
) : ViewModel() {

    private val camera get() = cameraRepository.getCurrentCamera()

    private val _cameraStatus = MutableStateFlow(CameraStatus())
    val cameraStatus: StateFlow<CameraStatus> = _cameraStatus.asStateFlow()

    private val _isWifiConnected = MutableStateFlow(false)
    val isWifiConnected: StateFlow<Boolean> = _isWifiConnected.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var keepAliveJob: Job? = null
    private var statusRefreshJob: Job? = null

    init {
        observeCameraStatus()
        startKeepAlive()
        startStatusRefresh()
    }

    private fun observeCameraStatus() {
        viewModelScope.launch {
            camera?.status?.collect { status ->
                _cameraStatus.value = status
            }
        }

        viewModelScope.launch {
            camera?.wifiConnectionState?.collect { state ->
                _isWifiConnected.value = state == WifiConnectionState.Connected
            }
        }
    }

    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                camera?.keepAlive()
            }
        }
    }

    private fun startStatusRefresh() {
        statusRefreshJob?.cancel()
        statusRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                camera?.refreshStatus()
            }
        }
    }

    fun connectWifi() {
        viewModelScope.launch {
            android.util.Log.d("ControlViewModel", "connectWifi clicked, camera=$camera")
            camera?.connectWifi()?.onFailure { e ->
                android.util.Log.e("ControlViewModel", "connectWifi error: ${e.message}", e)
                _error.value = "WiFi failed: ${e.message}"
            }
        }
    }

    fun startRecording() {
        viewModelScope.launch {
            camera?.startRecording()?.onFailure { e ->
                _error.value = "Start failed: ${e.message}"
            }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            camera?.stopRecording()?.onFailure { e ->
                _error.value = "Stop failed: ${e.message}"
            }
        }
    }

    fun setMode(mode: PresetGroup) {
        viewModelScope.launch {
            camera?.setMode(mode)?.onFailure { e ->
                _error.value = "Mode change failed: ${e.message}"
            }
        }
    }

    fun powerOff() {
        viewModelScope.launch {
            camera?.powerOff()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            cameraRepository.disconnectCamera()
        }
    }

    override fun onCleared() {
        super.onCleared()
        keepAliveJob?.cancel()
        statusRefreshJob?.cancel()
    }
}
