package com.myanim.kondi.data.prefs

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber

class UserPreferencesManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("kondi_user_prefs", Context.MODE_PRIVATE)

    var preferredSource: String?
        get() = prefs.getString("preferred_source", null)
        set(value) {
            prefs.edit().putString("preferred_source", value?.lowercase()).apply()
        }

    var downloadThreads: Int
        get() = prefs.getInt("download_threads", 6)
        set(value) {
            // Aşırı değerler (örn. 1000 eşzamanlı bağlantı) cihazı kilitler.
            val clamped = value.coerceIn(MIN_DOWNLOAD_THREADS, MAX_DOWNLOAD_THREADS)
            prefs.edit().putInt("download_threads", clamped).apply()
        }

    var enableMultiChunk: Boolean
        get() = prefs.getBoolean("enable_multi_chunk", true)
        set(value) {
            prefs.edit().putBoolean("enable_multi_chunk", value).apply()
        }
        
    var hdwpServerIp: String
        get() = prefs.getString("hdwp_server_ip", "http://111.235.150.203:7860") ?: "http://111.235.150.203:7860"
        set(value) {
            prefs.edit().putString("hdwp_server_ip", value).apply()
        }

    var hdwpTargetPhone: String
        get() = prefs.getString("hdwp_target_phone", "") ?: ""
        set(value) {
            prefs.edit().putString("hdwp_target_phone", value).apply()
        }
        
    fun extractHostAndSave(url: String) {
        try {
            val lowerUrl = url.lowercase().trim()
            // Önce URL'nin HOST kısmı ayıklanır. Aksi halde yol içinde geçen bir
            // kelime (örn. ".../tautology.mp4") yanlış video kaynağı olarak seçilir.
            val host = HOST_PATTERN.find(lowerUrl)?.groupValues?.get(1)?.substringBefore(':')
                ?: lowerUrl
            val source = when {
                host.contains("sibnet") -> "sibnet"
                host.contains("tau") -> "tau-video"
                host.contains("ok.ru") || host.contains("odnoklassniki") -> "ok.ru"
                host.contains("dood") -> "dood"
                host.contains("uqload") -> "uqload"
                host.contains("voe") -> "voe"
                host.contains("streamtape") -> "streamtape"
                else -> null
            }
            if (source != null) {
                preferredSource = source
            }
        } catch (e: Exception) {
            // Sessizce yutmak yerine kaydedilir: kaynak çıkarımı başarısız olursa
            // nedeninin loglarda görünmesi gerekir.
            Timber.w(e, "Video kaynağı çıkarılamadı: $url")
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: UserPreferencesManager? = null

        const val MIN_DOWNLOAD_THREADS = 1
        const val MAX_DOWNLOAD_THREADS = 16

        /** URL'den host kısmını ayıklamak için kullanılan desen. */
        private val HOST_PATTERN = Regex("^[a-z][a-z0-9+.-]*://([^/?#]+)")

        fun getInstance(context: Context): UserPreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserPreferencesManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
