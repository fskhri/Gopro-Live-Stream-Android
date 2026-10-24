package com.gopro.rebuild.camera

import com.gopro.rebuild.ble.GoProDevice
import com.gopro.rebuild.ble.GoProScanner
import com.gopro.rebuild.wifi.GoProWifiApi
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.gopro.rebuild.wifi.WifiConnectionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val CAMERA_SESSION_TAG = "CameraRepository"

@Singleton
class CameraRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanner: GoProScanner,
    private val wifiManager: WifiConnectionManager,
    private val wifiApi: GoProWifiApi
) {
    private val _connectedCamera = MutableStateFlow<GoProCamera?>(null)
    val connectedCamera: StateFlow<GoProCamera?> = _connectedCamera.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<GoProDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<GoProDevice>> = _discoveredDevices.asStateFlow()
    private val preferences = context.getSharedPreferences("camera_sessions", Context.MODE_PRIVATE)
    private val savedAddressKey = "last_camera_address"
    private val savedNameKey = "last_camera_name"

    @SuppressLint("MissingPermission")
    fun getSavedCamera(): GoProDevice? {
        val address = preferences.getString(savedAddressKey, null) ?: return null
        val name = preferences.getString(savedNameKey, null) ?: "GoPro"
        val bluetoothDevice = try {
            BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address)
        } catch (error: IllegalArgumentException) {
            Log.w(CAMERA_SESSION_TAG, "Saved camera address is invalid", error)
            null
        } ?: return null
        return GoProDevice(
            name = name,
            address = address,
            bluetoothDevice = bluetoothDevice,
            isPaired = bluetoothDevice.bondState == BluetoothDevice.BOND_BONDED,
            isPowerOn = false
        )
    }

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
        val index = current.indexOfFirst { existing ->
            val sameName = existing.name.isNotBlank() &&
                    !existing.name.startsWith("GoPro Camera") &&
                    existing.name.equals(device.name, ignoreCase = true)
            sameName || existing.address.equals(device.address, ignoreCase = true)
        }
        if (index >= 0) {
            val existing = current[index]
            current[index] = device.copy(
                isPaired = existing.isPaired || device.isPaired,
                bluetoothDevice = device.bluetoothDevice ?: existing.bluetoothDevice
            )
        } else {
            current.add(device)
        }
        // Prioritize devices in pairing mode, then online/advertising, then RSSI
        current.sortWith(
            compareByDescending<GoProDevice> { it.isPairingMode }
                .thenByDescending { it.isPowerOn }
                .thenByDescending { it.advertisement != null }
                .thenByDescending { it.rssi }
        )
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
            preferences.edit()
                .putString(savedAddressKey, device.address)
                .putString(savedNameKey, device.name)
                .apply()
            Result.success(camera)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Connection failed"))
        }
    }

    /**
     * Disconnect from current camera
     */
    suspend fun disconnectCamera(forgetSession: Boolean = false) {
        _connectedCamera.value?.disconnect()
        _connectedCamera.value = null
        if (forgetSession) {
            preferences.edit().remove(savedAddressKey).remove(savedNameKey).apply()
        }
    }

    /**
     * Get current camera
     */
    fun getCurrentCamera(): GoProCamera? = _connectedCamera.value
}
