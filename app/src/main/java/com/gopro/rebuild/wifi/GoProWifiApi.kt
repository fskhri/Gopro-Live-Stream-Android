package com.gopro.rebuild.wifi

import android.util.Log
import com.gopro.rebuild.camera.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GoProWifi"
private const val CAMERA_IP = "10.5.5.9"
private const val BASE_URL = "http://$CAMERA_IP"

@Serializable
data class MediaList(
    val id: String = "",
    val media: List<MediaDirectory> = emptyList()
)

@Serializable
data class MediaDirectory(
    val d: String = "",
    val fs: List<MediaFile> = emptyList()
)

@Serializable
data class MediaFile(
    val n: String = "",
    val cre: Long = 0,
    val mod: Long = 0,
    val s: String = "",
    val glrv: Int? = null,
    val ls: Int? = null,
    val g: String? = null
)

@Serializable
data class CameraState(
    val status: Map<String, Int> = emptyMap(),
    val settings: Map<String, Int> = emptyMap()
)

@Serializable
data class PresetList(
    val presetGroupArray: List<PresetGroupData> = emptyList()
)

@Serializable
data class PresetGroupData(
    val id: Int = 0,
    val presetArray: List<PresetData> = emptyList()
)

@Serializable
data class PresetData(
    val id: Int = 0,
    val mode: Int = 0,
    val titleId: Int = 0,
    val icon: Int = 0,
    val isModified: Boolean = false,
    val isFixed: Boolean = false,
    val settingArray: List<PresetSetting> = emptyList()
)

@Serializable
data class PresetSetting(
    val id: Int = 0,
    val value: Int = 0
)

@Singleton
class GoProWifiApi @Inject constructor(
    private val wifiConnectionManager: WifiConnectionManager
) {

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun getClient(): OkHttpClient {
        val net = wifiConnectionManager.getActiveNetwork()
        return if (net != null) {
            baseClient.newBuilder()
                .socketFactory(net.socketFactory)
                .build()
        } else {
            baseClient
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    // === Shutter ===
    suspend fun shutterStart(): Result<Unit> {
        val gpResult = sendCommand("/gp/gpControl/command/shutter?p=1")
        if (gpResult.isSuccess) return gpResult
        return sendCommand("/gopro/camera/shutter/start")
    }

    suspend fun shutterStop(): Result<Unit> {
        val gpResult = sendCommand("/gp/gpControl/command/shutter?p=0")
        if (gpResult.isSuccess) return gpResult
        return sendCommand("/gopro/camera/shutter/stop")
    }

    // === Mode / Preset ===
    suspend fun setPresetGroup(group: PresetGroup): Result<Unit> {
        val modeId = when (group) {
            PresetGroup.VIDEO -> 0
            PresetGroup.PHOTO -> 1
            PresetGroup.TIMELAPSE -> 2
        }
        val gpResult = sendCommand("/gp/gpControl/command/sub_mode?mode=$modeId&sub_mode=0")
        if (gpResult.isSuccess) return gpResult
        return sendCommand("/gopro/camera/presets/set_group?id=${group.id}")
    }

    suspend fun setPreset(presetId: Int): Result<Unit> =
        sendCommand("/gopro/camera/presets/load?id=$presetId")

    suspend fun getPresets(): Result<PresetList> = withContext(Dispatchers.IO) {
        try {
            val response = httpGet("/gopro/camera/presets/get")
            if (response != null) {
                Result.success(json.decodeFromString<PresetList>(response))
            } else {
                Result.failure(Exception("Failed to get presets"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // === Settings ===
    suspend fun setSetting(settingId: Int, value: Int): Result<Unit> {
        val gpResult = sendCommand("/gp/gpControl/setting/$settingId/$value")
        if (gpResult.isSuccess) return gpResult
        return sendCommand("/gopro/camera/setting?setting=$settingId&option=$value")
    }

    suspend fun setResolution(res: VideoResolution): Result<Unit> =
        setSetting(SettingIds.RESOLUTION, res.id)

    suspend fun setFrameRate(fps: FrameRate): Result<Unit> =
        setSetting(SettingIds.FPS, fps.id)

    suspend fun setFov(fov: FieldOfView): Result<Unit> {
        val gpResult = setSetting(4, fov.id)
        if (gpResult.isSuccess) return gpResult
        return setSetting(SettingIds.FOV, fov.id)
    }

    suspend fun setHyperSmooth(hs: HyperSmooth): Result<Unit> =
        setSetting(SettingIds.HYPERSMOOTH, hs.id)

    suspend fun setHindsight(hindsight: Hindsight): Result<Unit> =
        setSetting(SettingIds.HINDSIGHT, hindsight.id)

    suspend fun setTimerDuration(timer: Timer): Result<Unit> =
        setSetting(SettingIds.TIMER, timer.id)

    suspend fun setWhiteBalance(wb: WhiteBalance): Result<Unit> =
        setSetting(SettingIds.WHITE_BALANCE, wb.id)

    suspend fun setIsoMax(iso: ISO): Result<Unit> {
        val gpResult = setSetting(13, iso.id)
        if (gpResult.isSuccess) return gpResult
        return setSetting(SettingIds.ISO_MAX, iso.id)
    }

    suspend fun setIsoMin(iso: ISO): Result<Unit> =
        setSetting(SettingIds.ISO_MIN, iso.id)

    suspend fun setSharpness(sharpness: Sharpness): Result<Unit> =
        setSetting(SettingIds.SHARPNESS, sharpness.id)

    suspend fun setEvComp(ev: EVComp): Result<Unit> =
        setSetting(SettingIds.EV_COMP, ev.id)

    suspend fun setColor(color: ProtuneColor): Result<Unit> =
        setSetting(SettingIds.COLOR, color.id)

    // === Control ===
    suspend fun sleep(): Result<Unit> {
        val gpResult = sendCommand("/gp/gpControl/command/system/sleep")
        if (gpResult.isSuccess) return gpResult
        return sendCommand("/gopro/camera/control/sleep")
    }

    suspend fun setDateTime(): Result<Unit> {
        val now = System.currentTimeMillis() / 1000
        return sendCommand("/gopro/camera/set_date_time?date_time=$now")
    }

    suspend fun locate(enable: Boolean): Result<Unit> {
        val gpResult = sendCommand("/gp/gpControl/command/system/locate?p=${if (enable) 1 else 0}")
        if (gpResult.isSuccess) return gpResult
        return sendCommand("/gopro/camera/control/highlight?mode=${if (enable) 1 else 0}")
    }

    // === Status & Hardware Info ===

    /**
     * Get camera status and settings. Supports both gpControl (/gp/gpControl/status)
     * and Open GoPro (/gopro/camera/state).
     */
    suspend fun getState(): Result<CameraState> = withContext(Dispatchers.IO) {
        try {
            // Try gpControl first (Hero 5/6/7/8)
            var response = httpGet("/gp/gpControl/status")
            if (response == null) {
                // Try Open GoPro (Hero 9/10/11/12/13)
                response = httpGet("/gopro/camera/state")
            }

            if (response != null) {
                val jsonRoot = JSONObject(response)
                val statusMap = mutableMapOf<String, Int>()
                val settingsMap = mutableMapOf<String, Int>()

                if (jsonRoot.has("status")) {
                    val statusObj = jsonRoot.getJSONObject("status")
                    val keys = statusObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        statusMap[key] = statusObj.optInt(key, 0)
                    }
                }

                if (jsonRoot.has("settings")) {
                    val settingsObj = jsonRoot.getJSONObject("settings")
                    val keys = settingsObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        settingsMap[key] = settingsObj.optInt(key, 0)
                    }
                }

                // Battery calculation fallback:
                // If status "70" (percentage) is missing or 0, convert status "2" (bars 0..3) to percent
                val batteryPct = statusMap["70"] ?: 0
                if (batteryPct <= 0 && statusMap.containsKey("2")) {
                    val bars = statusMap["2"] ?: 0
                    val computedPct = when (bars) {
                        3 -> 100
                        2 -> 65
                        1 -> 35
                        else -> 10
                    }
                    statusMap["70"] = computedPct
                }

                Result.success(CameraState(status = statusMap, settings = settingsMap))
            } else {
                Result.failure(Exception("Failed to get camera state"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getState error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get hardware info (model, serial, firmware, mac, ssid).
     * Supports /gp/gpControl and /gopro/camera/info.
     */
    suspend fun getHardwareInfo(): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        try {
            val result = mutableMapOf<String, String>()

            // Try gpControl info first
            val gpResponse = httpGet("/gp/gpControl")
            if (gpResponse != null) {
                val root = JSONObject(gpResponse)
                if (root.has("info")) {
                    val info = root.getJSONObject("info")
                    info.keys().forEach { key ->
                        result[key] = info.optString(key, "")
                    }
                    return@withContext Result.success(result)
                }
            }

            // Fallback to Open GoPro
            val openGpResponse = httpGet("/gopro/camera/info")
            if (openGpResponse != null) {
                val root = JSONObject(openGpResponse)
                root.keys().forEach { key ->
                    result[key] = root.optString(key, "")
                }
                return@withContext Result.success(result)
            }

            Result.failure(Exception("Failed to get hardware info"))
        } catch (e: Exception) {
            Log.e(TAG, "getHardwareInfo error: ${e.message}")
            Result.failure(e)
        }
    }

    // === Media ===
    suspend fun getMediaList(): Result<MediaList> = withContext(Dispatchers.IO) {
        try {
            var response = httpGet("/gp/gpMediaList")
            if (response == null) {
                response = httpGet("/gopro/media/list")
            }
            if (response != null) {
                Result.success(json.decodeFromString<MediaList>(response))
            } else {
                Result.failure(Exception("Failed to get media list"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMediaList error: ${e.message}")
            Result.failure(e)
        }
    }

    fun getMediaUrl(directory: String, filename: String): String =
        "$BASE_URL/videos/DCIM/$directory/$filename"

    fun getThumbnailUrl(directory: String, filename: String): String =
        "$BASE_URL/gopro/media/thumbnail?path=$directory/$filename"

    fun getScreennailUrl(directory: String, filename: String): String =
        "$BASE_URL/gopro/media/screennail?path=$directory/$filename"

    suspend fun deleteMedia(directory: String, filename: String): Result<Unit> {
        val gpResult = sendCommand("/gp/gpControl/command/storage/delete?p=$directory/$filename")
        if (gpResult.isSuccess) return gpResult
        return sendCommand("/gopro/media/delete/file?path=$directory/$filename")
    }

    suspend fun deleteAllMedia(): Result<Unit> {
        val gpResult = sendCommand("/gp/gpControl/command/storage/delete/all")
        if (gpResult.isSuccess) return gpResult
        return sendCommand("/gopro/media/delete/all")
    }

    // === Live Preview ===
    suspend fun startPreview(): Result<Unit> {
        // Standard gpControl for Hero 4/5/6/7/8 (Hero 6 uses clean c1=restart without proto_v2)
        val gpResult = sendCommand("/gp/gpControl/execute?p1=gpStream&c1=restart")
        if (gpResult.isSuccess) return gpResult
        // Fallback for spherical (Fusion) or OpenGoPro (Hero 9+)
        val protoResult = sendCommand("/gp/gpControl/execute?p1=gpStream&a1=proto_v2&c1=restart")
        if (protoResult.isSuccess) return protoResult
        return sendCommand("/gopro/camera/stream/start")
    }

    suspend fun stopPreview(): Result<Unit> {
        val gpResult = sendCommand("/gp/gpControl/execute?p1=gpStream&c1=stop")
        if (gpResult.isSuccess) return gpResult
        return sendCommand("/gopro/camera/stream/stop")
    }

    fun getPreviewUrl(): String = "udp://0.0.0.0:8554"

    // === Low-level HTTP ===

    private suspend fun sendCommand(endpoint: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL$endpoint")
                .get()
                .build()

            getClient().newCall(request).execute().use { response ->
                val body = response.body?.string()
                Log.d(TAG, "GET $endpoint -> HTTP ${response.code} body=$body")
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "GET $endpoint failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun httpGet(endpoint: String): String? {
        return try {
            val request = Request.Builder()
                .url("$BASE_URL$endpoint")
                .get()
                .build()

            getClient().newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "GET $endpoint failed: ${e.message}")
            null
        }
    }
}
