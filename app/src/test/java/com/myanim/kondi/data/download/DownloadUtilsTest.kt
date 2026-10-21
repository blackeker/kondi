package com.myanim.kondi.data.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * DownloadUtils saf (pure) yardımcı fonksiyonlarının birim testleri.
 * Bu fonksiyonlar dosya sistemine yazılan adları ürettiği için hataları
 * doğrudan diskte veri kaybına / path traversal'a yol açabilir.
 */
class DownloadUtilsTest {

    // ------------------------------------------------------------------
    // sanitizeFileName
    // ------------------------------------------------------------------

    @Test
    fun `sanitizeFileName removes path separators and reserved characters`() {
        val result = DownloadUtils.sanitizeFileName("a/b\\c:d*e?f\"g<h>i|j")
        assertFalse("Sonuçta yol ayırıcı kalmamalı", result.contains('/'))
        assertFalse("Sonuçta yol ayırıcı kalmamalı", result.contains('\\'))
        listOf(':', '*', '?', '"', '<', '>', '|').forEach {
            assertFalse("Yasaklı karakter kaldı: $it", result.contains(it))
        }
    }

    @Test
    fun `sanitizeFileName prevents path traversal`() {
        val result = DownloadUtils.sanitizeFileName("../../etc/passwd")
        assertFalse("Path traversal engellenmeli", result.contains(".."))
        assertFalse("Path traversal engellenmeli", result.contains('/'))
        assertFalse("Path traversal engellenmeli", result.contains('\\'))
    }

    @Test
    fun `sanitizeFileName collapses dot only input to fallback`() {
        assertEquals(DownloadUtils.FALLBACK_FILE_NAME, DownloadUtils.sanitizeFileName(".."))
        assertEquals(DownloadUtils.FALLBACK_FILE_NAME, DownloadUtils.sanitizeFileName("."))
        assertEquals(DownloadUtils.FALLBACK_FILE_NAME, DownloadUtils.sanitizeFileName("   "))
    }

    @Test
    fun `sanitizeFileName handles empty input`() {
        assertEquals(DownloadUtils.FALLBACK_FILE_NAME, DownloadUtils.sanitizeFileName(""))
    }

    @Test
    fun `sanitizeFileName removes control characters`() {
        val result = DownloadUtils.sanitizeFileName("naruto\u0000\u0007\u001B\n\rto\u000B")
        assertFalse("Kontrol karakteri kalmamalı", result.any { it.code < 32 || it.code == 127 })
    }

    @Test
    fun `sanitizeFileName keeps unicode and turkish characters`() {
        // Anime uygulaması için Türkçe/Japonca karakterler kaybolmamalı.
        val result = DownloadUtils.sanitizeFileName("Şōnaratek: 鬼滅の刃")
        assertTrue("Türkçe karakter korunmalı", result.contains('Ş'))
        assertTrue("Japonca karakter korunmalı", result.contains('鬼'))
    }

    @Test
    fun `sanitizeFileName escapes windows reserved device names`() {
        val result = DownloadUtils.sanitizeFileName("CON")
        assertNotEquals("CON", result)
        assertTrue("Ayrılmış ad _ ile korunmalı", result.startsWith("_"))
    }

    @Test
    fun `sanitizeFileName trims trailing dots and spaces`() {
        val result = DownloadUtils.sanitizeFileName("video name... ")
        assertFalse("Sondaki nokta kalmamalı", result.endsWith("."))
        assertFalse("Sondaki boşluk kalmamalı", result.endsWith(" "))
    }

    @Test
    fun `sanitizeFileName enforces maximum length`() {
        val long = "a".repeat(500)
        val result = DownloadUtils.sanitizeFileName(long)
        assertTrue(
            "Dosya adı uzunluğu sınırı aşılmamalı (sonuç: ${result.length})",
            result.length <= DownloadUtils.MAX_FILE_NAME_LENGTH
        )
    }

    @Test
    fun `sanitizeFileName enforces byte limit for multibyte characters`() {
        // ext4/FAT sınırı 255 BAYT'tır. Çok baytlı karakterlerde karakter sayısı
        // sınırı tek başına yeterli değildir; bayt sınırı da uygulanmalıdır.
        val japanese = "鬼".repeat(500)
        val result = DownloadUtils.sanitizeFileName(japanese)
        val byteSize = result.toByteArray(Charsets.UTF_8).size
        assertTrue(
            "Dosya adı bayt sınırını aşmamalı (sonuç: $byteSize bayt)",
            byteSize <= DownloadUtils.MAX_FILE_NAME_BYTES
        )
    }

    @Test
    fun `sanitizeFileName does not split surrogate pairs`() {
        // Emoji gibi 4 baytlık karakterler bozulmamalı (bozuk UTF-16 = geçersiz dosya adı).
        val emoji = "😀".repeat(200)
        val result = DownloadUtils.sanitizeFileName(emoji)

        // Doğru çiftlenmiş bir vekil çifti iki Char olarak görünür; önemli olan
        // YALNIZ (eşleşmeyen) vekilin bulunmamasıdır.
        var i = 0
        while (i < result.length) {
            val ch = result[i]
            if (Character.isHighSurrogate(ch)) {
                assertTrue(
                    "Yüksek vekilin ardından düşük vekil gelmeli (index=$i)",
                    i + 1 < result.length && Character.isLowSurrogate(result[i + 1])
                )
                i += 2
            } else {
                assertFalse(
                    "Yalnız düşük vekil olmamalı (index=$i, kod=${ch.code})",
                    Character.isLowSurrogate(ch)
                )
                i += 1
            }
        }

        // Kesme sırasında emoji kaybolmamalı (bütünlük bozulmamalı)
        val emojiCount = result.split("😀").size - 1
        assertTrue("Emoji adedi korunmalı (bulunan: $emojiCount)", emojiCount > 0)
    }

    @Test
    fun `sanitizeFileName is idempotent`() {
        val once = DownloadUtils.sanitizeFileName("Dune: Part Two/2024")
        val twice = DownloadUtils.sanitizeFileName(once)
        assertEquals("Sanitize işlemi idempotent olmalı", once, twice)
    }

    // ------------------------------------------------------------------
    // createAnimecixFileName
    // ------------------------------------------------------------------

    @Test
    fun `createAnimecixFileName formats season and episode with padding`() {
        val name = DownloadUtils.createAnimecixFileName("Attack on Titan", 2, 5)
        assertTrue("Beklenen format: $name", name.endsWith("_s02_e05.mp4"))
    }

    @Test
    fun `createAnimecixFileName sanitizes anime name`() {
        val name = DownloadUtils.createAnimecixFileName("Fate/Zero", 1, 1)
        assertFalse("Ad içinde yol ayırıcı olmamalı", name.contains('/'))
    }

    // ------------------------------------------------------------------
    // retryWithBackoff
    // ------------------------------------------------------------------

    @Test
    fun `retryWithBackoff returns result without retrying on first success`() = runTest {
        var attempts = 0
        val result = DownloadUtils.retryWithBackoff(maxRetries = 3, initialDelay = 1000) {
            attempts++
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(1, attempts)
    }

    @Test
    fun `retryWithBackoff retries transient failures and eventually succeeds`() = runTest {
        var attempts = 0
        val result = DownloadUtils.retryWithBackoff(
            maxRetries = 5,
            initialDelay = 1000,
            onRetry = { _, _ -> }
        ) {
            attempts++
            if (attempts < 3) throw IOException("geçici hata")
            "başarılı"
        }
        assertEquals("başarılı", result)
        assertEquals(3, attempts)
    }

    @Test
    fun `retryWithBackoff throws original exception when attempts are exhausted`() = runTest {
        var attempts = 0
        try {
            DownloadUtils.retryWithBackoff(
                maxRetries = 3,
                initialDelay = 1000,
                onRetry = { _, _ -> }
            ) {
                attempts++
                throw IOException("kalıcı hata")
            }
            org.junit.Assert.fail("Exception bekleniyordu")
        } catch (e: IOException) {
            assertEquals("kalıcı hata", e.message)
        }
        // maxRetries kez denenir (3), her biri başarısız.
        assertEquals(3, attempts)
    }

    @Test
    fun `retryWithBackoff does not retry on cancellation`() = runTest {
        var attempts = 0
        try {
            DownloadUtils.retryWithBackoff(
                maxRetries = 5,
                initialDelay = 1000,
                onRetry = { _, _ -> org.junit.Assert.fail("İptal durumunda yeniden denenmemeli") }
            ) {
                attempts++
                throw CancellationException("kullanıcı iptali")
            }
            org.junit.Assert.fail("CancellationException bekleniyordu")
        } catch (e: CancellationException) {
            // Beklenen davranış
        }
        assertEquals("İptal sonrası tekrar denenmemeli", 1, attempts)
    }

    @Test
    fun `retryWithBackoff does not retry on pause`() = runTest {
        var attempts = 0
        try {
            DownloadUtils.retryWithBackoff(
                maxRetries = 5,
                initialDelay = 1000,
                onRetry = { _, _ -> org.junit.Assert.fail("Duraklatma durumunda yeniden denenmemeli") }
            ) {
                attempts++
                throw PausedException()
            }
            org.junit.Assert.fail("PausedException bekleniyordu")
        } catch (e: PausedException) {
            // Beklenen davranış
        }
        assertEquals("Duraklatma sonrası tekrar denenmemeli", 1, attempts)
    }

    @Test
    fun `retryWithBackoff with zero maxRetries still executes block once`() = runTest {
        var attempts = 0
        DownloadUtils.retryWithBackoff(maxRetries = 0, initialDelay = 1000) {
            attempts++
            "ok"
        }
        assertEquals(1, attempts)
    }
}
