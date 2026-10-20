package com.myanim.kondi.data.animecix

import org.junit.Test
import org.junit.Assert.*

class AnimecixScraperUnitTest {

    @Test
    fun testAnimecixVideoSafeProperties() {
        val video = AnimecixVideo(
            episodeNumber = null,
            seasonNumber = null,
            poster = null,
            name = null,
            description = null,
            directUrl = null,
            directEpisodeId = null,
            animeId = null,
            language = null,
            category = null
        )
        assertEquals("Bilinmeyen", video.safeName)
        assertEquals(0, video.safeEpisodeNumber)
        assertEquals(1, video.safeSeasonNumber)
        assertEquals("", video.safeCategory)
        assertNull(video.safePoster)
    }

    @Test
    fun testAnimecixVideoSafePropertiesWithValues() {
        val video = AnimecixVideo(
            episodeNumber = 5,
            seasonNumber = 2,
            poster = "http://example.com/poster.jpg",
            name = "Test Episode",
            description = "A test episode",
            directUrl = "http://example.com/video.mp4",
            directEpisodeId = 100,
            animeId = 1,
            language = "TR",
            category = "anime"
        )
        assertEquals("Test Episode", video.safeName)
        assertEquals(5, video.safeEpisodeNumber)
        assertEquals(2, video.safeSeasonNumber)
        assertEquals("anime", video.safeCategory)
        assertEquals("http://example.com/poster.jpg", video.safePoster)
    }

    @Test
    fun testAnimecixTitleSafeProperties() {
        val title = AnimecixTitle(
            titleObject = null,
            directId = null,
            directName = null,
            directPoster = null
        )
        assertNull(title.id)
        assertNull(title.name)
        assertNull(title.poster)
    }

    @Test
    fun testAnimecixTitleWithTitleObject() {
        val anime = AnimecixAnime(
            id = 42,
            title = "From Title Object",
            poster = null,
            description = null,
            year = 2022,
            rating = "9.0",
            genres = emptyList(),
            seasonCount = 1,
            seasons = emptyList(),
            videos = emptyList(),
            trailerUrl = null,
            credits = emptyList()
        )
        val title = AnimecixTitle(
            titleObject = anime,
            directId = null,
            directName = null,
            directPoster = null
        )
        assertEquals(42, title.id)
        assertEquals("From Title Object", title.name)
    }

    @Test
    fun testAnimecixSourceCreation() {
        val source = AnimecixSource(
            name = "Test Source",
            url = "http://example.com/video.mp4",
            type = "mp4",
            extra = null,
            language = "TR",
            quality = "1080p"
        )
        assertEquals("Test Source", source.name)
        assertEquals("http://example.com/video.mp4", source.url)
        assertEquals("mp4", source.type)
        assertEquals("TR", source.language)
        assertEquals("1080p", source.quality)
    }

    @Test
    fun testAnimecixAnimeCreation() {
        val anime = AnimecixAnime(
            id = 1,
            title = "Test Anime",
            poster = "http://example.com/poster.jpg",
            description = "A test anime",
            year = 2023,
            rating = "8.5",
            genres = emptyList(),
            seasonCount = 1,
            seasons = emptyList(),
            videos = emptyList(),
            trailerUrl = null,
            credits = emptyList()
        )
        assertEquals(1, anime.id)
        assertEquals("Test Anime", anime.title)
        assertEquals(2023, anime.year)
        assertEquals("8.5", anime.rating)
        assertEquals(1, anime.seasonCount)
    }

    @Test
    fun testAnimecixVideoDetailCreation() {
        val detail = AnimecixVideoDetail(
            url = "http://example.com/video.mp4",
            episodeId = 100,
            name = "Episode 1"
        )
        assertEquals("http://example.com/video.mp4", detail.url)
        assertEquals(100, detail.episodeId)
        assertEquals("Episode 1", detail.name)
    }

    @Test
    fun testAnimecixVideoUrlGetter() {
        val videoWithDirectUrl = AnimecixVideo(
            episodeNumber = 1,
            seasonNumber = 1,
            poster = null,
            name = "Test",
            description = null,
            directUrl = "http://direct.com/video.mp4",
            directEpisodeId = 1,
            animeId = 1,
            language = null,
            category = null
        )
        assertEquals("http://direct.com/video.mp4", videoWithDirectUrl.url)

        val videoWithNestedUrl = AnimecixVideo(
            episodeNumber = 1,
            seasonNumber = 1,
            poster = null,
            name = "Test",
            description = null,
            directUrl = null,
            directEpisodeId = null,
            animeId = 1,
            language = null,
            category = null,
            videos = listOf(AnimecixVideoDetail("http://nested.com/video.mp4", 2, "Nested"))
        )
        assertEquals("http://nested.com/video.mp4", videoWithNestedUrl.url)
    }

    @Test
    fun testAnimecixVideoEpisodeIdGetter() {
        val videoWithDirectId = AnimecixVideo(
            episodeNumber = 1,
            seasonNumber = 1,
            poster = null,
            name = "Test",
            description = null,
            directUrl = null,
            directEpisodeId = 50,
            animeId = 1,
            language = null,
            category = null
        )
        assertEquals(50, videoWithDirectId.episodeId)

        val videoWithNestedId = AnimecixVideo(
            episodeNumber = 1,
            seasonNumber = 1,
            poster = null,
            name = "Test",
            description = null,
            directUrl = null,
            directEpisodeId = null,
            animeId = 1,
            language = null,
            category = null,
            videos = listOf(AnimecixVideoDetail("http://nested.com/video.mp4", 75, "Nested"))
        )
        assertEquals(75, videoWithNestedId.episodeId)
    }
}
