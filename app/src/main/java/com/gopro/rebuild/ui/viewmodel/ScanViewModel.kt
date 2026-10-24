package com.gopro.rebuild.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gopro.rebuild.ble.GoProDevice
import com.gopro.rebuild.camera.CameraRepository
import com.gopro.rebuild.wifi.GoProWifiApi
import com.gopro.rebuild.wifi.WifiConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val wifiApi: GoProWifiApi,
    private val wifiManager: WifiConnectionManager
) : ViewModel() {

    private val _devices = MutableStateFlow<List<GoProDevice>>(emptyList())
    val devices: StateFlow<List<GoProDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _filterOnlyPairing = MutableStateFlow(true)
    val filterOnlyPairing: StateFlow<Boolean> = _filterOnlyPairing.asStateFlow()

    private var scanJob: Job? = null

    fun setFilterOnlyPairing(onlyPairing: Boolean) {
        _filterOnlyPairing.value = onlyPairing
        updateFilteredDevices()
    }

    private fun updateFilteredDevices() {
        val all = cameraRepository.discoveredDevices.value
        _devices.value = if (_filterOnlyPairing.value) {
            all.filter { it.isPairingMode }
        } else {
            all
        }
    }

    fun startScan() {
        scanJob?.cancel()
        cameraRepository.clearDiscoveredDevices()

        // 1. Show paired/bonded GoPro devices if not in strict pairing mode
        val paired = cameraRepository.getPairedDevices()
        paired.forEach { cameraRepository.addDiscoveredDevice(it) }
        updateFilteredDevices()

        // 2. Start BLE scanner for active advertisements
        scanJob = viewModelScope.launch {
            cameraRepository.scanForCameras()
                .onStart { _isScanning.value = true }
                .catch { e ->
                    _error.value = "Scan failed: ${e.message}"
                    _isScanning.value = false
                }
                .collect { device ->
                    cameraRepository.addDiscoveredDevice(device)
                    updateFilteredDevices()
                }
        }

        // Auto-stop scan after 15 seconds
        viewModelScope.launch {
            delay(15_000)
            stopScan()
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
    }

    fun connectToDevice(device: GoProDevice, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isConnecting.value = true
            _error.value = null

            val result = cameraRepository.connectToCamera(device)

            _isConnecting.value = false

            result.onSuccess {
                onSuccess()
            }.onFailure { e ->
                _error.value = "Connection failed: ${e.message}"
            }
        }
    }

    /**
     * Connect directly via WiFi if phone is already connected to GoPro WiFi AP
     */
    fun directWifiConnect(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isConnecting.value = true
            _error.value = null

            // Bind to active GoPro Wi-Fi if available
            wifiManager.checkAndBindActiveWifi()

            // Create a virtual device representing WiFi-connected camera
            val virtualDevice = GoProDevice(
                name = "HERO Camera (WiFi)",
                address = "10.5.5.9",
                isPaired = true
            )

            // Verify HTTP reachability
            val pingResult = wifiApi.getState()
            if (pingResult.isSuccess) {
                val hwInfo = wifiApi.getHardwareInfo().getOrNull()
                val realName = com.gopro.rebuild.camera.GoProModel.getDisplayName(
                    hwInfo?.get("model_name") ?: hwInfo?.get("model_number"),
                    "HERO6 Black"
                )
                val deviceWithName = virtualDevice.copy(name = realName)
                val result = cameraRepository.connectToCamera(deviceWithName)
                _isConnecting.value = false
                if (result.isSuccess) {
                    onSuccess()
                } else {
                    _error.value = "Failed to connect to camera"
                }
            } else {
                _isConnecting.value = false
                _error.value = "Camera not reachable at 10.5.5.9. Connect phone to GoPro WiFi first."
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
