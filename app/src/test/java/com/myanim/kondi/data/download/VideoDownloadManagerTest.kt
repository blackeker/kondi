package com.myanim.kondi.data.download

import com.myanim.kondi.data.local.Download
import com.myanim.kondi.data.local.DownloadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class FakeDownloadManager : DownloadManager {
    override val downloadsFlow: StateFlow<List<Download>> =
        MutableStateFlow<List<Download>>(emptyList()).asStateFlow()

    var canceledIds = mutableListOf<String>()
    var retriedIds = mutableListOf<String>()
    var pausedIds = mutableListOf<String>()
    var resumedIds = mutableListOf<String>()
    var deletedIds = mutableListOf<String>()
    var clearAllCompletedCalled = false
    var clearImageCacheCalled = false
    var clearStrayChunksCalled = false
    var clearLogsCalled = false

    override fun cancelDownload(id: String) { canceledIds += id }
    override fun retryDownload(id: String) { retriedIds += id }
    override fun pauseDownload(id: String) { pausedIds += id }
    override fun resumeDownload(id: String) { resumedIds += id }
    override fun deleteDownload(id: String) { deletedIds += id }
    override fun clearAllCompleted() { clearAllCompletedCalled = true }
    override fun clearImageCache(): Boolean { clearImageCacheCalled = true; return true }
    override fun clearStrayChunks(): Int { clearStrayChunksCalled = true; return 0 }
    override fun clearLogs(): Boolean { clearLogsCalled = true; return true }
    override fun destroy() {}
}

class VideoDownloadManagerTest {

    @Test
    fun testFakeDownloadManagerDelegates() = runBlocking {
        val fake = FakeDownloadManager()
        fake.cancelDownload("test-1")
        fake.retryDownload("test-2")
        fake.pauseDownload("test-3")
        fake.resumeDownload("test-4")
        fake.deleteDownload("test-5")
        fake.clearAllCompleted()
        fake.clearImageCache()
        fake.clearStrayChunks()
        fake.clearLogs()

        assertTrue("test-1" in fake.canceledIds)
        assertTrue("test-2" in fake.retriedIds)
        assertTrue("test-3" in fake.pausedIds)
        assertTrue("test-4" in fake.resumedIds)
        assertTrue("test-5" in fake.deletedIds)
        assertTrue(fake.clearAllCompletedCalled)
        assertTrue(fake.clearImageCacheCalled)
        assertTrue(fake.clearStrayChunksCalled)
        assertTrue(fake.clearLogsCalled)
    }

    @Test
    fun testDownloadStatusEnum() {
        assertEquals("PENDING", DownloadStatus.PENDING.name)
        assertEquals("DOWNLOADING", DownloadStatus.DOWNLOADING.name)
        assertEquals("COMPLETED", DownloadStatus.COMPLETED.name)
        assertEquals("FAILED", DownloadStatus.FAILED.name)
        assertEquals("PAUSED", DownloadStatus.PAUSED.name)
    }

    @Test
    fun testDownloadDataClass() {
        val download = Download(
            id = "1",
            title = "Test Download",
            url = "http://example.com/video.mp4",
            filePath = "/tmp/test.mp4",
            status = DownloadStatus.PENDING.name,
            progress = 0,
            totalBytes = 1000,
            downloadedBytes = 0,
            createdAt = System.currentTimeMillis()
        )
        assertEquals("1", download.id)
        assertEquals("Test Download", download.title)
        assertEquals(DownloadStatus.PENDING.name, download.status)
        assertEquals(0, download.progress)
        assertEquals(1000, download.totalBytes)
        assertEquals(0, download.downloadedBytes)
    }

    @Test
    fun testDownloadManagerInterfaceHasAllMethods() = runBlocking {
        val fake = FakeDownloadManager()
        fake.cancelDownload("id")
        fake.retryDownload("id")
        fake.pauseDownload("id")
        fake.resumeDownload("id")
        fake.deleteDownload("id")
        fake.clearAllCompleted()
        val cacheResult = fake.clearImageCache()
        val chunksResult = fake.clearStrayChunks()
        val logsResult = fake.clearLogs()
        assertTrue(cacheResult)
        assertEquals(0, chunksResult)
        assertTrue(logsResult)
    }

    @Test
    fun testGetHeadersForUrlReturnsUserAgentAndReferer() {
        val headers = getHeadersForUrlInternal("https://animecix.net/video/123")
        assertEquals("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36", headers["User-Agent"])
        assertEquals("com.kraptor.AnimeciX", headers["X-Requested-With"])
        assertEquals("https://animecix.net/", headers["Referer"])
    }

    @Test
    fun testGetHeadersForUrlSibnetReferer() {
        val headers = getHeadersForUrlInternal("https://video.sibnet.ru/archive/somevideo.mp4")
        assertEquals("https://video.sibnet.ru/", headers["Referer"])
    }

    @Test
    fun testGetHeadersForUrlTauReferer() {
        val headers = getHeadersForUrlInternal("https://tau-video.com/stream.m3u8")
        assertEquals("https://animecix.net/", headers["Referer"])
    }
}
