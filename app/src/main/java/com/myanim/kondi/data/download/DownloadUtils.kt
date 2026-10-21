package com.myanim.kondi.data.download

import android.util.Log
import kotlinx.coroutines.delay
import kotlin.math.pow

object DownloadUtils {
    private const val TAG = "DownloadUtils"

    /** Dosya adı boş/tamamen geçersiz olduğunda kullanılan yedek ad. */
    const val FALLBACK_FILE_NAME = "video"

    /** ext4/FAT dosya sistemi 255 bayt sınırı; güvenli tarafta kalmak için 200 bayt. */
    const val MAX_FILE_NAME_BYTES = 200

    /** Karakter sayısı bazlı kolaylık limiti (testler ve çağıranlar için). */
    const val MAX_FILE_NAME_LENGTH = 100

    /** Windows'ta ayrılmış dosya adları. */
    private val RESERVED_WINDOWS_NAMES = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    )

    suspend fun <T> retryWithBackoff(
        maxRetries: Int = 5,
        initialDelay: Long = 1000L,
        maxDelay: Long = 30000L,
        factor: Double = 2.0,
        onRetry: (Int, Exception) -> Unit = { _, _ -> },
        block: suspend () -> T
    ): T {
        val totalAttempts = maxRetries.coerceAtLeast(1)
        var currentDelay = initialDelay
        repeat(totalAttempts) { attempt ->
            try {
                return block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // Never retry on cancellation
            } catch (e: PausedException) {
                throw e // Never retry on pause
            } catch (e: Exception) {
                if (e.message == "PAUSED" || attempt == totalAttempts - 1) {
                    throw e
                }
                
                onRetry(attempt + 1, e)
                Log.w(TAG, "Attempt ${attempt + 1} failed, retrying in ${currentDelay}ms: ${e.message}")
                
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
        }
        // repeat() her zaman ya block() ile döner ya da son denemede exception fırlatır.
        // Bu satır yalnızca derleyici için gereklidir (defansif).
        return block()
    }

    /**
     * Verilen metni dosya sistemi ve Android tarafından güvenli bir dosya adına dönüştürür.
     *
     * Korumalar:
     * - Yol ayırıcıları ve Windows yasaklı karakterleri kaldırılır.
     * - Kontrol karakterleri (0x00-0x1F) temizlenir (log injection / bozuk dosya adı).
     * - Path traversal (".." ) engellenir.
     * - Windows ayrılmış adları (CON, PRN, AUX, NUL, COM1..9, LPT1..9) kaçınılır.
     * - Sondaki boşluk/nokta temizlenir (Windows normalizasyonu).
     * - Uzunluk sınırı uygulanır (ext4/FAT: 255 bayt).
     */
    fun sanitizeFileName(name: String): String {
        // 1) Kontrol karakterleri + yol ayırıcıları + yasaklı karakterler
        var cleaned = name
            .replace(Regex("[\\u0000-\\u001F\\u007F]"), "")   // kontrol karakterleri
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")            // yol ayırıcı + yasaklı karakterler

        // 2) Yalnızca nokta/boşluk karakterlerinden oluşan girdiler (veya tamamen boş girdi)
        if (cleaned.isBlank() || cleaned.all { it == '.' || it == ' ' }) {
            return FALLBACK_FILE_NAME
        }

        // 3) Path traversal: baştaki noktaları temizle, ".." dizisini noktasızlaştır
        cleaned = cleaned.trimStart('.', ' ')
        cleaned = cleaned.replace("..", ".")
        if (cleaned.isBlank() || cleaned.all { it == '.' || it == ' ' }) {
            return FALLBACK_FILE_NAME
        }

        // 4) Sondaki boşluk ve noktalar (Windows bunları sessizce kırpar)
        cleaned = cleaned.trimEnd(' ', '.')

        // 5) Windows ayrılmış adları
        val stem = cleaned.substringBefore('.')
        if (stem.uppercase() in RESERVED_WINDOWS_NAMES) {
            cleaned = "_$cleaned"
        }

        // 6) Uzunluk sınırı. ext4/FAT dosya sisteminde sınır 255 BAYT'tır; karakter
        //    sayısı ile bayt sayısı farklıdır (Türkçe/Japonca karakterler çok baytlıdır).
        //    Hem bayt hem karakter sınırı uygulanır; kod noktası (code point) bölünmez.
        val safe = truncateToChars(
            truncateToBytes(cleaned, MAX_FILE_NAME_BYTES),
            MAX_FILE_NAME_LENGTH
        ).trimEnd(' ', '.')
        if (safe.isBlank()) return FALLBACK_FILE_NAME

        return safe
    }

    /**
     * Metni verilen karakter sınırına göre keser. Vekil (surrogate) çiftleri
     * bölünmez; iki baytlık karakterler bozulmaz.
     */
    private fun truncateToChars(value: String, maxChars: Int): String {
        if (value.length <= maxChars) return value
        val builder = StringBuilder()
        var count = 0
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            val charCount = Character.charCount(codePoint)
            if (count + charCount > maxChars) break
            builder.appendCodePoint(codePoint)
            count += charCount
            index += charCount
        }
        return builder.toString()
    }

    /**
     * Metni verilen bayt sınırına göre keser. Çok baytlı karakterler ve
     * vekil (surrogate) çiftleri bölünmez.
     */
    private fun truncateToBytes(value: String, maxBytes: Int): String {
        if (value.toByteArray(Charsets.UTF_8).size <= maxBytes) return value

        val builder = StringBuilder()
        var bytes = 0
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            val charCount = Character.charCount(codePoint)
            val chunk = value.substring(index, index + charCount)
            val chunkBytes = chunk.toByteArray(Charsets.UTF_8).size
            if (bytes + chunkBytes > maxBytes) break
            builder.append(chunk)
            bytes += chunkBytes
            index += charCount
        }
        return builder.toString()
    }

    fun createAnimecixFileName(animeName: String, season: Int, episode: Int): String {
        val s = season.toString().padStart(2, '0')
        val e = episode.toString().padStart(2, '0')
        return sanitizeFileName("${animeName}_s${s}_e${e}.mp4")
    }
}
