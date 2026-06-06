package com.myanim.kondi.data.animecix

import timber.log.Timber
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.util.regex.Pattern
import android.net.Uri

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.delay

class AnimecixExtractors(baseClient: OkHttpClient) {
    private val client = baseClient.newBuilder()
        .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val TAG = "AnimecixExtractors"
    companion object {
        private val SIBNET_PATTERN = Pattern.compile("src:\\s*\"([^\"]+)\"")
        private val SIBNET_QUALITY_PATTERN = Pattern.compile("\\{[^}]*?\"url\"\\s*:\\s*\"([^\"]+)\"[^}]*?\"quality\"\\s*:\\s*(\\d+)[^}]*?\\}")
        private val SIBNET_SRC_PATTERN = Pattern.compile("source\\s+src=[\"']([^\"']+)[\"']")
        private val SOURCES_PATTERN = Pattern.compile("sources\":\\s*\\[([^\\]]+)\\]")
        private val FILE_PATTERN = Pattern.compile("file\":\\s*\"(http[^\"]+)\"")
        private val DOOD_MD5_PATTERN = Pattern.compile("/pass_md5/([^'\"]+)")
        
        private val tauVideoSemaphore = Semaphore(1)
        private var lastTauVideoRequestTime = 0L
        private var currentTauDelayMs = 2500L

        private val sibnetSemaphore = Semaphore(1)
        private var lastSibnetRequestTime = 0L
        private var currentSibnetDelayMs = 1500L

        private val googleDriveSemaphore = Semaphore(1)
        private var lastGoogleDriveRequestTime = 0L
        private var currentGoogleDriveDelayMs = 1500L

        private val doodstreamSemaphore = Semaphore(1)
        private var lastDoodstreamRequestTime = 0L
        private var currentDoodstreamDelayMs = 2000L
    }

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
        "X-Requested-With" to "com.kraptor.AnimeciX"
    )

    private fun Request.Builder.addCommonHeaders(referer: String? = null): Request.Builder {
        commonHeaders.forEach { (k, v) -> header(k, v) }
        referer?.let { header("Referer", it) }
        return this
    }

    suspend fun resolveSibNet(url: String): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        
        sibnetSemaphore.withPermit {
            var retryCount = 0
            val maxRetries = 3

            while (retryCount <= maxRetries) {
                val now = System.currentTimeMillis()
                val timeSinceLast = now - lastSibnetRequestTime
                if (timeSinceLast < currentSibnetDelayMs) {
                    delay(currentSibnetDelayMs - timeSinceLast)
                }
                
                lastSibnetRequestTime = System.currentTimeMillis()
                Timber.tag(TAG).d("Resolving SibNet: $url (Attempt: ${retryCount + 1})")
                
                try {
                    val request = Request.Builder()
                        .url(url)
                        .addCommonHeaders("https://animecix.net/")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            currentSibnetDelayMs = 1500L
                            val html = response.body?.string() ?: return@withContext null
                            
                            // Method 1: Try to extract from quality pattern (JSON format)
                            val qualityMatcher = SIBNET_QUALITY_PATTERN.matcher(html)
                            var bestUrl: String? = null
                            var bestQuality = 0
                            
                            while (qualityMatcher.find()) {
                                val videoUrl = qualityMatcher.group(1)
                                val quality = qualityMatcher.group(2)?.toIntOrNull() ?: 0
                                
                                if (quality > bestQuality) {
                                    bestQuality = quality
                                    bestUrl = videoUrl
                                }
                                Timber.tag(TAG).d("SibNet found quality: $quality - $videoUrl")
                            }
                            
                            if (bestUrl != null) {
                                Timber.tag(TAG).d("SibNet selected quality: $bestQuality - $bestUrl")
                                return@withContext if (bestUrl.startsWith("http")) bestUrl else "https://video.sibnet.ru$bestUrl"
                            }
                            
                            // Method 2: Try source tag pattern
                            val srcMatcher = SIBNET_SRC_PATTERN.matcher(html)
                            if (srcMatcher.find()) {
                                val srcUrl = srcMatcher.group(1) ?: return@withContext null
                                Timber.tag(TAG).d("SibNet found from source tag: $srcUrl")
                                return@withContext if (srcUrl.startsWith("http")) srcUrl else "https://video.sibnet.ru$srcUrl"
                            }

                            // Method 3: Try slug extraction if it's embed
                            if (url.contains("/embed/")) {
                                val id = url.substringAfter("/embed/").substringBefore("/")
                                val shellUrl = "https://video.sibnet.ru/shell.php?videoid=$id"
                                Timber.tag(TAG).d("SibNet trying shell URL: $shellUrl")
                                return@withContext resolveSibNet(shellUrl)
                            }
                            
                            // Method 4: Try original pattern
                            val matcher = SIBNET_PATTERN.matcher(html)
                            if (matcher.find()) {
                                val path = matcher.group(1) ?: return@withContext null
                                Timber.tag(TAG).d("SibNet found from original pattern: $path")
                                return@withContext if (path.startsWith("http")) path else "https://video.sibnet.ru$path"
                            }
                            
                            Timber.tag(TAG).w("SibNet: No video URL found in HTML")
                            return@withContext null
                        } else if (response.code == 429) {
                            Timber.tag(TAG).w("SibNet API failed: 429 - Retrying...")
                            retryCount++
                            currentSibnetDelayMs = (currentSibnetDelayMs * 1.5).toLong().coerceAtMost(10000L)
                            if (retryCount > maxRetries) {
                                Timber.tag(TAG).e("SibNet max retries reached")
                                return@withContext null
                            }
                        } else {
                            Timber.tag(TAG).w("SibNet response unsuccessful: ${response.code}")
                            return@withContext null
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "SibNet resolution failed: ${e.message}")
                    return@withContext null
                }
            }
            null
        }
    }

    suspend fun resolveGoogleDrive(url: String): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        
        googleDriveSemaphore.withPermit {
            var retryCount = 0
            val maxRetries = 3

            while (retryCount <= maxRetries) {
                val now = System.currentTimeMillis()
                val timeSinceLast = now - lastGoogleDriveRequestTime
                if (timeSinceLast < currentGoogleDriveDelayMs) {
                    delay(currentGoogleDriveDelayMs - timeSinceLast)
                }
                
                lastGoogleDriveRequestTime = System.currentTimeMillis()
                Timber.tag(TAG).d("Resolving Google Drive: $url (Attempt: ${retryCount + 1})")
                
                try {
                    // Handle gdplayer.vip API directly if possible
                    if (url.contains("gdplayer.vip")) {
                        val id = url.substringAfterLast("/").substringBefore("?")
                        val apiUrl = "https://gdplayer.vip/api/video/$id"
                        val request = Request.Builder()
                            .url(apiUrl)
                            .addCommonHeaders("https://gdplayer.vip/")
                            .build()
                        
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                currentGoogleDriveDelayMs = 1500L
                                val json = response.body?.string() ?: ""
                                val obj = JSONObject(json)
                                val videos = obj.optJSONArray("videos")
                                if (videos != null && videos.length() > 0) {
                                    return@withContext videos.getJSONObject(0).optString("url")
                                }
                            } else if (response.code == 429) {
                                Timber.tag(TAG).w("Google Drive API failed: 429 - Retrying...")
                                retryCount++
                                currentGoogleDriveDelayMs = (currentGoogleDriveDelayMs * 1.5).toLong().coerceAtMost(10000L)
                                if (retryCount > maxRetries) {
                                    return@withContext null
                                }
                                return@use // Continue to next retry
                            }
                        }
                    }

                    // Fallback to HTML parsing
                    val request = Request.Builder()
                        .url(url)
                        .addCommonHeaders("https://animecix.net/")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            currentGoogleDriveDelayMs = 1500L
                            val html = response.body?.string() ?: return@withContext null
                            
                            // 1. Try direct Google Drive fmt_stream_map pattern (for drive.google.com/file/d/.../preview URLs)
                            val fmtMatcher = java.util.regex.Pattern.compile("\"fmt_stream_map\"\\s*:\\s*\"([^\"]+)\"").matcher(html)
                            if (fmtMatcher.find()) {
                                val map = fmtMatcher.group(1) ?: ""
                                val streams = map.split(",")
                                var bestUrl: String? = null
                                var bestItag = 0
                                for (stream in streams) {
                                    val parts = stream.split("|")
                                    if (parts.size >= 2) {
                                        val itag = parts[0].toIntOrNull() ?: 0
                                        val streamUrl = parts[1]
                                            .replace("\\u0026", "&")
                                            .replace("\\/", "/")
                                        if (itag > bestItag) {
                                            bestItag = itag
                                            bestUrl = streamUrl
                                        }
                                    }
                                }
                                if (bestUrl != null) {
                                    Timber.tag(TAG).d("Google Drive resolved via fmt_stream_map (itag=$bestItag)")
                                    return@withContext bestUrl
                                }
                            }

                            // 2. Try direct Google Drive downloadUrl pattern
                            val downloadMatcher = java.util.regex.Pattern.compile("\"downloadUrl\"\\s*:\\s*\"([^\"]+)\"").matcher(html)
                            if (downloadMatcher.find()) {
                                val downloadUrl = downloadMatcher.group(1)
                                    ?.replace("\\u0026", "&")
                                    ?.replace("\\/", "/")
                                if (downloadUrl != null) {
                                    Timber.tag(TAG).d("Google Drive resolved via downloadUrl")
                                    return@withContext downloadUrl
                                }
                            }

                            // 3. Try JSON in HTML (common for gdplayer/tau)
                            val sourcesMatcher = SOURCES_PATTERN.matcher(html)
                            if (sourcesMatcher.find()) {
                                val sourcesJson = sourcesMatcher.group(1) ?: ""
                                val urlMatcher = FILE_PATTERN.matcher(sourcesJson)
                                if (urlMatcher.find()) {
                                    return@withContext urlMatcher.group(1)
                                        ?.replace("\\/", "/")
                                        ?.replace("\\u0026", "&")
                                }
                            }
                            
                            // 4. Try direct file pattern
                            val fileMatcher = FILE_PATTERN.matcher(html)
                            if (fileMatcher.find()) {
                                return@withContext fileMatcher.group(1)
                                    ?.replace("\\/", "/")
                                    ?.replace("\\u0026", "&")
                            }
                            return@withContext null
                        } else if (response.code == 429) {
                            Timber.tag(TAG).w("Google Drive failed: 429 - Retrying...")
                            retryCount++
                            currentGoogleDriveDelayMs = (currentGoogleDriveDelayMs * 1.5).toLong().coerceAtMost(10000L)
                            if (retryCount > maxRetries) {
                                return@withContext null
                            }
                        } else {
                            return@withContext null
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Google Drive resolution failed")
                    return@withContext null
                }
            }
            null
        }
    }

    suspend fun resolveTauVideo(url: String): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        
        tauVideoSemaphore.withPermit {
            var retryCount = 0
            val maxRetries = 3
            var resolvedUrl: String? = null

            while (retryCount <= maxRetries) {
                val now = System.currentTimeMillis()
                val timeSinceLast = now - lastTauVideoRequestTime
                if (timeSinceLast < currentTauDelayMs) {
                    delay(currentTauDelayMs - timeSinceLast)
                }
                
                lastTauVideoRequestTime = System.currentTimeMillis()
                Timber.tag(TAG).d("Resolving TauVideo: $url (Attempt: ${retryCount + 1})")
                
                try {
                    // Extract ID and domain from URL
                    val cleanUrl = url.trim().removeSuffix("/")
                    val uri = Uri.parse(cleanUrl)
                    val id = cleanUrl.substringAfterLast("/").substringBefore("?")
                    val domain = uri.host ?: "tau-video.xyz"
                    val apiUrl = "https://$domain/api/video/$id"
                    
                    Timber.tag(TAG).d("TauVideo API URL: $apiUrl")
                    
                    val request = Request.Builder()
                        .url(apiUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                        .header("Accept", "application/json")
                        .header("Referer", url)
                        .header("Origin", "https://$domain")
                        .header("X-Requested-With", "com.kraptor.AnimeciX")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            // On success, reset the delay to the default
                            currentTauDelayMs = 2500L
                            
                            val json = response.body?.string()?.trim() ?: ""
                            if (json.isEmpty()) {
                                throw Exception("TauVideo API returned an empty response body")
                            }
                            
                            val obj = JSONObject(json)
                            val urls = obj.optJSONArray("urls")
                            
                            if (urls != null && urls.length() > 0) {
                                // Prefer highest quality
                                var bestUrl: String? = null
                                var maxRes = 0
                                
                                for (i in 0 until urls.length()) {
                                    val u = urls.getJSONObject(i)
                                    val label = u.optString("label", "0")
                                    val res = label.filter { it.isDigit() }.toIntOrNull() ?: 0
                                    if (res >= maxRes) {
                                        maxRes = res
                                        bestUrl = u.optString("url")
                                    }
                                }
                                resolvedUrl = bestUrl ?: urls.getJSONObject(urls.length() - 1).optString("url")
                                return@withContext resolvedUrl
                            } else {
                                Timber.tag(TAG).w("TauVideo: No urls in JSON response")
                                break // Break out of API loop and try fallback
                            }
                        } else if (response.code == 429) {
                            Timber.tag(TAG).w("TauVideo API failed: 429 - Retrying...")
                            retryCount++
                            currentTauDelayMs = (currentTauDelayMs * 1.5).toLong().coerceAtMost(10000L) // Exponential backoff globally
                        } else {
                            Timber.tag(TAG).w("TauVideo API failed: ${response.code} - ${response.message}")
                            if (response.code == 404) {
                                break // Video doesn't exist, try fallback or stop
                            }
                            retryCount++
                            currentTauDelayMs = (currentTauDelayMs * 1.5).toLong().coerceAtMost(10000L)
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).w("TauVideo API attempt failed: ${e.message}. Retrying...")
                    retryCount++
                    currentTauDelayMs = (currentTauDelayMs * 1.5).toLong().coerceAtMost(10000L)
                }
            }

            // Fallback: If API attempts failed or returned empty, try fetching the embed page HTML directly
            if (resolvedUrl == null) {
                try {
                    Timber.tag(TAG).d("TauVideo API failed or empty. Trying HTML fallback for: $url")
                    val fallbackRequest = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                        .header("Referer", "https://animecix.net/")
                        .build()
                    
                    client.newCall(fallbackRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            val html = response.body?.string() ?: ""
                            
                            // 1. Try SOURCES_PATTERN inside HTML
                            val sourcesMatcher = SOURCES_PATTERN.matcher(html)
                            if (sourcesMatcher.find()) {
                                val sourcesJson = sourcesMatcher.group(1) ?: ""
                                val urlMatcher = FILE_PATTERN.matcher(sourcesJson)
                                if (urlMatcher.find()) {
                                    val fallbackUrl = urlMatcher.group(1)
                                        ?.replace("\\/", "/")
                                        ?.replace("\\u0026", "&")
                                    if (!fallbackUrl.isNullOrBlank()) {
                                        Timber.tag(TAG).d("TauVideo resolved via HTML sources fallback: $fallbackUrl")
                                        resolvedUrl = fallbackUrl
                                        return@withContext resolvedUrl
                                    }
                                }
                            }
                            
                            // 2. Try direct FILE_PATTERN in HTML
                            val fileMatcher = FILE_PATTERN.matcher(html)
                            if (fileMatcher.find()) {
                                val fallbackUrl = fileMatcher.group(1)
                                    ?.replace("\\/", "/")
                                    ?.replace("\\u0026", "&")
                                if (!fallbackUrl.isNullOrBlank()) {
                                    Timber.tag(TAG).d("TauVideo resolved via HTML file fallback: $fallbackUrl")
                                    resolvedUrl = fallbackUrl
                                    return@withContext resolvedUrl
                                }
                            }
                            
                            // 3. Try standard source src pattern
                            val srcMatcher = Pattern.compile("src=[\"'](http[^\"']+\\.(?:mp4|m3u8)[^\"']*)[\"']").matcher(html)
                            if (srcMatcher.find()) {
                                val fallbackUrl = srcMatcher.group(1)
                                if (!fallbackUrl.isNullOrBlank()) {
                                    Timber.tag(TAG).d("TauVideo resolved via HTML src fallback: $fallbackUrl")
                                    resolvedUrl = fallbackUrl
                                    return@withContext resolvedUrl
                                }
                            }
                        }
                    }
                } catch (fallbackEx: Exception) {
                    Timber.tag(TAG).e(fallbackEx, "TauVideo HTML fallback failed")
                }
            }

            resolvedUrl
        }
    }

    suspend fun resolveOkRu(url: String): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        Timber.tag(TAG).d("Resolving OkRu: $url")
        try {
             val request = Request.Builder()
                .url(url)
                .addCommonHeaders("https://animecix.net/")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: return@withContext null
                    // Look for data-options attribute in div id="app" or similar
                    // Or simpler regex for video URL
                     val matcher = Pattern.compile("hlsManifestUrl\\\\\":\\\\\"(.*?)\\\\\"").matcher(html)
                    if (matcher.find()) {
                        return@withContext matcher.group(1)?.replace("\\\\u0026", "&")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "OkRu resolution failed")
        }
        null
    }

    suspend fun resolveHeavyArchive(url: String): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        Timber.tag(TAG).d("Resolving HeavyArchive: $url")
         try {
            // Usually heavy.archive.org links are direct or can be converted
            // Expected format: https://heavy.archive.org/video/XYZ/XYZ.mp4
            if (url.endsWith(".mp4") || url.endsWith(".mkv")) {
                return@withContext url
            }
            
            val request = Request.Builder()
                .url(url)
                .addCommonHeaders("https://animecix.net/")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                     val html = response.body?.string() ?: return@withContext null
                     val matcher = Pattern.compile("source src=\"(.*?)\"").matcher(html)
                     if (matcher.find()) {
                          return@withContext matcher.group(1)
                     }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "HeavyArchive resolution failed")
        }
        null
    }

    suspend fun resolveDoodstream(url: String): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        val domain = if (url.contains("/e/")) url.substringBefore("/e/") else if (url.contains("/d/")) url.substringBefore("/d/") else url.substringBefore("/")
        
        doodstreamSemaphore.withPermit {
            var retryCount = 0
            val maxRetries = 3

            while (retryCount <= maxRetries) {
                val now = System.currentTimeMillis()
                val timeSinceLast = now - lastDoodstreamRequestTime
                if (timeSinceLast < currentDoodstreamDelayMs) {
                    delay(currentDoodstreamDelayMs - timeSinceLast)
                }
                
                lastDoodstreamRequestTime = System.currentTimeMillis()
                Timber.tag(TAG).d("Resolving Doodstream: $url (domain: $domain, Attempt: ${retryCount + 1})")
                
                try {
                    val request = Request.Builder()
                        .url(url)
                        .addCommonHeaders("https://animecix.net/")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            currentDoodstreamDelayMs = 2000L
                            val html = response.body?.string() ?: return@withContext null
                            
                            val md5Matcher = DOOD_MD5_PATTERN.matcher(html)
                            if (md5Matcher.find()) {
                                val md5Path = md5Matcher.group(1) ?: return@withContext null
                                
                                val md5Url = "$domain/pass_md5/$md5Path"
                                Timber.tag(TAG).d("Doodstream fetching MD5: $md5Url")
                                
                                val md5Request = Request.Builder()
                                    .url(md5Url)
                                    .addCommonHeaders(url)
                                    .header("Accept", "*/*")
                                    .build()
                                
                                client.newCall(md5Request).execute().use { md5Response ->
                                    if (md5Response.isSuccessful) {
                                        val baseUrl = md5Response.body?.string() ?: ""
                                        if (baseUrl.isNotEmpty()) {
                                            // Final URL structure
                                            val randomStr = (1..10).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
                                            val expiry = System.currentTimeMillis()
                                            val finalUrl = "$baseUrl$randomStr?token=$md5Path&expiry=$expiry"
                                            Timber.tag(TAG).d("Doodstream resolved: $finalUrl")
                                            return@withContext finalUrl
                                        } else {
                                            Timber.tag(TAG).w("Doodstream: Empty body for MD5 URL")
                                            return@withContext null
                                        }
                                    } else if (md5Response.code == 429) {
                                        Timber.tag(TAG).w("Doodstream MD5 fetch failed: 429 - Retrying...")
                                        // Trigger retry mechanism
                                        throw RuntimeException("429")
                                    } else {
                                        Timber.tag(TAG).w("Doodstream MD5 fetch failed: ${md5Response.code} - ${md5Response.message}")
                                        return@withContext null
                                    }
                                }
                            } else {
                                Timber.tag(TAG).w("Doodstream: MD5 pattern not found in HTML")
                                return@withContext null
                            }
                        } else if (response.code == 429) {
                            throw RuntimeException("429")
                        } else {
                            return@withContext null
                        }
                    }
                } catch (e: Exception) {
                    if (e is RuntimeException && e.message == "429") {
                        Timber.tag(TAG).w("Doodstream API failed: 429 - Retrying...")
                        retryCount++
                        currentDoodstreamDelayMs = (currentDoodstreamDelayMs * 1.5).toLong().coerceAtMost(10000L)
                        if (retryCount > maxRetries) {
                            return@withContext null
                        }
                    } else {
                        Timber.tag(TAG).e(e, "Doodstream resolution failed")
                        return@withContext null
                    }
                }
            }
            null
        }
    }

    suspend fun resolveUqload(url: String): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        Timber.tag(TAG).d("Resolving Uqload: $url")
        
        try {
            val request = Request.Builder()
                .url(url)
                .addCommonHeaders("https://animecix.net/")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val html = response.body?.string() ?: return@withContext null
                
                val matcher = FILE_PATTERN.matcher(html)
                if (matcher.find()) {
                    val direct = matcher.group(1)
                    Timber.tag(TAG).d("Uqload found via file pattern: $direct")
                    return@withContext direct
                }
                
                // Try sources: ["..."]
                val sourcesMatcher = Pattern.compile("sources:\\s*\\[\"([^\"]+)\"").matcher(html)
                if (sourcesMatcher.find()) {
                    val direct = sourcesMatcher.group(1)
                    Timber.tag(TAG).d("Uqload found via sources pattern: $direct")
                    return@withContext direct
                }
                
                Timber.tag(TAG).w("Uqload: No video URL found in HTML")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Uqload resolution failed")
        }
        null
    }

    suspend fun resolveStreamTape(url: String): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        Timber.tag(TAG).d("Resolving StreamTape: $url")
        try {
            val request = Request.Builder()
                .url(url)
                .addCommonHeaders("https://animecix.net/")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: return@withContext null
                    
                    // StreamTape obfuscation usually involves a "videolink" element
                    val matcher = Pattern.compile("document\\.getElementById\\('videolink'\\)\\.innerHTML\\s*=\\s*\"([^\"]+)\"").matcher(html)
                    if (matcher.find()) {
                        val rawUrl = matcher.group(1) ?: return@withContext null
                        return@withContext "https:$rawUrl"
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "StreamTape resolution failed")
        }
        null
    }

    suspend fun resolveVoe(url: String): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        Timber.tag(TAG).d("Resolving Voe: $url")
        try {
            val request = Request.Builder()
                .url(url)
                .addCommonHeaders("https://animecix.net/")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: return@withContext null
                    
                    // Voe often puts the HLS url in a base64 string or directly
                    val hlsMatcher = Pattern.compile("'hls':\\s*'([^']+)'").matcher(html)
                    if (hlsMatcher.find()) {
                        return@withContext hlsMatcher.group(1)
                    }
                    
                    val wcMatcher = Pattern.compile("window\\.location\\.href\\s*=\\s*'([^']+)'").matcher(html)
                    if (wcMatcher.find()) {
                         // Redirect wrapper
                         return@withContext resolveVoe(wcMatcher.group(1) ?: return@withContext null)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Voe resolution failed")
        }
        null
    }

    suspend fun resolveVidMoly(url: String): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        Timber.tag(TAG).d("Resolving VidMoly: $url")
        try {
            val request = Request.Builder()
                .url(url)
                .addCommonHeaders("https://animecix.net/")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: return@withContext null
                    
                    val matcher = FILE_PATTERN.matcher(html)
                    if (matcher.find()) {
                        return@withContext matcher.group(1)
                    }
                    
                    val sourceMatcher = Pattern.compile("sources:\\s*\\[\\{file:\"([^\"]+)\"").matcher(html)
                    if (sourceMatcher.find()) {
                        return@withContext sourceMatcher.group(1)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "VidMoly resolution failed")
        }
        null
    }

    suspend fun resolveSendVid(url: String): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        Timber.tag(TAG).d("Resolving SendVid: $url")
        try {
            val request = Request.Builder()
                .url(url)
                .addCommonHeaders("https://animecix.net/")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: return@withContext null
                    
                    val matcher = Pattern.compile("id=\"video_source\" src=\"([^\"]+)\"").matcher(html)
                    if (matcher.find()) {
                        return@withContext matcher.group(1)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "SendVid resolution failed")
        }
        null
    }

    suspend fun resolveGeneric(url: String): String? = withContext(Dispatchers.IO) {
        if (url.isBlank() || !url.startsWith("http")) return@withContext null
        Timber.tag(TAG).d("Resolving Generic: $url")
        
        try {
            val request = Request.Builder()
                .url(url)
                .addCommonHeaders("https://animecix.net/")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: return@withContext null
                    
                    // 1. Try m3u8
                    val m3u8Matcher = Pattern.compile("[\"']([^\"']+\\.m3u8[^\"']*)[\"']").matcher(html)
                    if (m3u8Matcher.find()) {
                        return@withContext m3u8Matcher.group(1)
                    }
                    
                    // 2. Try mp4
                    val mp4Matcher = Pattern.compile("[\"']([^\"']+\\.mp4[^\"']*)[\"']").matcher(html)
                    if (mp4Matcher.find()) {
                         return@withContext mp4Matcher.group(1)
                    }

                    // 3. Try <video/source src="...">
                     val srcMatcher = Pattern.compile("src=[\"'](http[^\"']+)[\"']").matcher(html)
                     if (srcMatcher.find()) {
                          val src = srcMatcher.group(1)
                          // Filter out non-video likely urls if needed, but for now strict regex helps
                          if (src != null && (src.contains(".mp4") || src.contains(".m3u8") || src.contains("blob:"))) {
                              return@withContext src
                          }
                     }
                     
                     // 4. Try JWPlayer file: "..."
                     val jwMatcher = FILE_PATTERN.matcher(html)
                     if (jwMatcher.find()) {
                          return@withContext jwMatcher.group(1)?.replace("\\/", "/")
                     }
                     
                     // 5. Try "src": "..."
                     val srcJsonMatcher = Pattern.compile("\"src\"\\s*:\\s*\"([^\"]+)\"").matcher(html)
                     if (srcJsonMatcher.find()) {
                          val src = srcJsonMatcher.group(1)?.replace("\\/", "/")
                          if (src != null && (src.contains(".mp4") || src.contains(".m3u8") || src.contains("google"))) {
                              return@withContext src
                          }
                     }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Generic resolution failed for $url")
        }
        return@withContext null
    }
}
