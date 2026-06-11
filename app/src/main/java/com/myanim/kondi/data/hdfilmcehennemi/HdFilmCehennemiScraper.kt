package com.myanim.kondi.data.hdfilmcehennemi

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.FormBody
import okhttp3.MultipartBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.math.pow

class HdFilmCehennemiScraper {
    companion object {
        private val client = com.myanim.kondi.util.NetworkUtils.getUnsafeOkHttpClientBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        @Volatile private var baseUrl = "https://www.hdfilmcehennemi.nl"
        @Volatile private var domainFetched = false
    }

    private val gson = Gson()

    val categories = listOf(
        "Trend Filmler" to "mostLiked",
        "Son Eklenenler" to "home",
        "Popüler Filmler" to "popular",
        "IMDB 7.0+" to "imdb7"
    )

    private suspend fun ensureDomain() {
        if (domainFetched) return
        withContext(Dispatchers.IO) {
            try {
                // SharedPreferences cache
                val context = try { com.myanim.kondi.KondiApplication.getContext() } catch (e: Exception) { null }
                val sharedPrefs = context?.getSharedPreferences("kondi_prefs", android.content.Context.MODE_PRIVATE)

                val cachedUrl = sharedPrefs?.getString("hdfilm_cached_domain", null)
                if (cachedUrl != null) {
                    baseUrl = cachedUrl
                    domainFetched = true
                    Timber.d("Loaded cached HDFilmCehennemi domain: $baseUrl")
                }

                val request = Request.Builder()
                    .url("https://raw.githubusercontent.com/Kraptor123/domainListesi/refs/heads/main/eklenti_domainleri.txt")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val content = response.body?.string() ?: ""
                        content.lines().forEach { line ->
                            if (line.startsWith("|HDFilmCehennemi:")) {
                                val fetchedUrl = line.substringAfter("|HDFilmCehennemi:").trim()
                                if (fetchedUrl.isNotEmpty()) {
                                    baseUrl = fetchedUrl
                                    domainFetched = true
                                    sharedPrefs?.edit()?.putString("hdfilm_cached_domain", fetchedUrl)?.apply()
                                    Timber.d("Fetched new HDFilmCehennemi domain: $baseUrl")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error ensuring HDFilmCehennemi domain")
            }
        }
    }

    private fun getHeaders(): Map<String, String> {
        return mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36",
            "Referer" to "$baseUrl/",
            "Origin" to baseUrl,
            "X-Requested-With" to "fetch"
        )
    }

    private fun Request.Builder.addHeaders(): Request.Builder {
        getHeaders().forEach { (k, v) -> header(k, v) }
        return this
    }

    @Serializable
    private data class ApiResponse(val html: String)

    @Serializable
    data class SearchResponse(val results: List<String>)

    @Serializable
    data class FilterSearchResponse(val html: String, val showMore: Boolean, val status: Int)

    suspend fun getCategoryItems(categoryPath: String, page: Int = 1): List<HdFilmCehennemiTitle> = withContext(Dispatchers.IO) {
        ensureDomain()
        val url = when (categoryPath) {
            "mostLiked" -> "$baseUrl/load/page/$page/mostLiked/"
            "home" -> "$baseUrl/load/page/$page/home/"
            "popular" -> "$baseUrl/load/page/$page/popular/"
            "imdb7" -> "$baseUrl/load/page/$page/imdb7/"
            else -> "$baseUrl/load/page/$page/$categoryPath/"
        }

        val request = Request.Builder()
            .url(url)
            .addHeaders()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val json = response.body?.string() ?: return@withContext emptyList()
                val apiResponse = gson.fromJson(json, ApiResponse::class.java)
                val doc = Jsoup.parse(apiResponse.html)
                
                return@withContext doc.select("article.item, a.poster").map { element ->
                    val linkElement = if (element.tagName() == "a") element else element.selectFirst("a[href]")
                    val titleElement = element.selectFirst("h2.flbaslik, strong.poster-title, h4.title")
                    val imgElement = element.selectFirst("img")

                    val href = linkElement?.attr("href") ?: ""
                    val title = titleElement?.text() ?: imgElement?.attr("alt") ?: "Unknown Title"
                    val poster = imgElement?.run { absUrl("data-src").ifBlank { absUrl("src") } }
                    val rating = element.selectFirst(".imdb, span.imdb")?.text()
                    val year = element.selectFirst("span.anayil")?.text()

                    HdFilmCehennemiTitle(
                        id = href.removePrefix(baseUrl).trim('/'),
                        name = title,
                        poster = poster,
                        rating = rating,
                        year = year
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading category items: $categoryPath")
            return@withContext emptyList()
        }
    }

    suspend fun search(query: String, page: Int = 1): List<HdFilmCehennemiTitle> = withContext(Dispatchers.IO) {
        ensureDomain()
        val body = FormBody.Builder().add("query", query).build()
        val request = Request.Builder()
            .url("$baseUrl/search")
            .post(body)
            .addHeaders()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val json = response.body?.string() ?: return@withContext emptyList()
                val searchResponse = gson.fromJson(json, SearchResponse::class.java)
                
                return@withContext searchResponse.results.mapNotNull { html ->
                    val element = Jsoup.parse(html).selectFirst("a[href]") ?: return@mapNotNull null
                    val title = element.selectFirst("strong.poster-title, h4.title")?.text() ?: element.selectFirst("img")?.attr("alt") ?: "Unknown"
                    val poster = element.selectFirst("img")?.run { absUrl("data-src").ifBlank { absUrl("src") } }
                    
                    HdFilmCehennemiTitle(
                        id = element.attr("href").removePrefix(baseUrl).trim('/'),
                        name = title,
                        poster = poster
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error searching: $query")
            return@withContext emptyList()
        }
    }

    suspend fun getMovieDetail(id: String): HdFilmCehennemiDetail? = withContext(Dispatchers.IO) {
        ensureDomain()
        val url = "$baseUrl/$id"
        val request = Request.Builder()
            .url(url)
            .addHeaders()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val doc = Jsoup.parse(response.body?.string() ?: "")

                val isSerie = id.contains("/dizi/") || doc.location().contains("/dizi/")
                val title = doc.selectFirst(".section-title")?.ownText()
                    ?.substringBefore(" Filminin Bilgileri")
                    ?.substringBefore(" izle") ?: "Unknown Movie"

                val div = doc.selectFirst("div.section-content div.post-info") ?: return@withContext null
                val poster = div.selectFirst("img")?.absUrl("data-src")

                val genres = div.select("div.post-info-genres > a").map { it.text() }
                val cast = div.select("div.post-info-cast > a").map { it.text() }
                val description = div.selectFirst("div.post-info-content > p")?.text()
                val rating = div.selectFirst("span.imdb")?.text()
                val year = div.selectFirst("div.post-info-genres")?.parent()?.select("a")?.firstOrNull { it.attr("href").contains("/yıl/") }?.text()

                val episodes = mutableListOf<HdFilmCehennemiEpisode>()
                if (isSerie) {
                    val numberRegex = Regex("(\\d+)\\.")
                    doc.select("div.seasons-tabs-wrapper > div.seasons-tab-content > a").forEach { element ->
                        val href = element.attr("href")
                        val name = element.selectFirst("h3, h4")?.text() ?: "Episode"
                        val (seasonNum, epNum) = numberRegex.findAll(name).map { it.groupValues.last() }.toList()
                        val episodeFloat = "$seasonNum.${epNum.padStart(3, '0')}".toFloatOrNull() ?: 1F
                        episodes.add(
                            HdFilmCehennemiEpisode(
                                name = name,
                                url = href.removePrefix(baseUrl).trim('/'),
                                episodeNum = episodeFloat
                            )
                        )
                    }
                    episodes.sortByDescending { it.episodeNum }
                } else {
                    episodes.add(
                        HdFilmCehennemiEpisode(
                            name = "Film",
                            url = id,
                            episodeNum = 1F
                        )
                    )
                }

                return@withContext HdFilmCehennemiDetail(
                    id = id,
                    title = title,
                    poster = poster,
                    description = description,
                    year = year,
                    rating = rating,
                    genres = genres,
                    cast = cast,
                    isSerie = isSerie,
                    episodes = episodes
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting movie details: $id")
            return@withContext null
        }
    }

    suspend fun getStreamUrls(detailUrl: String): List<HdFilmCehennemiSource> = withContext(Dispatchers.IO) {
        ensureDomain()
        val request = Request.Builder()
            .url("$baseUrl/$detailUrl")
            .addHeaders()
            .build()

        val sources = mutableListOf<HdFilmCehennemiSource>()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val doc = Jsoup.parse(response.body?.string() ?: "")

                val langTabs = doc.select("div.alternative-tab div.alternative-links[data-lang]")
                langTabs.forEach { tab ->
                    val lang = tab.attr("data-lang")
                    tab.select("button.alternative-link[data-video]").forEach { btn ->
                        val providerName = btn.text()
                        val videoId = btn.attr("data-video")
                        val name = "[$lang] $providerName"
                        
                        try {
                            val videoRequest = Request.Builder()
                                .url("$baseUrl/video/$videoId/")
                                .addHeaders()
                                .build()
                            
                            client.newCall(videoRequest).execute().use { videoResponse ->
                                if (videoResponse.isSuccessful) {
                                    val html = videoResponse.body?.string() ?: ""
                                    val srcUrl = html.substringAfter("src=").substringBefore(' ')
                                        .trim('\\', '"', '\'', ' ')
                                        .replace("\\/", "/")
                                    
                                    if (srcUrl.isNotEmpty()) {
                                        sources.add(
                                            HdFilmCehennemiSource(
                                                name = name,
                                                url = srcUrl
                                            )
                                        )
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error loading video source URL for $videoId")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting stream URLs: $detailUrl")
        }
        return@withContext sources
    }

    suspend fun resolveSource(url: String): String? = withContext(Dispatchers.IO) {
        Timber.d("resolveSource: $url")
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                .header("Referer", "$baseUrl/")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val html = response.body?.string() ?: return@withContext null

                // Rapidrame, Closeload etc. Edwards unpacker
                if (JsUnpacker.detect(html)) {
                    val unpacked = JsUnpacker.unpackAndCombine(html) ?: html
                    val varName = unpacked.substringAfter("atob(").substringBefore(")", "")
                    if (varName.isNotEmpty()) {
                        val propertyVal = unpacked.substringAfter("$varName=\"", "").substringBefore("\"", "")
                        if (propertyVal.isNotEmpty()) {
                            val playlistUrl = String(Base64.decode(propertyVal, Base64.DEFAULT))
                            if (playlistUrl.startsWith("http")) return@withContext playlistUrl
                        }
                    }
                }

                // Vidmoly extractor
                if (url.contains("vidmoly")) {
                    val fileUrl = html.substringAfter("file:\"", "").substringBefore('"', "")
                    if (fileUrl.isNotEmpty()) return@withContext fileUrl
                    val sourceUrl = Pattern.compile("sources:\\s*\\[\\{file:\"([^\"]+)\"").matcher(html)
                    if (sourceUrl.find()) return@withContext sourceUrl.group(1)
                }

                // Fallback: look for typical file references
                val fileMatcher = Pattern.compile("file\":\\s*\"(http[^\"]+)\"").matcher(html)
                if (fileMatcher.find()) return@withContext fileMatcher.group(1)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error resolving source: $url")
        }
        return@withContext null
    }
}

data class HdFilmCehennemiTitle(
    val id: String,
    val name: String,
    val poster: String?,
    val rating: String? = null,
    val year: String? = null
)

data class HdFilmCehennemiDetail(
    val id: String,
    val title: String,
    val poster: String?,
    val description: String?,
    val year: String?,
    val rating: String?,
    val genres: List<String>?,
    val cast: List<String>?,
    val isSerie: Boolean,
    val episodes: List<HdFilmCehennemiEpisode>
)

data class HdFilmCehennemiEpisode(
    val name: String,
    val url: String,
    val episodeNum: Float
)

data class HdFilmCehennemiSource(
    val name: String,
    val url: String
)

annotation class Serializable

// Standard JsUnpacker implementation inside
object JsUnpacker {
    private val packedRegex = Regex("eval[(]function[(]p,a,c,k,e,[r|d]?", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private val packedExtractRegex = Regex("[}][(]'(.*)', *(\\d+), *(\\d+), *'(.*?)'[.]split[(]'[|]'[)]", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private val unpackReplaceRegex = Regex("\\b\\w+\\b", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    fun detect(scriptBlock: String): Boolean {
        return scriptBlock.contains(packedRegex)
    }

    fun unpack(scriptBlock: String): Sequence<String> {
        return if (!detect(scriptBlock)) {
            emptySequence()
        } else {
            unpacking(scriptBlock)
        }
    }

    fun unpackAndCombine(scriptBlock: String): String? {
        val unpacked = unpack(scriptBlock)
        return if (unpacked.toList().isEmpty()) {
            null
        } else {
            unpacked.joinToString(" ")
        }
    }

    private fun unpacking(scriptBlock: String): Sequence<String> {
        val unpacked = packedExtractRegex.findAll(scriptBlock).mapNotNull { result ->
            val payload = result.groups[1]?.value
            val symtab = result.groups[4]?.value?.split('|')
            val radix = result.groups[2]?.value?.toIntOrNull() ?: 10
            val count = result.groups[3]?.value?.toIntOrNull()
            val unbaser = Unbaser(radix)

            if (symtab == null || count == null || symtab.size != count) {
                null
            } else {
                payload?.replace(unpackReplaceRegex) { match ->
                    val word = match.value
                    val unbased = symtab[unbaser.unbase(word)]
                    unbased.ifEmpty {
                        word
                    }
                }
            }
        }
        return unpacked
    }
}

internal data class Unbaser(
    private val base: Int
) {
    private val selector: Int = when {
        base > 62 -> 95
        base > 54 -> 62
        base > 52 -> 54
        else -> 52
    }

    fun unbase(value: String): Int {
        return if (base in 2..36) {
            value.toIntOrNull(base) ?: 0
        } else {
            val dict = ALPHABET[selector]?.toCharArray()?.mapIndexed { index, c ->
                c to index
            }?.toMap()
            var returnVal = 0

            val valArray = value.toCharArray().reversed()
            for (i in valArray.indices) {
                val cipher = valArray[i]
                returnVal += (base.toFloat().pow(i) * (dict?.get(cipher) ?: 0)).toInt()
            }
            returnVal
        }
    }

    companion object {
        private val ALPHABET = mapOf<Int, String>(
            52 to "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP",
            54 to "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQR",
            62 to "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ",
            95 to " !\"#\$%&\\'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"
        )
    }
}
