package com.myanim.kondi.data.download

import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.yausername.youtubedl_android.mapper.VideoInfo
import com.myanim.kondi.data.local.DownloadStatus

class YtDlpDownloader {

    companion object {
        private const val TAG = "YtDlpDownloader"
    }

    /**
     * Extracts video info using yt-dlp to find the best available format
     * and streams to download.
     */
    suspend fun getInfo(url: String): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url)
            // Fetch info without downloading
            request.addOption("--dump-json")
            val response = YoutubeDL.getInstance().execute(request, null, null)
            val info = YoutubeDL.getInstance().getInfo(url)
            return@withContext info
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get info for $url: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Executes the download process via yt-dlp.
     * This is useful for complex sites where our custom Chunk Downloader might fail.
     * It handles DASH/M3U8 aggregation and FFmpeg merging automatically.
     */
    suspend fun downloadVideo(
        id: String,
        url: String,
        outputFile: File,
        headers: Map<String, String> = emptyMap(),
        format: String = "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
        progressCallback: (Float, Long, Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            outputFile.parentFile?.mkdirs()
            
            val request = YoutubeDLRequest(url)
            request.addOption("-f", format)
            request.addOption("-o", outputFile.absolutePath)
            
            // Allow resuming
            request.addOption("--continue")
            
            // Suppress warnings to keep logs clean
            request.addOption("--no-warnings")
            
            // Add custom headers to prevent 403 errors
            headers.forEach { (key, value) ->
                request.addOption("--add-header", "$key:$value")
            }

            Log.d(TAG, "Starting yt-dlp download: $url -> ${outputFile.name}")
            
            var parsedTotalBytes = -1L
            val sizeRegex = Regex("""of\s+~?(\d+(?:\.\d+)?)\s*(KiB|MiB|GiB|TiB|B|KB|MB|GB|TB)""", RegexOption.IGNORE_CASE)

            val response = YoutubeDL.getInstance().execute(request, id) { progress: Float, etaInSeconds: Long, line: String ->
                val pct = progress.coerceIn(0f, 100f)
                
                // Parse file size from stdout line if possible
                val match = sizeRegex.find(line)
                if (match != null) {
                    val sizeVal = match.groupValues[1]
                    val unitVal = match.groupValues[2]
                    val bytes = parseSizeToBytes(sizeVal, unitVal)
                    if (bytes > 0) {
                        parsedTotalBytes = bytes
                    }
                }
                
                val total = if (parsedTotalBytes > 0) parsedTotalBytes else 100L
                val downloaded = if (parsedTotalBytes > 0) ((pct * parsedTotalBytes) / 100f).toLong() else pct.toLong()
                
                progressCallback(pct, downloaded, total)
            }

            Log.d(TAG, "yt-dlp download completed with code ${response.exitCode}")
            return@withContext response.exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "yt-dlp download failed: ${e.message}", e)
            if (e.message?.contains("canceled", ignoreCase = true) == true) {
                // Return gracefully if cancelled
                return@withContext false
            }
            throw e
        }
    }

    private fun parseSizeToBytes(sizeStr: String, unitStr: String): Long {
        val value = sizeStr.toDoubleOrNull() ?: return 0L
        val multiplier = when (unitStr.uppercase()) {
            "KIB", "KB" -> 1024L
            "MIB", "MB" -> 1024L * 1024L
            "GIB", "GB" -> 1024L * 1024L * 1024L
            "TIB", "TB" -> 1024L * 1024L * 1024L * 1024L
            else -> 1L
        }
        return (value * multiplier).toLong()
    }
}
