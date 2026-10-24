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
    val isPaired: Boolean = false,
    val isPairingMode: Boolean = false,
    val isPowerOn: Boolean = true
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
                isStrictGoProName(name)
            }.map { device ->
                GoProDevice(
                    name = device.name ?: "GoPro",
                    address = device.address,
                    rssi = 0,
                    bluetoothDevice = device,
                    isPaired = true,
                    isPairingMode = false,
                    isPowerOn = false // Standby until live advertisement seen
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
            .filter { advertisement ->
                val name = advertisement.name ?: ""
                val mfgData = advertisement.manufacturerData(GoProBleConstants.COMPANY_ID_GOPRO)
                    ?: if (advertisement.manufacturerData?.code == GoProBleConstants.COMPANY_ID_GOPRO) {
                        advertisement.manufacturerData?.data
                    } else null

                val hasGoProMfg = mfgData != null
                val hasGoProService = advertisement.uuids.any {
                    it == GoProBleConstants.SERVICE_CONTROL_POINT ||
                    it == GoProBleConstants.SERVICE_WIFI_AP ||
                    it == GoProBleConstants.SERVICE_CAMERA_MGMT
                }
                val hasStrictName = isStrictGoProName(name)

                (hasGoProMfg || hasGoProService || hasStrictName).also { matched ->
                    if (matched) {
                        val isPairing = if (mfgData != null && mfgData.size >= 2) {
                            (mfgData[1].toInt() and GoProBleConstants.StatusFlags.PAIRING) != 0
                        } else false
                        Log.i(TAG, ">>> MATCHED GOPRO: name='$name', addr=${advertisement.address}, mfg=$hasGoProMfg, svc=$hasGoProService, pairing=$isPairing <<<")
                    }
                }
            }
            .map { advertisement ->
                val mfgData = advertisement.manufacturerData(GoProBleConstants.COMPANY_ID_GOPRO)
                    ?: if (advertisement.manufacturerData?.code == GoProBleConstants.COMPANY_ID_GOPRO) {
                        advertisement.manufacturerData?.data
                    } else null

                val isPairingMode = if (mfgData != null && mfgData.size >= 2) {
                    (mfgData[1].toInt() and GoProBleConstants.StatusFlags.PAIRING) != 0
                } else false

                val isPowerOn = if (mfgData != null && mfgData.size >= 2) {
                    (mfgData[1].toInt() and GoProBleConstants.StatusFlags.HOST_POWER_ON) != 0
                } else true

                val displayName = when {
                    !advertisement.name.isNullOrBlank() -> advertisement.name!!
                    mfgData != null && mfgData.size >= 3 -> {
                        val modelId = mfgData[2].toInt() and 0xFF
                        "GoPro Camera ($modelId)"
                    }
                    else -> "GoPro Camera"
                }

                GoProDevice(
                    name = displayName,
                    address = advertisement.address,
                    rssi = advertisement.rssi,
                    advertisement = advertisement,
                    isPaired = false,
                    isPairingMode = isPairingMode,
                    isPowerOn = isPowerOn
                )
            }
    }

    /**
     * Strict check for GoPro naming.
     * Rejects generic prefixes ("HD", "GP", "Max") that caused ghost devices.
     */
    private fun isStrictGoProName(name: String): Boolean {
        if (name.isBlank()) return false
        val trimmed = name.trim()
        if (trimmed.startsWith("GoPro", ignoreCase = true)) return true
        if (trimmed.startsWith("HERO", ignoreCase = true)) return true
        if (trimmed.equals("Fusion", ignoreCase = true)) return true
        if (trimmed.matches(Regex("^(?i)GP\\s*\\d{4}$"))) return true
        return false
    }
}
