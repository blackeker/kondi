package com.myanim.kondi.util

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.myanim.kondi.data.prefs.UserPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

data class HdwpBulkItem(
    val url: String,
    val title: String,
    val fileName: String? = null,
    val caption: String? = null
)

object HdwpDownloaderHelper {
    private const val TAG = "HDWP_HELPER"
    const val DEFAULT_SERVER_URL = "http://111.235.150.203:7860"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
        
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    fun formatServerUrl(rawInput: String): String {
        var ip = rawInput.trim().removeSuffix("/")
        if (ip.isBlank() || ip.contains("localhost") || ip == "127.0.0.1") {
            return DEFAULT_SERVER_URL
        }
        if (!ip.startsWith("http://") && !ip.startsWith("https://")) {
            ip = "http://$ip"
        }
        // If IP is provided without a port (e.g., http://111.235.150.203), append :7860
        val hostPart = ip.substringAfter("://").substringBefore("/")
        if (!hostPart.contains(":") && hostPart.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))) {
            ip = "$ip:7860"
        }
        return ip
    }

    fun formatJid(phoneInput: String): String {
        val clean = phoneInput.replace("+", "")
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
            .trim()
            
        if (clean.contains("@")) return clean
        
        val normalized = when {
            clean.startsWith("05") && clean.length == 11 -> "9" + clean
            clean.startsWith("5") && clean.length == 10 -> "90$clean"
            clean.startsWith("905") -> clean
            else -> clean
        }
        return if (normalized.isNotBlank()) "$normalized@s.whatsapp.net" else ""
    }

    suspend fun sendDownloadRequest(
        context: Context, 
        videoUrl: String,
        title: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val prefManager = UserPreferencesManager.getInstance(context)
        val rawIp = prefManager.hdwpServerIp.trim()
        val targetPhone = prefManager.hdwpTargetPhone.trim()

        val serverIp = formatServerUrl(rawIp)
        val jid = formatJid(targetPhone)

        Log.d(TAG, "sendDownloadRequest: serverIp='$serverIp', jid='$jid', videoUrl='$videoUrl', title='$title'")

        if (targetPhone.isBlank() || jid.isBlank()) {
            Log.w(TAG, "WhatsApp numarası boş! İstek iptal edildi.")
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context, 
                    "Lütfen önce Ayarlar (⚙️) sayfasından WhatsApp telefon numaranızı girin.", 
                    Toast.LENGTH_LONG
                ).show()
            }
            return@withContext false
        }

        val payload = JSONObject().apply {
            put("url", videoUrl)
            put("jid", jid)
            if (!title.isNullOrBlank()) {
                val cleanTitle = title.trim()
                val fn = if (cleanTitle.endsWith(".mp4", ignoreCase = true)) cleanTitle else "$cleanTitle.mp4"
                put("title", cleanTitle)
                put("fileName", fn)
                put("filename", fn)
                put("caption", cleanTitle)
            }
        }.toString()

        val targetEndpoint = "$serverIp/api/indir"
        Log.i(TAG, "🚀 HDWP Gönderiliyor -> URL=$targetEndpoint | Payload=$payload")

        val request = Request.Builder()
            .url(targetEndpoint)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                val isSuccessful = response.isSuccessful
                Log.i(TAG, "📥 HDWP Yanıt: HTTP ${response.code} | Body=$bodyStr")
                
                withContext(Dispatchers.Main) {
                    if (isSuccessful) {
                        Toast.makeText(context, "Medya WhatsApp indirme kuyruğuna eklendi! 📱", Toast.LENGTH_SHORT).show()
                    } else {
                        val errMsg = try {
                            JSONObject(bodyStr).optString("message", JSONObject(bodyStr).optString("error", "Sunucu hatası: HTTP ${response.code}"))
                        } catch (e: Exception) {
                            "Sunucu Hatası: HTTP ${response.code}"
                        }
                        Toast.makeText(context, "$errMsg ($targetEndpoint)", Toast.LENGTH_LONG).show()
                    }
                }
                isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ HDWP Hatası: ${e.message}", e)
            withContext(Dispatchers.Main) {
                val errorDesc = when (e) {
                    is java.net.ConnectException -> "Bağlantı reddedildi! Sunucu çalışıyor mu? ($targetEndpoint)"
                    is java.net.SocketTimeoutException -> "Zaman aşımı! Sunucu yanıt vermedi ($targetEndpoint)"
                    is java.net.UnknownHostException -> "Sunucu adresi bulunamadı! IP kontrol edin ($targetEndpoint)"
                    else -> "Sunucuya bağlanılamadı: ${e.localizedMessage ?: e.message}"
                }
                Toast.makeText(context, errorDesc, Toast.LENGTH_LONG).show()
            }
            false
        }
    }

    suspend fun sendBulkDownloadRequest(
        context: Context,
        items: List<HdwpBulkItem>,
        seriesName: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext false

        val prefManager = UserPreferencesManager.getInstance(context)
        val rawIp = prefManager.hdwpServerIp.trim()
        val targetPhone = prefManager.hdwpTargetPhone.trim()

        val serverIp = formatServerUrl(rawIp)
        val jid = formatJid(targetPhone)

        Log.d(TAG, "sendBulkDownloadRequest: items=${items.size}, serverIp='$serverIp', jid='$jid', series='$seriesName'")

        if (targetPhone.isBlank() || jid.isBlank()) {
            Log.w(TAG, "WhatsApp numarası boş! İstek iptal edildi.")
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context, 
                    "Lütfen önce Ayarlar (⚙️) sayfasından WhatsApp telefon numaranızı girin.", 
                    Toast.LENGTH_LONG
                ).show()
            }
            return@withContext false
        }

        val jsonArray = JSONArray()
        for (item in items) {
            val itemObj = JSONObject().apply {
                put("url", item.url)
                put("title", item.title)
                put("fileName", item.fileName ?: (if (item.title.endsWith(".mp4", ignoreCase = true)) item.title else "${item.title}.mp4"))
                put("caption", item.caption ?: item.title)
            }
            jsonArray.put(itemObj)
        }

        val payload = JSONObject().apply {
            put("jid", jid)
            put("seriesName", seriesName)
            put("episodes", jsonArray)
        }.toString()

        val targetEndpoint = "$serverIp/api/indir-multiple"
        Log.i(TAG, "🚀 HDWP Bulk Gönderiliyor -> URL=$targetEndpoint | ${items.size} Bölüm")

        val request = Request.Builder()
            .url(targetEndpoint)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                val isSuccessful = response.isSuccessful
                Log.i(TAG, "📥 HDWP Bulk Yanıt: HTTP ${response.code} | Body=$bodyStr")
                
                withContext(Dispatchers.Main) {
                    if (isSuccessful) {
                        Toast.makeText(context, "${items.size} bölüm başarıyla WhatsApp indirme kuyruğuna eklendi! 📱", Toast.LENGTH_LONG).show()
                    } else {
                        val errMsg = try {
                            JSONObject(bodyStr).optString("message", JSONObject(bodyStr).optString("error", "Sunucu hatası: HTTP ${response.code}"))
                        } catch (e: Exception) {
                            "Sunucu Hatası: HTTP ${response.code}"
                        }
                        Toast.makeText(context, "$errMsg ($targetEndpoint)", Toast.LENGTH_LONG).show()
                    }
                }
                isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ HDWP Bulk Hatası: ${e.message}", e)
            withContext(Dispatchers.Main) {
                val errorDesc = when (e) {
                    is java.net.ConnectException -> "Bağlantı reddedildi! Sunucu çalışıyor mu? ($targetEndpoint)"
                    is java.net.SocketTimeoutException -> "Zaman aşımı! Sunucu yanıt vermedi ($targetEndpoint)"
                    is java.net.UnknownHostException -> "Sunucu adresi bulunamadı! IP kontrol edin ($targetEndpoint)"
                    else -> "Sunucuya bağlanılamadı: ${e.localizedMessage ?: e.message}"
                }
                Toast.makeText(context, errorDesc, Toast.LENGTH_LONG).show()
            }
            false
        }
    }
}
