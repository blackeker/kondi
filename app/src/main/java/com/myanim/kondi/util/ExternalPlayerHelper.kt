package com.myanim.kondi.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.myanim.kondi.data.animecix.AnimecixScraper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ExternalPlayerHelper {
    fun launchPlayer(context: Context, url: String, title: String, source: String = "GENERAL", scope: CoroutineScope = CoroutineScope(Dispatchers.Main)) {
        scope.launch {
            val finalUrl = if (source != "LOCAL" && source == "ANIMECIX" && !url.startsWith("http") && !url.startsWith("content://") && !url.startsWith("file://")) {
                withContext(Dispatchers.IO) {
                    try {
                        AnimecixScraper().resolveSource(url)
                    } catch (e: Exception) {
                        url
                    }
                }
            } else {
                url
            }

            if (finalUrl.isNullOrBlank()) {
                Toast.makeText(context, "Kaynak çözümlenemedi veya geçersiz.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(finalUrl), "video/*")
                    putExtra("title", title)
                    // Common player extras
                    putExtra("forcename", title)
                    
                    // Add headers for players that support them (MX, VLC, etc.)
                    val headers = arrayOf("Referer", "https://animecix.net/", "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                    putExtra("headers", headers)
                    putExtra("http-user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                    
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                val chooser = Intent.createChooser(intent, "Video Oynatıcı Seçin")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } catch (e: Exception) {
                Toast.makeText(context, "Oynatıcı başlatılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
