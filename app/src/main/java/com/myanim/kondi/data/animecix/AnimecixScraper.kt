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
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import java.nio.charset.StandardCharsets
import java.util.Base64

class AnimecixScraper {
    companion object {
        private val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        @Volatile private var baseUrl = "https://animecix.tv"
        @Volatile private var domainFetched = false

        private const val MAX_CACHE_SIZE = 100
        private const val CACHE_TTL_MS = 30 * 60 * 1000L

        /** Alan adı listesi alınamazsa kullanılan güvenli varsayılan. */
        const val FALLBACK_DOMAIN = "https://animecix.tv"

        private val detailsCache = ConcurrentHashMap<Int, CacheEntry<AnimecixAnime>>()
        private val sourcesCache = ConcurrentHashMap<Int, CacheEntry<List<AnimecixSource>>>()
        private val searchCache = ConcurrentHashMap<String, CacheEntry<List<AnimecixTitle>>>()

        private val apiSemaphore = Semaphore(1)
        @Volatile private var lastApiRequestTime = 0L

        /** Alan adı çekme işleminin eşzamanlı çağrılarda tek seferde çalışmasını sağlar. */
        private val domainMutex = kotlinx.coroutines.sync.Mutex()

        /**
         * Animecix alan adı doğrulaması.
         *
         * Alan adı listesi GİTHUB'daki harici bir depodan çekiliyor. Bu depoya erişim
         * ele geçirilirse (depo sahibi hesabı / CDN önbellek zehirlemesi) uygulama
         * tamamen saldırganın kontrolündeki bir alan adına yönlendirilir ve tüm
         * istekler (Cookie dahil) oraya gider. Bu yüzden yalnızca "animecix" kök
         * alan adını içeren, https şemalı, IP/ userinfo içermeyen adresler kabul edilir.
         */
        @JvmStatic
        fun isValidAnimecixDomain(url: String): Boolean {
            val trimmed = url.trim()
            if (!trimmed.startsWith("https://", ignoreCase = true)) return false
            val withoutScheme = trimmed.substring("https://".length)
            if (withoutScheme.isBlank()) return false
            // Path / query / fragment ve userinfo olmamalı
            val hostPart = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
            if (hostPart.isBlank()) return false
            if (hostPart.contains('@')) return false
            if (hostPart.contains(':')) return false // port/credentials
            val host = hostPart.lowercase()
            // IP adresı olmamalı
            if (Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host)) return false
            // Kayıtlı alan adı "animecix" içermeli ve TDE'den önce gelmelidir.
            // Etiket sayısı sabit değildir; kamu son eki (public suffix) bir veya iki
            // etiketten oluşabilir:
            //   animecix.tv, www.animecix.net, animecix.co.uk, animecix.com.tr
            val labels = host.split('.').filter { it.isNotEmpty() }
            if (labels.size < 2) return false
            val animecixIndex = labels.indexOf("animecix")
            if (animecixIndex < 0) return false
            val suffixLabels = labels.subList(animecixIndex + 1, labels.size)
            if (suffixLabels.isEmpty() || suffixLabels.size > 2) return false
            // Son etiket (TLD) yalnızca harflerden oluşmalı, en az 2 karakter.
            if (!suffixLabels.last().matches(Regex("^[a-z]{2,}$"))) return false
            // Ara etiketler (co, com, org, ac ...) 2-3 harf olmalı.
            // Böylece "animecix.evil.com" gibi alan adları reddedilir.
            if (suffixLabels.size == 2 && !suffixLabels.first().matches(Regex("^[a-z]{2,3}$"))) {
                return false
            }
            // Her etiket yalnızca harf/rakam/hyphen içerebilir.
            if (!labels.all { it.matches(Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")) }) return false
            return true
        }
    }

    private data class CacheEntry<T>(val value: T, val timestamp: Long) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > CACHE_TTL_MS
    }

    private fun <K, T> getFromCache(cache: ConcurrentHashMap<K, CacheEntry<T>>, key: K): T? {
        return cache[key]?.takeIf { !it.isExpired() }?.value
    }

    private fun <K, T> putToCache(cache: ConcurrentHashMap<K, CacheEntry<T>>, key: K, value: T) {
        if (cache.size >= MAX_CACHE_SIZE) {
            val oldestKey = cache.entries.minByOrNull { it.value.timestamp }?.key
            if (oldestKey != null) {
                cache.remove(oldestKey)
            }
        }
        cache[key] = CacheEntry(value, System.currentTimeMillis())
    }

    private val gson = Gson()
    private val securityToken = decodeObfuscated("N1kyb3psTytQeXNSNXc5UTZUdXBtdHZsOWpKcDdUa0ZIOFNCK0xvN052Z2pqUlNxT2dnVDI2VHNtbmxZSThXUT09LnhrbHlPQkZTNUIyS2s=")
    private val appHash = decodeObfuscated("Yjg0OWU4YTlmNmNjZWZmMjY3MjUxYTdkNzI2Y2M4ZjIyYTljMzIzYzU2YTIwM2Y1NDQ2")
    private val extractors = AnimecixExtractors(client)

    private fun decodeObfuscated(base64Encoded: String): String {
        val decoded = Base64.getDecoder().decode(base64Encoded)
        return String(decoded, StandardCharsets.UTF_8)
    }

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
        // Eşzamanlı çağrılarda alan adı yalnızca bir kez çekilsin.
        domainMutex.withLock {
            if (domainFetched) return
            withContext(Dispatchers.IO) {
                val context = try { com.myanim.kondi.KondiApplication.getContext() } catch (e: Exception) { null }
                val sharedPrefs = context?.getSharedPreferences("kondi_prefs", android.content.Context.MODE_PRIVATE)

                val cachedUrl = sharedPrefs?.getString("animecix_cached_domain", null)
                if (cachedUrl != null && isValidAnimecixDomain(cachedUrl)) {
                    baseUrl = cachedUrl.trim()
                    domainFetched = true
                    Timber.d("Loaded cached Animecix domain from SharedPreferences: $baseUrl")
                } else if (cachedUrl != null) {
                    Timber.w("Cached Animecix domain rejected by validation, refetching: $cachedUrl")
                    sharedPrefs.edit().remove("animecix_cached_domain").apply()
                }
                val urls = listOf(
                    "https://raw.githubusercontent.com/Kraptor123/domainListesi/refs/heads/main/eklenti_domainleri.txt",
                    "https://cdn.jsdelivr.net/gh/Kraptor123/domainListesi@main/eklenti_domainleri.txt"
                )
                var success = false

                for (url in urls) {
                    if (success) break
                    try {
                        val request = Request.Builder().url(url).build()
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val content = response.body?.string() ?: ""
                                content.lines().forEach { line ->
                                    if (line.startsWith("|Animecix:")) {
                                        val fetchedUrl = line.substringAfter("|Animecix:").trim()
                                        // Yalnızca doğrulanmış alan adı kabul edilir.
                                        if (fetchedUrl.isNotEmpty() && isValidAnimecixDomain(fetchedUrl)) {
                                            baseUrl = fetchedUrl
                                            domainFetched = true
                                            sharedPrefs?.edit()?.putString("animecix_cached_domain", fetchedUrl)?.apply()
                                            Timber.d("Successfully fetched and cached Animecix domain from $url: $baseUrl")
                                            success = true
                                        } else if (fetchedUrl.isNotEmpty()) {
                                            Timber.w("Rejected Animecix domain (failed validation): $fetchedUrl")
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to fetch Animecix domain from $url")
                    }
                }
                if (!domainFetched) {
                    baseUrl = FALLBACK_DOMAIN
                    domainFetched = true
                    Timber.d("Fallback to default domain: $baseUrl")
                }
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
            "X-App-Hash" to appHash
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

        val json = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("HTTP error: ${response.code}")
            response.body?.string() ?: throw java.io.IOException("Empty body")
        }
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

        val json = try {
            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    Timber.w("Category items not found: 404")
                    return@withContext emptyList()
                }
                if (!response.isSuccessful) {
                    throw java.io.IOException("HTTP error: ${response.code}")
                }
                response.body?.string() ?: throw java.io.IOException("Empty body")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching category items")
            throw e
        }
        return@withContext parseList<AnimecixTitle>(json, object : TypeToken<List<AnimecixTitle>>() {}.type)
    }

    suspend fun search(query: String, page: Int = 1): List<AnimecixTitle> = withContext(Dispatchers.IO) {
        val cacheKey = "search-$query-$page"
        getFromCache(searchCache, cacheKey)?.let { return@withContext it }

        ensureDomain()
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")

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
                    Timber.e(e, "Search error for $url")
                }
            }
            emptyList<AnimecixTitle>()
        }

        if (finalResults.isNotEmpty()) {
            putToCache(searchCache, cacheKey, finalResults)
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

                val arrayCandidates = listOf(
                    responseObj.optJSONArray("videos"),
                    responseObj.optJSONArray("data"),
                    responseObj.optJSONArray("results"),
                    responseObj.optJSONArray("titles"),
                    responseObj.optJSONObject("pagination")?.optJSONArray("videos"),
                    responseObj.optJSONObject("pagination")?.optJSONArray("data"),
                    responseObj.optJSONObject("outputs")?.optJSONArray("videos"),
                    responseObj.optJSONObject("outputs")?.optJSONArray("data"),
                    responseObj.optJSONObject("data")?.optJSONArray("data"),
                    responseObj.optJSONObject("data")?.optJSONArray("videos")
                )

                for (arr in arrayCandidates) {
                    if (arr != null && arr.length() > 0) {
                        return gson.fromJson(arr.toString(), type)
                    }
                }

                for (key in responseObj.keys()) {
                    val valObj = responseObj.opt(key)
                    if (valObj is JSONArray && valObj.length() > 0) {
                        return gson.fromJson(valObj.toString(), type)
                    } else if (valObj is JSONObject) {
                        for (subKey in valObj.keys()) {
                            val subVal = valObj.opt(subKey)
                            if (subVal is JSONArray && subVal.length() > 0) {
                                return gson.fromJson(subVal.toString(), type)
                            }
                        }
                    }
                }

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
            throw e
        }
    }

    suspend fun getAnimeDetails(id: Int): AnimecixAnime? = withContext(Dispatchers.IO) {
        getFromCache(detailsCache, id)?.let { return@withContext it }
        ensureDomain()
        val request = Request.Builder().url("$baseUrl/secure/titles/$id").addAppHeaders().build()
        val json = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string() ?: return@withContext null
            }
        } catch (e: Exception) {
            return@withContext null
        }

        try {
            val responseObj = JSONObject(json)
            val titleObj = responseObj.optJSONObject("title") ?: return@withContext null
            val anime = gson.fromJson(titleObj.toString(), AnimecixAnime::class.java)

            val episodesOnly = anime.videos?.filter { it.category != "trailer" && it.episodeNumber != null }

            if (episodesOnly.isNullOrEmpty() && anime.seasonCount > 0) {

                val allEpisodes = ConcurrentHashMap.newKeySet<AnimecixVideo>()

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
                                    }
                                }
                            }.awaitAll()
                        }
                    }
                }

                if (allEpisodes.isNotEmpty()) {
                    val grouped = allEpisodes.filter { it.episodeNumber != null }
                        .groupBy { (it.seasonNumber ?: 1) to (it.episodeNumber ?: 0) }

                    val uniqueEpisodes = grouped.map { (key, sources) ->
                        val first = sources.first()
                        first.copy(
                            name = "Bölüm ${key.second}",
                            videos = sources.map { AnimecixVideoDetail(it.url, it.episodeId, it.name) }
                        )
                    }.sortedWith(compareBy({ it.seasonNumber ?: 1 }, { it.episodeNumber ?: 0 }))

                    val trailers = (anime.videos ?: emptyList()).filter { it.category == "trailer" || it.episodeNumber == null }
                    val finalAnime = anime.copy(videos = trailers + uniqueEpisodes)
                    putToCache(detailsCache, id, finalAnime)
                    return@withContext finalAnime
                }
            }

            putToCache(detailsCache, id, anime)
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
            "$baseUrl/secure/episode-videos?titleId=$titleId&season=$season&episode=$episode"
        } else {
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
                    response.use { resp ->
                        if (resp.isSuccessful) {
                            val json = resp.body?.string() ?: return@withContext emptyList()

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
                        } else if (resp.code == 429) {
                            Timber.w("getVideoSources API failed: 429 - Retrying...")
                        } else {
                            Timber.w("getVideoSources API failed: ${resp.code} - Retrying...")
                            if (resp.code == 404) {
                                return@withContext emptyList()
                            }
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
