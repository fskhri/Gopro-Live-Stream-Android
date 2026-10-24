package com.gopro.rebuild.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "WifiManager"

sealed class WifiConnectionState {
    object Disconnected : WifiConnectionState()
    object Connecting : WifiConnectionState()
    object Connected : WifiConnectionState()
    data class Error(val message: String) : WifiConnectionState()
}

@Singleton
class WifiConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _connectionState = MutableStateFlow<WifiConnectionState>(WifiConnectionState.Disconnected)
    val connectionState: StateFlow<WifiConnectionState> = _connectionState.asStateFlow()

    private var currentNetwork: Network? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // System-wide Wi-Fi state listener to auto-detect GoPro connection
    private val systemWifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            checkAndBindActiveWifi()
        }

        override fun onLost(network: Network) {
            if (network == currentNetwork) {
                Log.d(TAG, "GoPro Wi-Fi lost: $network")
                currentNetwork = null
                connectivityManager.bindProcessToNetwork(null)
                _connectionState.value = WifiConnectionState.Disconnected
            }
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivityManager.registerNetworkCallback(request, systemWifiCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register system wifi callback: ${e.message}")
        }
        checkAndBindActiveWifi()
    }

    /**
     * Inspect all active networks. If any Wi-Fi network has a GoPro IP range (10.5.5.x)
     * or gateway 10.5.5.9, bind the process to it immediately.
     */
    fun checkAndBindActiveWifi(): Boolean {
        try {
            for (network in connectivityManager.allNetworks) {
                val caps = connectivityManager.getNetworkCapabilities(network) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    val linkProps = connectivityManager.getLinkProperties(network) ?: continue
                    val isGoPro = linkProps.routes.any { it.gateway?.hostAddress == "10.5.5.9" } ||
                            linkProps.linkAddresses.any { it.address.hostAddress?.startsWith("10.5.5.") == true }

                    if (isGoPro) {
                        currentNetwork = network
                        // Do NOT bind the entire process: GoPro Wi-Fi has no internet.
                        // Sockets to GoPro use network.bindSocket / socketFactory explicitly.
                        // Default process network remains cellular/WAN for TikTok RTMP.
                        _connectionState.value = WifiConnectionState.Connected
                        Log.d(TAG, "Detected GoPro WiFi network (per-socket routing): $network")
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking active WiFi: ${e.message}")
        }
        return false
    }

    fun getActiveNetwork(): Network? {
        if (currentNetwork == null) {
            checkAndBindActiveWifi()
        }
        return currentNetwork
    }

    /**
     * Connect to GoPro WiFi network
     */
    @SuppressLint("MissingPermission")
    suspend fun connectToGoProWifi(ssid: String, password: String): Result<Unit> {
        Log.d(TAG, "Connecting to WiFi: $ssid")
        _connectionState.value = WifiConnectionState.Connecting

        // Check if already connected
        if (checkAndBindActiveWifi()) {
            return Result.success(Unit)
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ uses NetworkSpecifier
            connectWithNetworkSpecifier(ssid, password)
        } else {
            // Legacy WiFi connection
            connectLegacy(ssid, password)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectWithNetworkSpecifier(ssid: String, password: String): Result<Unit> {
        return suspendCancellableCoroutine { continuation ->
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password)
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "WiFi connected: $ssid")
                    currentNetwork = network
                    // Sockets to camera are explicitly bound via socketFactory or bindSocket.
                    // Keep process default network on cellular/WAN for TikTok RTMP.
                    _connectionState.value = WifiConnectionState.Connected
                    if (continuation.isActive) {
                        continuation.resume(Result.success(Unit))
                    }
                }

                override fun onUnavailable() {
                    Log.e(TAG, "WiFi unavailable")
                    _connectionState.value = WifiConnectionState.Error("Network unavailable")
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(Exception("Network unavailable")))
                    }
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "WiFi connection lost")
                    if (network == currentNetwork) {
                        currentNetwork = null
                        connectivityManager.bindProcessToNetwork(null)
                        _connectionState.value = WifiConnectionState.Disconnected
                    }
                }
            }

            networkCallback = callback
            connectivityManager.requestNetwork(request, callback)

            continuation.invokeOnCancellation {
                try {
                    connectivityManager.unregisterNetworkCallback(callback)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun connectLegacy(ssid: String, password: String): Result<Unit> {
        return try {
            val config = WifiConfiguration().apply {
                SSID = "\"$ssid\""
                preSharedKey = "\"$password\""
            }

            val netId = wifiManager.addNetwork(config)
            if (netId == -1) {
                return Result.failure(Exception("Failed to add network"))
            }

            wifiManager.disconnect()
            val success = wifiManager.enableNetwork(netId, true)
            if (success) {
                wifiManager.reconnect()
                checkAndBindActiveWifi()
                _connectionState.value = WifiConnectionState.Connected
                Result.success(Unit)
            } else {
                _connectionState.value = WifiConnectionState.Error("Failed to enable network")
                Result.failure(Exception("Failed to enable network"))
            }
        } catch (e: Exception) {
            _connectionState.value = WifiConnectionState.Error(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting WiFi")
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
        networkCallback = null
        currentNetwork = null
        connectivityManager.bindProcessToNetwork(null)
        _connectionState.value = WifiConnectionState.Disconnected
    }

    fun isConnectedToGoPro(): Boolean {
        if (_connectionState.value == WifiConnectionState.Connected) {
            return true
        }
        return checkAndBindActiveWifi()
    }
}
