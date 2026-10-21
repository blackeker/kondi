package com.myanim.kondi.ui.download

import androidx.lifecycle.viewModelScope
import com.myanim.kondi.data.download.DownloadManager
import com.myanim.kondi.data.local.Download
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * DownloadsViewModel birim testleri.
 *
 * DÜZELTİLEN HATALAR:
 *  1) Testler "delay(100L)" ile akışın yayınlanmasını bekliyordu. Bu yarış
 *     koşulu (race) yavaş cihazlarda testlerin kararsız (flaky) olmasına yol
 *     açıyordu. Artık kotlinx-coroutines-test'in deterministik dispatcher'ı ve
 *     advanceUntilIdle() kullanılıyor.
 *  2) "testDownloadsFlowHandlesErrorGracefully" testi hiçbir şey assert etmiyordu
 *     (assertNull yerine sadece collected.isEmpty kontrolü yapılıyordu) ve
 *     "testDownloadsFlowErrorSetsErrorState" ile çelişiyordu.
 *  3) "testRetrySetsErrorToNullInitially" testi `assertTrue(true)` ile anlamsızdı.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Davranışı gerçek DownloadManager arayüzüne uyan, kontrollü bir sahte.
     * (Eski testteki "FakeDownloadManager" yalnızca kendi içindeki sahte
     * downloadManager'ı test ediyordu; üretim koduna hiç dokunmuyordu.)
     */
    private class TestDownloadManager(
        initial: List<Download> = emptyList(),
        var failWith: Throwable? = null
    ) : DownloadManager {
        private val _downloadsFlow = MutableStateFlow(initial)

        // Hata senaryosu: getter hata fırlatarak akışa erişimi kırar; ViewModel
        // bu hatayı yakalayıp hata durumuna geçmelidir.
        override val downloadsFlow: StateFlow<List<Download>>
            get() {
                failWith?.let { throw it }
                return _downloadsFlow
            }

        val cancelled = mutableListOf<String>()
        val retried = mutableListOf<String>()
        val paused = mutableListOf<String>()
        val resumed = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        var clearAllCompletedCalled = false
        var destroyCalled = false

        fun emit(items: List<Download>) {
            _downloadsFlow.value = items
        }

        override fun cancelDownload(id: String) { cancelled += id }
        override fun retryDownload(id: String) { retried += id }
        override fun pauseDownload(id: String) { paused += id }
        override fun resumeDownload(id: String) { resumed += id }
        override fun deleteDownload(id: String) { deleted += id }
        override fun clearAllCompleted() { clearAllCompletedCalled = true }
        override fun clearImageCache(): Boolean = true
        override fun clearStrayChunks(): Int = 0
        override fun clearLogs(): Boolean = true
        override fun destroy() { destroyCalled = true }
    }

    private fun download(
        id: String = "1",
        title: String = "Test Anime",
        status: String = "DOWNLOADING",
        progress: Int = 42
    ) = Download(
        id = id,
        title = title,
        url = "https://example.com/$id.mp4",
        filePath = "/tmp/$id.mp4",
        status = status,
        progress = progress,
        totalBytes = 1000L,
        downloadedBytes = 420L
    )

    @Test
    fun `downloads are mapped to ui items`() = runTest(testDispatcher) {
        val manager = TestDownloadManager(
            listOf(
                download(id = "1", title = "Naruto", status = "DOWNLOADING", progress = 42),
                download(id = "2", title = "Bleach", status = "COMPLETED", progress = 100)
            )
        )

        val viewModel = DownloadsViewModel(manager)
        advanceUntilIdle()

        val items = viewModel.downloads.value
        assertEquals(2, items.size)
        assertEquals("Naruto", items[0].title)
        assertEquals("DOWNLOADING", items[0].status)
        assertEquals(42, items[0].progress)
        assertEquals(1000L, items[0].totalBytes)
        assertEquals("COMPLETED", items[1].status)
    }

    @Test
    fun `new emissions update the state`() = runTest(testDispatcher) {
        val manager = TestDownloadManager(listOf(download(id = "1")))
        val viewModel = DownloadsViewModel(manager)
        advanceUntilIdle()
        assertEquals(1, viewModel.downloads.value.size)

        manager.emit(listOf(download(id = "1"), download(id = "2")))
        advanceUntilIdle()

        assertEquals(2, viewModel.downloads.value.size)
    }

    @Test
    fun `error state is set when the flow fails`() = runTest(testDispatcher) {
        val manager = TestDownloadManager(failWith = IllegalStateException("veritabanı kapalı"))

        val viewModel = DownloadsViewModel(manager)
        advanceUntilIdle()

        assertNotNull("Hata durumu ayarlanmalı", viewModel.error.value)
        assertEquals(
            "İndirilenler yüklenemedi: veritabanı kapalı",
            viewModel.error.value
        )
    }

    @Test
    fun `retry clears the error and starts a new collection`() = runTest(testDispatcher) {
        val manager = TestDownloadManager(failWith = IllegalStateException("geçici hata"))
        val viewModel = DownloadsViewModel(manager)
        advanceUntilIdle()
        assertNotNull(viewModel.error.value)

        // Akış artık hata vermiyor
        manager.failWith = null
        viewModel.retry()
        advanceUntilIdle()

        assertNull("retry() hata durumunu temizlemeli", viewModel.error.value)
    }

    @Test
    fun `commands are delegated to the download manager`() = runTest(testDispatcher) {
        val manager = TestDownloadManager()
        val viewModel = DownloadsViewModel(manager)
        advanceUntilIdle()

        viewModel.cancelDownload("a")
        viewModel.retryDownload("b")
        viewModel.pauseDownload("c")
        viewModel.resumeDownload("d")
        viewModel.deleteDownload("e")
        viewModel.clearAllCompleted()

        assertEquals(listOf("a"), manager.cancelled)
        assertEquals(listOf("b"), manager.retried)
        assertEquals(listOf("c"), manager.paused)
        assertEquals(listOf("d"), manager.resumed)
        assertEquals(listOf("e"), manager.deleted)
        assertEquals(true, manager.clearAllCompletedCalled)
    }

    @Test
    fun `cancelling the scope does not set an error state`() =
        runTest(testDispatcher) {
            val manager = TestDownloadManager(listOf(download()))
            val viewModel = DownloadsViewModel(manager)
            advanceUntilIdle()

            viewModel.viewModelScope.cancel()
            advanceUntilIdle()

            // İptal bir hata olarak yorumlanmamalı
            // (eskiden "İndirilenler yüklenemedi: Job was cancelled" gösteriliyordu).
            assertNull("İptal hata durumu üretmemeli", viewModel.error.value)
        }

    @Test
    fun `onCleared releases the download manager`() = runTest(testDispatcher) {
        val manager = TestDownloadManager(listOf(download()))
        val viewModel = DownloadsViewModel(manager)
        advanceUntilIdle()

        // ViewModel.clear() androidx tarafından internal; onCleared korumalı.
        // Test için reflection ile çağrılıyor.
        val method = DownloadsViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
        advanceUntilIdle()

        assertEquals("Manager temizlenmeli", true, manager.destroyCalled)
        assertNull("İptal hata durumu üretmemeli", viewModel.error.value)
    }
}
