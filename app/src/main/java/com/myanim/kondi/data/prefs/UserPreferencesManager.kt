package com.myanim.kondi.data.prefs

import android.content.Context
import android.content.SharedPreferences

class UserPreferencesManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("kondi_user_prefs", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var INSTANCE: UserPreferencesManager? = null

        fun getInstance(context: Context): UserPreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserPreferencesManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    var preferredSource: String?
        get() = prefs.getString("preferred_source", null)
        set(value) {
            prefs.edit().putString("preferred_source", value?.lowercase()).apply()
        }

    var downloadThreads: Int
        get() = prefs.getInt("download_threads", 3)
        set(value) {
            prefs.edit().putInt("download_threads", value).apply()
        }

    var enableMultiChunk: Boolean
        get() = prefs.getBoolean("enable_multi_chunk", false)
        set(value) {
            prefs.edit().putBoolean("enable_multi_chunk", value).apply()
        }
        
    fun extractHostAndSave(url: String) {
        try {
            val lowerUrl = url.lowercase()
            val host = when {
                lowerUrl.contains("sibnet") -> "sibnet"
                lowerUrl.contains("tau-video") || lowerUrl.contains("tau") -> "tau-video"
                lowerUrl.contains("ok.ru") || lowerUrl.contains("odnoklassniki") -> "ok.ru"
                lowerUrl.contains("dood") -> "dood"
                lowerUrl.contains("uqload") -> "uqload"
                lowerUrl.contains("voe") -> "voe"
                lowerUrl.contains("streamtape") -> "streamtape"
                else -> null
            }
            if (host != null) {
                preferredSource = host
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
}
