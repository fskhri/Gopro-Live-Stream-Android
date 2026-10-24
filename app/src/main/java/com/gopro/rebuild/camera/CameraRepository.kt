package com.gopro.rebuild.camera

import com.gopro.rebuild.ble.GoProDevice
import com.gopro.rebuild.ble.GoProScanner
import com.gopro.rebuild.wifi.GoProWifiApi
import com.gopro.rebuild.wifi.WifiConnectionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraRepository @Inject constructor(
    private val scanner: GoProScanner,
    private val wifiManager: WifiConnectionManager,
    private val wifiApi: GoProWifiApi
) {
    private val _connectedCamera = MutableStateFlow<GoProCamera?>(null)
    val connectedCamera: StateFlow<GoProCamera?> = _connectedCamera.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<GoProDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<GoProDevice>> = _discoveredDevices.asStateFlow()

    fun getPairedDevices(): List<GoProDevice> {
        return scanner.getPairedGoProDevices()
    }

    /**
     * Scan for GoPro cameras via BLE
     */
    fun scanForCameras(): Flow<GoProDevice> {
        return scanner.scan()
    }

    /**
     * Add discovered device to list
     */
    fun addDiscoveredDevice(device: GoProDevice) {
        val current = _discoveredDevices.value.toMutableList()
        val index = current.indexOfFirst { it.address == device.address }
        if (index >= 0) {
            current[index] = device
        } else {
            current.add(device)
        }
        _discoveredDevices.value = current
    }

    /**
     * Clear discovered devices
     */
    fun clearDiscoveredDevices() {
        _discoveredDevices.value = emptyList()
    }

    /**
     * Connect to a camera
     */
    suspend fun connectToCamera(device: GoProDevice): Result<GoProCamera> {
        // Disconnect existing camera if any
        _connectedCamera.value?.disconnect()

        val camera = GoProCamera(device, wifiManager, wifiApi)
        val result = camera.connect()

        return if (result.isSuccess) {
            _connectedCamera.value = camera
            Result.success(camera)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Connection failed"))
        }
    }

    /**
     * Disconnect from current camera
     */
    suspend fun disconnectCamera() {
        _connectedCamera.value?.disconnect()
        _connectedCamera.value = null
    }

    /**
     * Get current camera
     */
    fun getCurrentCamera(): GoProCamera? = _connectedCamera.value
}
