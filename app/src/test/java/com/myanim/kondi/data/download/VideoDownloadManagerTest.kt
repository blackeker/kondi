package com.myanim.kondi.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VideoDownloadManager'ın saf (pure) yardımcı fonksiyonlarının birim testleri.
 *
 * ÖNCEKİ HALİ: Bu dosya, `VideoDownloadManager`'ın gerçek implementasyonunu
 * hiç test etmeyen bir `FakeDownloadManager` sınıfı tanımlıyordu ve o sahte
 * sınıfın kendi davranışını doğruluyordu. Bu tür testler her zaman geçer ve
 * üretim kodundaki hiçbir hata yakalayamaz. Testler artık gerçek fonksiyonları
 * doğruluyor.
 */
class VideoDownloadManagerTest {

    // ------------------------------------------------------------------
    // getHeadersForUrlInternal
    // ------------------------------------------------------------------

    @Test
    fun `headers always contain user agent and requested with`() {
        val headers = getHeadersForUrlInternal("https://example.com/video.mp4")
        assertNotNull(headers["User-Agent"])
        assertTrue(headers["User-Agent"]!!.isNotBlank())
        assertEquals("com.kraptor.AnimeciX", headers["X-Requested-With"])
        assertNotNull(headers["Referer"])
    }

    @Test
    fun `doodstream urls get doodstream referer`() {
        val headers = getHeadersForUrlInternal("https://doodstream.com/abc123")
        assertEquals("https://doodstream.com/", headers["Referer"])
    }

    @Test
    fun `sibnet urls get sibnet referer`() {
        val headers = getHeadersForUrlInternal("https://video.sibnet.ru/12345")
        assertEquals("https://video.sibnet.ru/", headers["Referer"])
    }

    @Test
    fun `unknown urls fall back to animecix referer`() {
        val headers = getHeadersForUrlInternal("https://example.com/video.mp4")
        assertEquals("https://animecix.net/", headers["Referer"])
    }

    @Test
    fun `malformed url does not crash and still returns headers`() {
        val headers = getHeadersForUrlInternal("ht!tp://:::bozuk url")
        assertNotNull(headers["User-Agent"])
        assertNotNull(headers["Referer"])
    }

    @Test
    fun `host specific referer is not applied by false positive on path`() {
        // "tau" kelimesi yol içinde geçse bile (örn. "tautology.mp4") domain eşleşmesi
        // yapılmamalı; referer varsayılan kalmalıdır.
        val headers = getHeadersForUrlInternal("https://example.com/tautology.mp4")
        assertEquals("https://animecix.net/", headers["Referer"])
    }

    // ------------------------------------------------------------------
    // sanitizeFileNameInternal
    // ------------------------------------------------------------------

    @Test
    fun `sanitize removes illegal characters`() {
        val result = sanitizeFileNameInternal("a/b\\c:d*e?f\"g<h>i|j")
        listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|').forEach {
            assertFalse("Yasaklı karakter kaldı: $it", result.contains(it))
        }
    }

    @Test
    fun `sanitize keeps unicode characters`() {
        // Önceki uygulama Regex("[^a-zA-Z0-9]") kullanıyordu; Türkçe ve Japonca
        // karakterler "_" ile değiştiriliyordu (anime adları için veri kaybı).
        val result = sanitizeFileNameInternal("Şōnaratek 鬼滅の刃")
        assertTrue("Türkçe karakter korunmalı", result.contains('Ş'))
        assertTrue("Japonca karakter korunmalı", result.contains('鬼'))
    }

    @Test
    fun `sanitize prevents path traversal`() {
        val result = sanitizeFileNameInternal("../../secret")
        assertFalse("Path traversal engellenmeli", result.contains(".."))
        assertFalse("Path traversal engellenmeli", result.contains('/'))
    }

    @Test
    fun `sanitize returns fallback for empty input`() {
        assertEquals(DownloadUtils.FALLBACK_FILE_NAME, sanitizeFileNameInternal(""))
    }
}
