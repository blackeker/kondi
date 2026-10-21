package com.myanim.kondi

import com.myanim.kondi.data.animecix.AnimecixScraper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.PrintWriter
import java.io.StringWriter

/**
 * CANLI AĞ (ENTEGRASYON) TESTİ.
 *
 * Bu test gerçek animecix sunucusuna istek atar; ağ olmadan veya sunucu
 * engellendiğinde çalışamaz. Bu nedenle varsayılan olarak ATLANIR ve yalnızca
 * KONDI_INTEGRATION_TESTS=true ortam değişkeni ile çalıştırılır.
 *
 * DÜZELTİLEN HATALAR:
 *  1) Hata/çıktı dosyası sabit bir Windows yoluna
 *     ("c:/Users/keke/Desktop/projeler/kondi/test_error.txt") yazılıyordu.
 *     Bu yol başka makine/CI'da var olmadığı için test, ağ hatası olmasa bile
 *     FileNotFoundException ile patlıyordu. Artık JUnit TemporaryFolder kullanılıyor.
 *  2) Test ağ bağlantısına bağımlıydı; ağ yoksa patlıyordu. Artık canlı ağ
 *     testleri açıkça etkinleştirilmelidir.
 */
class AnimecixScraperTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun integrationEnabled(): Boolean =
        System.getenv("KONDI_INTEGRATION_TESTS") == "true"

    @Test
    fun testGetLatestEpisodes() = runBlocking {
        assumeTrue(
            "Canlı ağ testi. KONDI_INTEGRATION_TESTS=true ile çalıştırın.",
            integrationEnabled()
        )

        val scraper = AnimecixScraper()
        val reportFile = temporaryFolder.newFile("test_report.txt")

        try {
            val episodes = scraper.getLatestEpisodes(1)
            val msg = "Successfully fetched ${episodes.size} episodes."
            reportFile.writeText(msg)
            assertTrue("Episodes should not be empty", episodes.isNotEmpty())
        } catch (t: Throwable) {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()
            reportFile.writeText("Error caught:\n$stackTrace")
            // Rapor geçici klasöre yazıldı; test artık "dosya bulunamadı" hatasıyla değil,
            // gerçek nedenle başarısız oluyor.
            throw AssertionError("Canlı ağ testi başarısız oldu:\n$stackTrace")
        }
    }
}
