package com.myanim.kondi

import com.myanim.kondi.data.animecix.AnimecixScraper
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class AnimecixScraperTest {
    @Test
    fun testGetLatestEpisodes() = runBlocking {
        println("Starting scraper test...")
        val scraper = AnimecixScraper()
        val errorFile = File("c:/Users/keke/Desktop/projeler/kondi/test_error.txt")
        try {
            val episodes = scraper.getLatestEpisodes(1)
            val msg = "Successfully fetched ${episodes.size} episodes."
            println(msg)
            errorFile.writeText(msg)
            assertTrue("Episodes should not be empty", episodes.isNotEmpty())
        } catch (t: Throwable) {
            println("Throwable caught in test!")
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()
            errorFile.writeText("Error caught:\n$stackTrace")
            fail(stackTrace)
        }
    }
}
