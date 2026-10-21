package com.myanim.kondi.data.animecix

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Animecix alan adı doğrulamasının birim testleri.
 *
 * Alan adı listesi harici bir depodan çekiliyor. Doğrulama olmazsa, o depoya
 * erişimi olan biri tüm uygulama trafiğini kendi sunucusuna yönlendirebilir.
 */
class AnimecixDomainValidationTest {

    @Test
    fun `accepts known animecix domains over https`() {
        listOf(
            "https://animecix.tv",
            "https://animecix.net",
            "https://www.animecix.net",
            "https://animecix.one",
            "https://animecix.link",
            "https://sub.domain.animecix.tv"
        ).forEach {
            assertTrue("Geçerli olmalı: $it", AnimecixScraper.isValidAnimecixDomain(it))
        }
    }

    @Test
    fun `accepts domains with multi label public suffixes`() {
        // TLD tek etiketli olmak zorunda değil (ör. animecix.co.uk).
        // Sabit etiket sayısı kullanan bir kural bunları yanlışlıkla reddeder.
        listOf(
            "https://animecix.co.uk",
            "https://www.animecix.com.tr"
        ).forEach {
            assertTrue("Geçerli olmalı: $it", AnimecixScraper.isValidAnimecixDomain(it))
        }
    }

    @Test
    fun `rejects cleartext http domains`() {
        assertFalse(
            "HTTP adres kabul edilmemeli",
            AnimecixScraper.isValidAnimecixDomain("http://animecix.tv")
        )
    }

    @Test
    fun `rejects unrelated domains`() {
        listOf(
            "https://evil.com",
            "https://animecix.evil.com",
            "https://notanimecix.tv",
            "https://animecixtv",
            "https://animecix",
            "https://"
        ).forEach {
            assertFalse("Reddedilmeli: $it", AnimecixScraper.isValidAnimecixDomain(it))
        }
    }

    @Test
    fun `rejects domains where animecix is the top level domain`() {
        // "animecix" bir TDE olamaz; aksi halde "animecix" adı ele geçirilebilir.
        assertFalse(
            "Reddedilmeli: https://animecix",
            AnimecixScraper.isValidAnimecixDomain("https://animecix")
        )
    }

    @Test
    fun `rejects ip addresses`() {
        listOf(
            "https://1.2.3.4",
            "https://192.168.1.10",
            "https://111.235.150.203"
        ).forEach {
            assertFalse("IP adresi reddedilmeli: $it", AnimecixScraper.isValidAnimecixDomain(it))
        }
    }

    @Test
    fun `rejects embedded credentials and ports`() {
        listOf(
            "https://user:pass@animecix.tv",
            "https://animecix.tv:8080",
            "https://evil.com@animecix.tv"
        ).forEach {
            assertFalse("Reddedilmeli: $it", AnimecixScraper.isValidAnimecixDomain(it))
        }
    }

    @Test
    fun `rejects blank and malformed input`() {
        listOf("", "   ", "animecix.tv", "://animecix.tv", "https://", "ftp://animecix.tv")
            .forEach {
                assertFalse("Reddedilmeli: '$it'", AnimecixScraper.isValidAnimecixDomain(it))
            }
    }

    @Test
    fun `tolerates surrounding whitespace and trailing slash`() {
        assertTrue(AnimecixScraper.isValidAnimecixDomain("  https://animecix.tv  "))
        assertTrue(AnimecixScraper.isValidAnimecixDomain("https://animecix.tv/"))
    }
}
