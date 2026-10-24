package com.gopro.rebuild.tiktok

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TiktokService"

@Singleton
class TiktokService @Inject constructor(
    private val repository: TiktokRepository
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private var cachedStudioVersion: String = "1.12.0"

    private fun getDesktopUserAgent(version: String): String {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) TikTokLIVEStudio/$version " +
                "Chrome/108.0.5359.215 Electron/22.3.18-tt.8.release.main.44 " +
                "TTElectron/22.3.18-tt.8.release.main.44 Safari/537.36"
    }

    suspend fun getLiveStudioVersion(): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://tron-sg.bytelemon.com/api/sdk/check_update" +
                    "?pid=7393277106664249610&uid=7464643088460875280&branch=studio/release/stable&buildId=0"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@use
                val json = JSONObject(body)
                val v = json.optJSONObject("data")
                    ?.optJSONObject("manifest")
                    ?.optJSONObject("win32")
                    ?.optString("version")
                if (!v.isNullOrEmpty()) {
                    cachedStudioVersion = v
                    Log.d(TAG, "Fetched latest Live Studio version: $cachedStudioVersion")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check studio version: ${e.message}")
        }
        cachedStudioVersion
    }

    suspend fun checkLoginStatus(): Boolean = withContext(Dispatchers.IO) {
        val cookies = repository.getCookies() ?: return@withContext false
        try {
            val request = Request.Builder()
                .url("https://www.tiktok.com/passport/web/account/info/")
                .addHeader("Cookie", cookies)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)
                    val data = json.optJSONObject("data")
                    val userId = data?.optLong("user_id", 0L) ?: 0L
                    val screenName = data?.optString("screen_name", "")
                    Log.d(TAG, "checkLoginStatus: userId=$userId, name=$screenName")
                    return@withContext userId > 0L
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "checkLoginStatus error: ${e.message}")
        }
        false
    }

    suspend fun fetchGameTags(): Map<String, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String>()
        try {
            val url = "https://webcast16-normal-c-useast2a.tiktokv.com/webcast/room/hashtag/list/"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", getDesktopUserAgent(cachedStudioVersion))
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)
                    val list = json.optJSONObject("data")?.optJSONArray("game_tag_list") ?: return@use
                    for (i in 0 until list.length()) {
                        val item = list.optJSONObject(i) ?: continue
                        val id = item.optString("id")
                        val name = item.optString("show_name")
                        if (id.isNotEmpty() && name.isNotEmpty()) {
                            result[id] = name
                        }
                    }
                    Log.d(TAG, "Loaded ${result.size} game tags")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchGameTags error: ${e.message}")
        }
        result
    }

    suspend fun createStream(
        title: String,
        hashtagId: String = "42",
        gameTagId: String = "0",
        enableReplay: Boolean = false,
        closeRoomWhenStreamEnds: Boolean = true,
        regionPriority: String = "id",
        isMatureContent: Boolean = false
    ): Result<TiktokStreamInfo> = withContext(Dispatchers.IO) {
        val cookies = repository.getCookies()
        if (cookies.isNullOrEmpty()) {
            return@withContext Result.failure(IllegalStateException("Silakan login TikTok terlebih dahulu."))
        }
        if (!repository.hasSessionId(cookies)) {
            return@withContext Result.failure(IllegalStateException("sessionid tidak ditemukan dalam cookie. Login ulang di WebView."))
        }

        val version = getLiveStudioVersion()
        val sdkVersion = version.replace(".", "").padEnd(5, '0')
        val effectiveRegion = regionPriority.trim().lowercase()
        val userAgent = getDesktopUserAgent(version)
        val tz = try { java.util.TimeZone.getDefault().id } catch (_: Exception) { "Asia/Jakarta" }

        val candidateBases = listOf(
            "https://webcast.tiktok.com/",
            "https://webcast-normal.tiktokv.com/",
            "https://webcast16-normal-c-useast1a.tiktokv.com/"
        )

        var lastError: Exception? = null

        for (baseUrl in candidateBases) {
            try {
                val fullUrl = "${baseUrl}webcast/room/create/".toHttpUrlOrNull() ?: continue
                val urlBuilder = fullUrl.newBuilder()
                    .addQueryParameter("aid", "8311")
                    .addQueryParameter("app_name", "tiktok_live_studio")
                    .addQueryParameter("channel", "studio")
                    .addQueryParameter("device_platform", "windows")
                    .addQueryParameter("priority_region", effectiveRegion)
                    .addQueryParameter("live_mode", "6")
                    .addQueryParameter("version_code", version)
                    .addQueryParameter("webcast_sdk_version", sdkVersion)
                    .addQueryParameter("webcast_language", "en")
                    .addQueryParameter("app_language", "en")
                    .addQueryParameter("language", "en")
                    .addQueryParameter("browser_version", userAgent)
                    .addQueryParameter("browser_name", "Mozilla")
                    .addQueryParameter("browser_platform", "Win32")
                    .addQueryParameter("browser_language", "en-US")
                    .addQueryParameter("screen_height", "1080")
                    .addQueryParameter("screen_width", "1920")
                    .addQueryParameter("timezone_name", tz)
                    .addQueryParameter("device_id", "7378193331631310352")
                    .addQueryParameter("install_id", "7378196538524927745")

                val bodyBuilder = FormBody.Builder()
                    .add("title", title.trim())
                    .add("live_studio", "1")
                    .add("gen_replay", enableReplay.toString().lowercase())
                    .add("chat_auth", "1")
                    .add("cover_uri", "")
                    .add("close_room_when_close_stream", closeRoomWhenStreamEnds.toString().lowercase())
                    .add("hashtag_id", hashtagId)
                    .add("game_tag_id", gameTagId)
                    .add("screenshot_cover_status", "1")
                    .add("live_sub_only", "0")
                    .add("chat_sub_only_auth", "2")
                    .add("multi_stream_scene", "0")
                    .add("gift_auth", "1")
                    .add("chat_l2", "1")
                    .add("star_comment_switch", "true")
                    .add("multi_stream_source", "1")

                if (isMatureContent) {
                    bodyBuilder.add("age_restricted", "4")
                }

                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .header("User-Agent", userAgent)
                    .header("Cookie", cookies)
                    .post(bodyBuilder.build())
                    .build()

                Log.d(TAG, "Attempting room create on: $baseUrl with version=$version, region=$effectiveRegion")

                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string() ?: ""
                    Log.d(TAG, "Create room response from $baseUrl (${response.code}): $bodyStr")

                    if (!response.isSuccessful) {
                        lastError = IllegalStateException("HTTP ${response.code} from $baseUrl: $bodyStr")
                        return@use
                    }

                    val json = JSONObject(bodyStr)
                    val statusCode = json.optInt("status_code", -1)

                    if (statusCode == 0) {
                        val data = json.optJSONObject("data")
                            ?: return@use
                        val streamUrlObj = data.optJSONObject("stream_url")
                        val fullRtmpUrl = streamUrlObj?.optString("rtmp_push_url")
                            ?: return@use
                        val shareUrl = data.optString("share_url", "")
                        val roomId = data.optString("id_str", "").ifEmpty { data.optString("id", "") }

                        val slashIdx = fullRtmpUrl.lastIndexOf('/')
                        val baseUrlPart = if (slashIdx > 0) fullRtmpUrl.substring(0, slashIdx + 1) else fullRtmpUrl
                        val streamKeyPart = if (slashIdx > 0) fullRtmpUrl.substring(slashIdx + 1) else ""

                        Log.i(TAG, "SUCCESS! Room created: roomId=$roomId, rtmp=$fullRtmpUrl")

                        return@withContext Result.success(
                            TiktokStreamInfo(
                                fullRtmpUrl = fullRtmpUrl,
                                baseUrl = baseUrlPart,
                                streamKey = streamKeyPart,
                                shareUrl = shareUrl,
                                roomId = roomId
                            )
                        )
                    } else {
                        val prompts = json.optJSONObject("data")?.optString("prompts")
                        val msg = if (!prompts.isNullOrEmpty()) prompts else json.optString("status_msg", "Error $statusCode")
                        Log.w(TAG, "Create room failed on $baseUrl: $msg")
                        lastError = IllegalStateException("TikTok ($baseUrl): $msg")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "createStream error with $baseUrl: ${e.message}")
                lastError = e
            }
        }

        Result.failure(lastError ?: IllegalStateException("Gagal membuat live stream TikTok."))
    }

    suspend fun endStream(roomId: String): Boolean = withContext(Dispatchers.IO) {
        val cookies = repository.getCookies() ?: return@withContext false
        val version = getLiveStudioVersion()
        val userAgent = getDesktopUserAgent(version)

        val candidateBases = listOf(
            "https://webcast.tiktok.com/",
            "https://webcast-normal.tiktokv.com/"
        )

        for (baseUrl in candidateBases) {
            try {
                val url = "${baseUrl}webcast/room/finish_abnormal/".toHttpUrlOrNull()?.newBuilder()
                    ?.addQueryParameter("aid", "8311")
                    ?.addQueryParameter("app_name", "tiktok_live_studio")
                    ?.addQueryParameter("channel", "studio")
                    ?.addQueryParameter("device_platform", "windows")
                    ?.addQueryParameter("live_mode", "6")
                    ?.build() ?: continue

                val body = FormBody.Builder()
                    .add("room_id", roomId)
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("Cookie", cookies)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        val json = JSONObject(bodyStr)
                        if (json.optInt("status_code", -1) == 0) {
                            Log.d(TAG, "endStream success on $baseUrl")
                            return@withContext true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "endStream error on $baseUrl: ${e.message}")
            }
        }
        false
    }
}
