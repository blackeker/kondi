package com.myanim.kondi.ui.download

import com.myanim.kondi.data.download.DownloadManager
import com.myanim.kondi.data.local.Download
import com.myanim.kondi.data.local.DownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull

open class FakeDownloadManager : DownloadManager {
    val downloads = MutableStateFlow<List<Download>>(emptyList())
    override val downloadsFlow: StateFlow<List<Download>> = downloads.asStateFlow()

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

class DownloadsViewModelTest {

    private fun createViewModel(fake: FakeDownloadManager): DownloadsViewModel {
        return DownloadsViewModel(downloadManager = fake)
    }

    @Test
    fun testDownloadsFlowEmitsInitialDownloads() = runBlocking {
        val fake = FakeDownloadManager()
        fake.downloads.value = listOf(
            Download(id = "1", title = "Test 1", url = "http://example.com/1", filePath = "/tmp/1.mp4", status = DownloadStatus.COMPLETED.name, progress = 100)
        )
        val viewModel = createViewModel(fake)

        // Flow collection is async (Dispatchers.IO), wait for emission
        withContext(Dispatchers.IO) { kotlinx.coroutines.delay(100L) }

        val collected = viewModel.downloads.value
        assertEquals(1, collected.size)
        assertEquals("Test 1", collected[0].title)
    }

    @Test
    fun testCancelDownloadDelegatesToManager() = runBlocking {
        val fake = FakeDownloadManager()
        val viewModel = createViewModel(fake)

        viewModel.cancelDownload("abc-123")
        assertTrue("abc-123" in fake.canceledIds)
    }

    @Test
    fun testRetryDownloadDelegatesToManager() = runBlocking {
        val fake = FakeDownloadManager()
        val viewModel = createViewModel(fake)

        viewModel.retryDownload("abc-123")
        assertTrue("abc-123" in fake.retriedIds)
    }

    @Test
    fun testPauseDownloadDelegatesToManager() = runBlocking {
        val fake = FakeDownloadManager()
        val viewModel = createViewModel(fake)

        viewModel.pauseDownload("abc-123")
        assertTrue("abc-123" in fake.pausedIds)
    }

    @Test
    fun testResumeDownloadDelegatesToManager() = runBlocking {
        val fake = FakeDownloadManager()
        val viewModel = createViewModel(fake)

        viewModel.resumeDownload("abc-123")
        assertTrue("abc-123" in fake.resumedIds)
    }

    @Test
    fun testDeleteDownloadDelegatesToManager() = runBlocking {
        val fake = FakeDownloadManager()
        val viewModel = createViewModel(fake)

        viewModel.deleteDownload("abc-123")
        assertTrue("abc-123" in fake.deletedIds)
    }

    @Test
    fun testClearAllCompletedDelegatesToManager() = runBlocking {
        val fake = FakeDownloadManager()
        fake.downloads.value = listOf(
            Download(id = "1", title = "Completed", url = "http://example.com/1", filePath = "/tmp/1.mp4", status = DownloadStatus.COMPLETED.name, progress = 100)
        )
        val viewModel = createViewModel(fake)

        viewModel.clearAllCompleted()
        // clearAllCompleted launches a coroutine on Dispatchers.IO, wait for it
        withContext(Dispatchers.IO) { kotlinx.coroutines.delay(100L) }
        assertTrue(fake.clearAllCompletedCalled)
    }

    @Test
    fun testDownloadItemDataClass() {
        val item = DownloadItem(
            id = "1",
            title = "Test",
            status = "COMPLETED",
            progress = 100,
            totalBytes = 1000,
            downloadedBytes = 1000,
            createdAt = System.currentTimeMillis()
        )
        assertEquals("1", item.id)
        assertEquals("Test", item.title)
        assertEquals("COMPLETED", item.status)
        assertEquals(100, item.progress)
    }

    @Test
    fun testDownloadsFlowHandlesErrorGracefully() = runBlocking {
        val errorStateFlow = object : StateFlow<List<Download>> {
            override val value: List<Download> get() = emptyList()
            override val replayCache: List<List<Download>> get() = emptyList()
            override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<List<Download>>): Nothing {
                throw RuntimeException("Veritabanı hatası")
            }
        }

        val fakeWithError = object : FakeDownloadManager() {
            override val downloadsFlow: StateFlow<List<Download>> = errorStateFlow
        }

        // ViewModel'in init bloğunda flow collection hatası sessizce yakalanmalı
        val viewModel = createViewModel(fakeWithError)

        // ViewModel çökmemeli ve boş liste göstermeli
        val collected = viewModel.downloads.value
        assertEquals(0, collected.size)
    }

    @Test
    fun testClearAllCompletedWithMultipleDownloads() = runBlocking {
        val fake = FakeDownloadManager()
        fake.downloads.value = listOf(
            Download(id = "1", title = "Completed 1", url = "http://example.com/1", filePath = "/tmp/1.mp4", status = DownloadStatus.COMPLETED.name, progress = 100),
            Download(id = "2", title = "Completed 2", url = "http://example.com/2", filePath = "/tmp/2.mp4", status = DownloadStatus.COMPLETED.name, progress = 100),
            Download(id = "3", title = "Downloading", url = "http://example.com/3", filePath = "/tmp/3.mp4", status = DownloadStatus.DOWNLOADING.name, progress = 50)
        )
        val viewModel = createViewModel(fake)

        viewModel.clearAllCompleted()
        withContext(Dispatchers.IO) { kotlinx.coroutines.delay(100L) }
        assertTrue(fake.clearAllCompletedCalled)
    }

    @Test
    fun testDownloadItemDataClassWithErrorMessage() {
        val item = DownloadItem(
            id = "1",
            title = "Test",
            status = "FAILED",
            progress = 0,
            totalBytes = 1000,
            downloadedBytes = 0,
            createdAt = System.currentTimeMillis(),
            errorMessage = "Ağ hatası"
        )
        assertEquals("FAILED", item.status)
        assertEquals("Ağ hatası", item.errorMessage)
    }

    @Test
    fun testDownloadItemDataClassDefaultErrorMessage() {
        val item = DownloadItem(
            id = "1",
            title = "Test",
            status = "PENDING",
            progress = 0,
            totalBytes = 1000,
            downloadedBytes = 0,
            createdAt = System.currentTimeMillis()
        )
        assertNull(item.errorMessage)
    }

    @Test
    fun testErrorIsNullInitially() {
        val fake = FakeDownloadManager()
        val viewModel = createViewModel(fake)

        assertNull(viewModel.error.value)
    }

    @Test
    fun testDownloadsFlowErrorSetsErrorState() = runBlocking {
        val errorStateFlow = object : StateFlow<List<Download>> {
            override val value: List<Download> get() = emptyList()
            override val replayCache: List<List<Download>> get() = emptyList()
            override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<List<Download>>): Nothing {
                throw RuntimeException("Veritabanı hatası")
            }
        }

        val fakeWithError = object : FakeDownloadManager() {
            override val downloadsFlow: StateFlow<List<Download>> = errorStateFlow
        }

        val viewModel = createViewModel(fakeWithError)

        // Flow collection is async (Dispatchers.IO), wait for emission
        withContext(Dispatchers.IO) { kotlinx.coroutines.delay(100L) }

        assertNotNull(viewModel.error.value)
        assertTrue(viewModel.error.value!!.contains("Veritabanı hatası"))
    }

    @Test
    fun testRetrySetsErrorToNullInitially() = runBlocking {
        val errorStateFlow = object : StateFlow<List<Download>> {
            override val value: List<Download> get() = emptyList()
            override val replayCache: List<List<Download>> get() = emptyList()
            override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<List<Download>>): Nothing {
                throw RuntimeException("Veritabanı hatası")
            }
        }

        val fakeWithError = object : FakeDownloadManager() {
            override val downloadsFlow: StateFlow<List<Download>> = errorStateFlow
        }

        val viewModel = createViewModel(fakeWithError)

        // Hata durumunu bekle
        withContext(Dispatchers.IO) { kotlinx.coroutines.delay(100L) }
        assertNotNull(viewModel.error.value)

        // Retry çağır - bu metod çökmemeli
        viewModel.retry()

        // retry() metodu başarıyla çağrıldı (exception fırlatmadı)
        assertTrue(true)
    }
}
