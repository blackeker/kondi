package com.myanim.kondi.data.animecix

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.async
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.awaitAll
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.delay

class AnimecixScraper {
    companion object {
        private val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
            
        private var baseUrl = "https://animecix.tv"
        private var domainFetched = false
        
        // In-memory caches to persist across screens
        private val detailsCache = ConcurrentHashMap<Int, AnimecixAnime>()
        private val sourcesCache = ConcurrentHashMap<Int, List<AnimecixSource>>()
        private val searchCache = ConcurrentHashMap<String, List<AnimecixTitle>>()
        
        private val apiSemaphore = Semaphore(1)
        private var lastApiRequestTime = 0L
    }

    private val gson = Gson()
    private val securityToken = "7Y2ozlO+QysR5w9Q6Tupmtvl9jJp7ThFH8SB+Lo7NvZjgjqRSqOgcT2v4ISM9sP10LmnlYI8WQ==.xrlyOBFS5BHjQ2Lk"
    private val appHash = "b849e8a9f6cceff267251a73644faacc801ad726cc8f22a9c323c56a203f5446"
    private val extractors = AnimecixExtractors(client)

    val categories = listOf(
        "Son Bölümler" to "/secure/last-episodes",
        "Animeler" to "/secure/titles?type=series&onlyStreamable=true",
        "Filmler" to "/secure/titles?type=movie&onlyStreamable=true",
        "Aksiyon" to "/secure/titles?genre=action&onlyStreamable=true",
        "Askeri" to "/secure/titles?keyword=military&onlyStreamable=true",
        "Büyü" to "/secure/titles?keyword=magic&onlyStreamable=true",
        "Dram" to "/secure/titles?genre=drama&onlyStreamable=true",
        "Spor" to "/secure/titles?keyword=sport&onlyStreamable=true",
        "Gerilim" to "/secure/titles?genre=thriller&onlyStreamable=true",
        "Gizem" to "/secure/titles?genre=mystery&onlyStreamable=true",
        "Komedi" to "/secure/titles?genre=comedy&onlyStreamable=true",
        "Okul" to "/secure/titles?keyword=school&onlyStreamable=true",
        "Isekai" to "/secure/titles?keyword=isekai&onlyStreamable=true",
        "Shounen" to "/secure/titles?keyword=shounen&onlyStreamable=true",
        "Shoujo" to "/secure/titles?keyword=shoujo&onlyStreamable=true",
        "Seinen" to "/secure/titles?keyword=seinen&onlyStreamable=true",
        "Romantizm" to "/secure/titles?genre=romance&onlyStreamable=true",
        "Harem" to "/secure/titles?keyword=harem&onlyStreamable=true",
        "Ecchi" to "/secure/titles?keyword=ecchi&onlyStreamable=true",
        "Macera" to "/secure/titles?genre=adventure&onlyStreamable=true",
        "Fantezi" to "/secure/titles?genre=fantasy&onlyStreamable=true",
        "Korku" to "/secure/titles?genre=horror&onlyStreamable=true",
        "Bilim Kurgu" to "/secure/titles?genre=sci-fi&onlyStreamable=true",
        "Doğaüstü" to "/secure/titles?keyword=supernatural&onlyStreamable=true",
        "Psikolojik" to "/secure/titles?keyword=psychological&onlyStreamable=true",
        "Yaşamdan Kesitler" to "/secure/titles?keyword=slice-of-life&onlyStreamable=true",
        "Tarihi" to "/secure/titles?keyword=historical&onlyStreamable=true",
        "Mecha" to "/secure/titles?keyword=mecha&onlyStreamable=true",
        "Vampir" to "/secure/titles?keyword=vampire&onlyStreamable=true",
        "Samuray" to "/secure/titles?keyword=samurai&onlyStreamable=true"
    )

    private suspend fun ensureDomain() {
        if (domainFetched) return
        withContext(Dispatchers.IO) {
            try {
                // Try loading from SharedPreferences cache first
                val context = try { com.myanim.kondi.KondiApplication.getContext() } catch (e: Exception) { null }
                val sharedPrefs = context?.getSharedPreferences("kondi_prefs", android.content.Context.MODE_PRIVATE)
                
                val cachedUrl = sharedPrefs?.getString("animecix_cached_domain", null)
                if (cachedUrl != null && !domainFetched) {
                    baseUrl = cachedUrl
                    domainFetched = true
                    Timber.d("Loaded cached Animecix domain from SharedPreferences: $baseUrl")
                }

                val request = Request.Builder()
                    .url("https://raw.githubusercontent.com/Kraptor123/domainListesi/refs/heads/main/eklenti_domainleri.txt")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val content = response.body?.string() ?: ""
                        content.lines().forEach { line ->
                            if (line.startsWith("|Animecix:")) {
                                val fetchedUrl = line.substringAfter("|Animecix:").trim()
                                if (fetchedUrl.isNotEmpty()) {
                                    baseUrl = fetchedUrl
                                    domainFetched = true
                                    sharedPrefs?.edit()?.putString("animecix_cached_domain", fetchedUrl)?.apply()
                                    Timber.d("Successfully fetched and cached new Animecix domain: $baseUrl")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error ensuring domain")
            }
        }
    }

    private fun getHeaders(referer: String = baseUrl): Map<String, String> {
        return mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36",
            "Referer" to referer,
            "X-Requested-With" to "com.kraptor.AnimeciX",
            "X-App-Version" to "1.0.5",
            "x-e-h" to securityToken,
            "X-App-Hash" to appHash // Added based on Cloudstream findings
        )
    }

    private fun Request.Builder.addAppHeaders(referer: String = baseUrl): Request.Builder {
        getHeaders(referer).forEach { (k, v) -> header(k, v) }
        return this
    }

    suspend fun getLatestEpisodes(page: Int = 1): List<AnimecixVideo> = withContext(Dispatchers.IO) {
        ensureDomain()
        val url = "$baseUrl/secure/last-episodes?page=$page"
        val request = Request.Builder()
            .url(url)
            .addAppHeaders()
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            Timber.e(e, "Error fetching latest episodes")
            return@withContext emptyList()
        }
        
        if (!response.isSuccessful) {
            return@withContext emptyList()
        }
        
        val json = response.body?.string() ?: return@withContext emptyList()
        return@withContext parseList<AnimecixVideo>(json, object : TypeToken<List<AnimecixVideo>>() {}.type)
    }

    suspend fun getCategoryItems(categoryPath: String, page: Int = 1): List<AnimecixTitle> = withContext(Dispatchers.IO) {
        ensureDomain()
        val url = if (categoryPath.contains("?")) {
            "$baseUrl$categoryPath&page=$page"
        } else {
            "$baseUrl$categoryPath?page=$page"
        }
        
        val request = Request.Builder()
            .url(url)
            .addAppHeaders()
            .build()
            
        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            Timber.e(e, "Error fetching category items")
            return@withContext emptyList()
        }
        
        if (!response.isSuccessful) {
            return@withContext emptyList()
        }
        
        val json = response.body?.string() ?: return@withContext emptyList()
        return@withContext parseList<AnimecixTitle>(json, object : TypeToken<List<AnimecixTitle>>() {}.type)
    }

    suspend fun search(query: String, page: Int = 1): List<AnimecixTitle> = withContext(Dispatchers.IO) {
        val cacheKey = "search-$query-$page"
        searchCache[cacheKey]?.let { return@withContext it }
        
        ensureDomain()
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        
        // Try correct endpoints: site uses 'query' param at /search or /secure/search
        val urls = listOf(
            "$baseUrl/secure/search?query=$encodedQuery&page=$page",
            "$baseUrl/secure/titles?query=$encodedQuery&onlyStreamable=true&page=$page",
            "$baseUrl/secure/titles?keyword=$encodedQuery&onlyStreamable=true&page=$page"
        )
        Timber.d("Searching with URLs: $urls")
        
        val finalResults = run {
            for (url in urls) {
                val request = Request.Builder().url(url).addAppHeaders().build()
                try {
                    val results = client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val json = response.body?.string() ?: ""
                            // Log raw response structure for debugging
                            try {
                                val obj = org.json.JSONObject(json.trim())
                                Timber.d("[Search] URL: $url | TopKeys: ${obj.keys().asSequence().toList()} | Preview: ${json.take(500)}")
                            } catch (_: Exception) {
                                Timber.d("[Search] URL: $url | RawPreview: ${json.take(500)}")
                            }
                            parseList<AnimecixTitle>(json, object : TypeToken<List<AnimecixTitle>>() {}.type)
                        } else emptyList()
                    }
                    if (results.isNotEmpty()) return@run results
                } catch (e: Exception) {
                    android.util.Log.e("AnimecixScraper", "Search error for $url", e)
                }
            }
            emptyList<AnimecixTitle>()
        }
        
        if (finalResults.isNotEmpty()) {
            searchCache[cacheKey] = finalResults
            Timber.d("Search results for '$query' (${finalResults.size} items):")
            finalResults.forEachIndexed { i, t -> Timber.d("  [$i] id=${t.id} name=${t.name}") }
        } else {
            Timber.w("No results found for '$query'")
        }
        finalResults
    }

    private fun <T> parseList(json: String, type: java.lang.reflect.Type): List<T> {
        return try {
            val jsonTrimmed = json.trim()
            if (jsonTrimmed.startsWith("{")) {
                val responseObj = JSONObject(jsonTrimmed)
                
                // 1. Try to find "data" array or object
                val dataVal = responseObj.opt("data")
                if (dataVal is JSONArray) {
                    return gson.fromJson(dataVal.toString(), type)
                } else if (dataVal is JSONObject) {
                    // Check if it's a paginated object with nested "data" array
                    val nestedData = dataVal.optJSONArray("data")
                    if (nestedData != null) {
                        return gson.fromJson(nestedData.toString(), type)
                    }
                }

                // 1b. Check "pagination" object specifically
                val paginationVal = responseObj.optJSONObject("pagination")
                if (paginationVal != null) {
                    val pData = paginationVal.optJSONArray("data")
                    if (pData != null) {
                        return gson.fromJson(pData.toString(), type)
                    }
                }

                // 1c. Search endpoint returns "results" array (örnek/Search.java: @JsonProperty("results"))
                val resultsArray = responseObj.optJSONArray("results")
                if (resultsArray != null) {
                    return gson.fromJson(resultsArray.toString(), type)
                }

                // 1d. Some endpoints return "titles" array
                val titlesArray = responseObj.optJSONArray("titles")
                if (titlesArray != null) {
                    return gson.fromJson(titlesArray.toString(), type)
                }
                
                // 2. Greedy fallback DISABLED - it picks up wrong arrays like 'featured'
                // Log all keys to help diagnose
                val allKeys = responseObj.keys().asSequence().toList()
                Timber.w("parseList: No known array key found. All keys: $allKeys")

                if (responseObj.has("title")) {
                    val titleObj = responseObj.optJSONObject("title")
                    if (titleObj != null) {
                        val array = JSONArray()
                        array.put(titleObj)
                        return gson.fromJson(array.toString(), type)
                    }
                }

                emptyList()
            } else if (jsonTrimmed.startsWith("[")) {
                gson.fromJson(jsonTrimmed, type)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing list: ${e.message}")
            emptyList()
        }
    }
    
    suspend fun getAnimeDetails(id: Int): AnimecixAnime? = withContext(Dispatchers.IO) {
        detailsCache[id]?.let { return@withContext it }
        ensureDomain()
        val request = Request.Builder().url("$baseUrl/secure/titles/$id").addAppHeaders().build()
            
        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) { null }
        
        if (response?.isSuccessful != true) return@withContext null
        val json = response.body?.string() ?: return@withContext null
        
        try {
            val responseObj = JSONObject(json)
            val titleObj = responseObj.optJSONObject("title") ?: return@withContext null
            val anime = gson.fromJson(titleObj.toString(), AnimecixAnime::class.java)
            
            // If episodes list is empty and it's a series, fetch separately
            // Filters out trailers (videos with category "trailer" or null episode_num)
            val episodesOnly = anime.videos?.filter { it.category != "trailer" && it.episodeNumber != null }
            
            if (episodesOnly.isNullOrEmpty() && anime.seasonCount > 0) {
                
                val allEpisodes = ConcurrentHashMap.newKeySet<AnimecixVideo>()
                
                // Fetch first page to get pagination info
                val firstPageUrl = "$baseUrl/secure/videos?titleId=$id&page=1"
                val firstPageRequest = Request.Builder().url(firstPageUrl).addAppHeaders().build()
                
                var lastPage = 1
                client.newCall(firstPageRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = response.body?.string() ?: ""
                        val responseObj = JSONObject(json)
                        val pagination = responseObj.optJSONObject("pagination")
                        lastPage = pagination?.optInt("last_page") ?: 1
                        val firstPageEpisodes = parseList<AnimecixVideo>(json, object : TypeToken<List<AnimecixVideo>>() {}.type)
                        allEpisodes.addAll(firstPageEpisodes)
                    }
                }

                if (lastPage > 1) {
                    (2..lastPage).chunked(5).forEach { pageChunk ->
                        supervisorScope {
                            pageChunk.map { page ->
                                async(Dispatchers.IO) {
                                    val url = "$baseUrl/secure/videos?titleId=$id&page=$page"
                                    val request = Request.Builder().url(url).addAppHeaders().build()
                                    try {
                                        client.newCall(request).execute().use { response ->
                                            if (response.isSuccessful) {
                                                val json = response.body?.string() ?: ""
                                                val pageEpisodes = parseList<AnimecixVideo>(json, object : TypeToken<List<AnimecixVideo>>() {}.type)
                                                allEpisodes.addAll(pageEpisodes)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Timber.e(e, "Error fetching episodes page")
                                        // Ignore single page error
                                    }
                                }
                            }.awaitAll()
                        }
                    }
                }

                if (allEpisodes.isNotEmpty()) {
                    // Group flattened sources by (Season, Episode)
                    val grouped = allEpisodes.filter { it.episodeNumber != null }
                        .groupBy { (it.seasonNumber ?: 1) to (it.episodeNumber ?: 0) }
                    
                    val uniqueEpisodes = grouped.map { (key, sources) ->
                        val first = sources.first()
                        // Create a composite AnimecixVideo with nested sources
                        first.copy(
                            name = "Bölüm ${key.second}",
                            videos = sources.map { AnimecixVideoDetail(it.url, it.episodeId, it.name) }
                        )
                    }.sortedWith(compareBy({ it.seasonNumber ?: 1 }, { it.episodeNumber ?: 0 }))

                    // Merge with existing trailers if any
                    val trailers = (anime.videos ?: emptyList()).filter { it.category == "trailer" || it.episodeNumber == null }
                    val finalAnime = anime.copy(videos = trailers + uniqueEpisodes)
                    detailsCache[id] = finalAnime
                    return@withContext finalAnime
                }
            }
            
            detailsCache[id] = anime
            return@withContext anime
        } catch (e: Exception) {
            return@withContext null
        }
    }

    suspend fun getVideoSources(
        episodeId: Int,
        titleId: Int? = null,
        season: Int? = null,
        episode: Int? = null
    ): List<AnimecixSource> = withContext(Dispatchers.IO) {
        ensureDomain()
        
        val url = if (titleId != null && season != null && episode != null) {
            // New structure: /secure/episode-videos?titleId={id}&season={s}&episode={e}
            "$baseUrl/secure/episode-videos?titleId=$titleId&season=$season&episode=$episode"
        } else {
            // Fallback to old structure
            "$baseUrl/secure/videos/$episodeId"
        }
        
        apiSemaphore.withPermit {
            var retryCount = 0
            val maxRetries = 3
            var currentDelayMs = 2000L
            
            while (retryCount <= maxRetries) {
                val now = System.currentTimeMillis()
                val timeSinceLast = now - lastApiRequestTime
                if (timeSinceLast < 2500L) {
                    delay(2500L - timeSinceLast)
                }
                lastApiRequestTime = System.currentTimeMillis()
 
                val request = Request.Builder()
                    .url(url)
                    .addAppHeaders()
                    .build()
 
                val response = try {
                    client.newCall(request).execute()
                } catch (e: Exception) {
                    Timber.e(e, "Error getVideoSources network request")
                    null
                }
                
                if (response != null) {
                    if (response.isSuccessful) {
                        val json = response.body?.string() ?: return@withContext emptyList()
                        
                        try {
                            val jsonTrimmed = json.trim()
                            if (jsonTrimmed.startsWith("[")) {
                                return@withContext gson.fromJson(jsonTrimmed, object : TypeToken<List<AnimecixSource>>() {}.type)
                            } else if (jsonTrimmed.startsWith("{")) {
                                val responseObj = JSONObject(jsonTrimmed)
                                
                                val dataArray = responseObj.optJSONArray("data")
                                if (dataArray != null) {
                                    return@withContext gson.fromJson(dataArray.toString(), object : TypeToken<List<AnimecixSource>>() {}.type)
                                }
 
                                val videosArray = responseObj.optJSONArray("videos")
                                if (videosArray != null) {
                                    return@withContext gson.fromJson(videosArray.toString(), object : TypeToken<List<AnimecixSource>>() {}.type)
                                }
                                
                                val videoObj = responseObj.optJSONObject("video")
                                if (videoObj != null) {
                                    val actualVideosArray = videoObj.optJSONArray("videos")
                                    if (actualVideosArray != null) {
                                        return@withContext gson.fromJson(actualVideosArray.toString(), object : TypeToken<List<AnimecixSource>>() {}.type)
                                    } else if (videoObj.has("url")) {
                                        val source = gson.fromJson(videoObj.toString(), AnimecixSource::class.java)
                                        return@withContext listOf(source)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error parsing video sources")
                        }
                        
                        return@withContext emptyList()
                    } else if (response.code == 429) {
                        Timber.w("getVideoSources API failed: 429 - Retrying...")
                    } else {
                        Timber.w("getVideoSources API failed: ${response.code} - Retrying...")
                        if (response.code == 404) {
                            return@withContext emptyList()
                        }
                    }
                }
                
                retryCount++
                if (retryCount <= maxRetries) {
                    delay(currentDelayMs)
                    currentDelayMs = (currentDelayMs * 1.5).toLong().coerceAtMost(5000L)
                }
            }
            throw java.lang.Exception("Sunucu bağlantısı zaman aşımına uğradı veya rate limit'e takıldı. Lütfen daha sonra tekrar deneyin.")
        }
    }

    suspend fun resolveSource(url: String): String? {
        return when {
            url.contains("sibnet") -> extractors.resolveSibNet(url)
            url.contains("drive.google.com") || url.contains("gdrive") || url.contains("gdplayer") -> extractors.resolveGoogleDrive(url)
            url.contains("tau-video.xyz") || url.contains("tau") || url.contains("yoroichi") -> extractors.resolveTauVideo(url)
            url.contains("dood") -> extractors.resolveDoodstream(url)
            url.contains("uqload") -> extractors.resolveUqload(url)
            url.contains("ok.ru") || url.contains("odnoklassniki") -> extractors.resolveOkRu(url)
            url.contains("heavy.archive.org") || url.contains("archive.org") -> extractors.resolveHeavyArchive(url)
            url.contains("streamtape") -> extractors.resolveStreamTape(url)
            url.contains("voe.sx") || url.contains("voe") -> extractors.resolveVoe(url)
            url.contains("vidmoly") -> extractors.resolveVidMoly(url)
            url.contains("sendvid") -> extractors.resolveSendVid(url)
            else -> extractors.resolveGeneric(url)
        }
    }
}

