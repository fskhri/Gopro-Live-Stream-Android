package com.gopro.rebuild.tiktok

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tiktok_settings")

@Singleton
class TiktokRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_COOKIES = stringPreferencesKey("tiktok_cookies")
    }

    val cookiesFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_COOKIES]
    }

    suspend fun getCookies(): String? {
        return context.dataStore.data.map { it[KEY_COOKIES] }.firstOrNull()
    }

    suspend fun saveCookies(cookies: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_COOKIES] = cookies
        }
    }

    suspend fun clearCookies() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_COOKIES)
        }
    }

    /**
     * Parses cookies from raw string, Netscape format, or JSON formats
     * (e.g. user_data_tiktok.json, EditThisCookie, or name-value maps).
     */
    fun parseCookieInput(raw: String): Result<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Cookie input kosong."))
        }

        val pairs = mutableMapOf<String, String>()

        fun addPair(name: String?, value: String?) {
            val n = name?.trim() ?: ""
            val v = value?.trim() ?: ""
            if (n.isNotEmpty() && v.isNotEmpty()) {
                pairs[n] = v
            }
        }

        // Try JSON format
        var parsedAsJson = false
        try {
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    addPair(obj.optString("name"), obj.optString("value"))
                }
                parsedAsJson = true
            } else if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val obj = JSONObject(trimmed)
                if (obj.has("cookies")) {
                    val c = obj.get("cookies")
                    if (c is JSONArray) {
                        for (i in 0 until c.length()) {
                            val item = c.optJSONObject(i) ?: continue
                            addPair(item.optString("name"), item.optString("value"))
                        }
                    } else if (c is JSONObject) {
                        val keys = c.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            addPair(k, c.optString(k))
                        }
                    }
                } else {
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = obj.opt(k)
                        if (v != null) {
                            addPair(k, v.toString())
                        }
                    }
                }
                parsedAsJson = true
            }
        } catch (_: Exception) {
            parsedAsJson = false
        }

        // Try Netscape or semicolon delimited
        if (!parsedAsJson || pairs.isEmpty()) {
            val lines = trimmed.split("\n")
            for (line in lines) {
                val l = line.trim()
                if (l.isEmpty() || l.startsWith("#")) continue
                if (l.contains("\t")) {
                    // Netscape format: domain, flag, path, secure, expiration, name, value
                    val parts = l.split("\t")
                    if (parts.size >= 7) {
                        addPair(parts[5], parts[6])
                    }
                } else {
                    for (seg in l.split(";")) {
                        val idx = seg.indexOf('=')
                        if (idx > 0) {
                            addPair(seg.substring(0, idx), seg.substring(idx + 1))
                        }
                    }
                }
            }
        }

        if (pairs.isEmpty()) {
            return Result.failure(IllegalArgumentException("Format cookie tidak dikenali."))
        }

        val resultString = pairs.entries.joinToString("; ") { "${it.key}=${it.value}" }
        return Result.success(resultString)
    }

    fun hasSessionId(cookies: String?): Boolean {
        if (cookies.isNullOrEmpty()) return false
        return cookies.contains("sessionid=") || cookies.contains("sessionid =")
    }

    fun hasSidTt(cookies: String?): Boolean {
        if (cookies.isNullOrEmpty()) return false
        return cookies.contains("sid_tt=") || cookies.contains("sid_tt =")
    }
}
