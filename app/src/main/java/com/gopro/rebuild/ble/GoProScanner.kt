package com.gopro.rebuild.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import com.juul.kable.Advertisement
import com.juul.kable.Scanner
import com.juul.kable.logs.Logging
import com.juul.kable.logs.SystemLogEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GoProScanner"

data class GoProDevice(
    val name: String,
    val address: String,
    val rssi: Int = 0,
    val advertisement: Advertisement? = null,
    val bluetoothDevice: BluetoothDevice? = null,
    val isPaired: Boolean = false
)

@Singleton
class GoProScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val scanner = Scanner {
        logging {
            engine = SystemLogEngine
            level = Logging.Level.Events
        }
    }

    /**
     * Get paired/bonded GoPro devices from Android BluetoothManager
     */
    @SuppressLint("MissingPermission")
    fun getPairedGoProDevices(): List<GoProDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        return try {
            adapter.bondedDevices.filter { device ->
                val name = device.name ?: ""
                isGoProDeviceName(name)
            }.map { device ->
                GoProDevice(
                    name = device.name ?: "GoPro",
                    address = device.address,
                    rssi = 0,
                    bluetoothDevice = device,
                    isPaired = true
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting paired devices", e)
            emptyList()
        }
    }

    /**
     * Scan for GoPro cameras via BLE
     */
    @SuppressLint("MissingPermission")
    fun scan(): Flow<GoProDevice> {
        return scanner.advertisements
            .onEach { adv ->
                Log.d(TAG, "Saw BLE device: name='${adv.name}', addr=${adv.address}, rssi=${adv.rssi}")
            }
            .filter { advertisement ->
                val name = advertisement.name ?: ""
                val isGoProName = isGoProDeviceName(name)

                val hasGoProService = advertisement.uuids.any {
                    it == GoProBleConstants.SERVICE_CONTROL_POINT ||
                    it == GoProBleConstants.SERVICE_WIFI_AP ||
                    it == GoProBleConstants.SERVICE_CAMERA_MGMT
                }

                (isGoProName || hasGoProService).also { matched ->
                    if (matched) {
                        Log.i(TAG, ">>> MATCHED GOPRO: name='$name', addr=${advertisement.address} <<<")
                    }
                }
            }
            .map { advertisement ->
                GoProDevice(
                    name = advertisement.name ?: "HERO Camera",
                    address = advertisement.address,
                    rssi = advertisement.rssi,
                    advertisement = advertisement,
                    isPaired = false
                )
            }
    }

    private fun isGoProDeviceName(name: String): Boolean {
        if (name.isBlank()) return false
        return name.startsWith("GoPro", ignoreCase = true) ||
                name.startsWith("HERO", ignoreCase = true) ||
                name.startsWith("Hero", ignoreCase = true) ||
                name.startsWith("HD", ignoreCase = true) ||
                name.startsWith("GP", ignoreCase = true) ||
                name.startsWith("Fusion", ignoreCase = true) ||
                name.startsWith("Max", ignoreCase = true)
    }
}
