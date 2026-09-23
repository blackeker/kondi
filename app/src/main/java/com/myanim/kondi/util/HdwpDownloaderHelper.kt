package com.myanim.kondi.util

import android.content.Context
import android.widget.Toast
import com.myanim.kondi.data.prefs.UserPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber

import java.util.concurrent.TimeUnit

object HdwpDownloaderHelper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
        
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun sendDownloadRequest(
        context: Context, 
        videoUrl: String,
        title: String? = null
    ) = withContext(Dispatchers.IO) {
        val prefManager = UserPreferencesManager.getInstance(context)
        val rawIp = prefManager.hdwpServerIp.trim().removeSuffix("/")
        val targetPhone = prefManager.hdwpTargetPhone.trim()

        if (rawIp.isBlank() || targetPhone.isBlank()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context, 
                    "Lütfen önce Ayarlar (⚙️) sayfasından VDS Sunucu IP ve WhatsApp telefon numaranızı girin.", 
                    Toast.LENGTH_LONG
                ).show()
            }
            return@withContext
        }

        // Auto prepend http:// if missing
        val serverIp = if (!rawIp.startsWith("http://") && !rawIp.startsWith("https://")) {
            "http://$rawIp"
        } else {
            rawIp
        }

        val jid = if (targetPhone.contains("@")) targetPhone else "$targetPhone@s.whatsapp.net"
        val payload = JSONObject().apply {
            put("url", videoUrl)
            put("jid", jid)
            if (!title.isNullOrBlank()) {
                put("title", title)
                put("fileName", if (title.endsWith(".mp4")) title else "$title.mp4")
                put("filename", if (title.endsWith(".mp4")) title else "$title.mp4")
                put("caption", title)
            }
        }.toString()

        val targetEndpoint = "$serverIp/api/indir"
        Timber.d("HDWP Request: URL=$targetEndpoint, Payload=$payload")

        val request = Request.Builder()
            .url(targetEndpoint)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                val isSuccessful = response.isSuccessful
                
                withContext(Dispatchers.Main) {
                    if (isSuccessful) {
                        Toast.makeText(context, "Medya başarıyla WhatsApp indirme kuyruğuna eklendi! 📱", Toast.LENGTH_LONG).show()
                    } else {
                        val errMsg = try {
                            JSONObject(bodyStr).optString("message", "Sunucu hatası: HTTP ${response.code}")
                        } catch (e: Exception) {
                            "Sunucu Hatası: HTTP ${response.code}"
                        }
                        Toast.makeText(context, "$errMsg ($targetEndpoint)", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error sending download request to HDWP Downloader at $targetEndpoint")
            withContext(Dispatchers.Main) {
                val errorDesc = when (e) {
                    is java.net.ConnectException -> "Bağlantı reddedildi! VDS sunucuda servis çalışıyor mu veya port açık mı? ($targetEndpoint)"
                    is java.net.SocketTimeoutException -> "Zaman aşımı! VDS sunucu yanıt vermedi ($targetEndpoint)"
                    is java.net.UnknownHostException -> "Sunucu adresi bulunamadı! IP/Domain kontrol edin ($targetEndpoint)"
                    else -> "Sunucuya bağlanılamadı: ${e.localizedMessage ?: e.message}"
                }
                Toast.makeText(context, errorDesc, Toast.LENGTH_LONG).show()
            }
        }
    }
}
