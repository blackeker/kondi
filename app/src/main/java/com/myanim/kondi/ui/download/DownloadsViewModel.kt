package com.myanim.kondi.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myanim.kondi.data.download.DownloadManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DownloadsViewModel(
    private val downloadManager: DownloadManager
) : ViewModel() {

    // viewModelScope kullanılıyor: ViewModel yok edildiğinde tüm işler otomatik iptal edilir.
    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Aktif akış dinleyicisi. retry() çağrıldığında önceki dinleyici iptal edilir. */
    private var collectJob: Job? = null

    init {
        startCollecting()
    }

    private fun startCollecting() {
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            try {
                downloadManager.downloadsFlow.collect { downloads ->
                    _downloads.value = downloads.map { it.toDownloadItem() }
                }
            } catch (e: CancellationException) {
                // İptal bir hata değildir; ViewModel yok edildiğinde hata durumu gösterilmemeli.
                throw e
            } catch (e: Exception) {
                _error.value = "İndirilenler yüklenemedi: ${e.message}"
            }
        }
    }

    fun cancelDownload(id: String) {
        downloadManager.cancelDownload(id)
    }

    fun retryDownload(id: String) {
        downloadManager.retryDownload(id)
    }

    fun pauseDownload(id: String) {
        downloadManager.pauseDownload(id)
    }

    fun resumeDownload(id: String) {
        downloadManager.resumeDownload(id)
    }

    fun deleteDownload(id: String) {
        downloadManager.deleteDownload(id)
    }

    fun clearAllCompleted() {
        downloadManager.clearAllCompleted()
    }

    fun retry() {
        _error.value = null
        startCollecting()
    }

    override fun onCleared() {
        super.onCleared()
        collectJob?.cancel()
        downloadManager.destroy()
    }
}

private fun com.myanim.kondi.data.local.Download.toDownloadItem(): DownloadItem {
    return DownloadItem(
        id = id,
        title = title,
        status = status,
        progress = progress,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        createdAt = createdAt,
        errorMessage = errorMessage,
        filePath = filePath
    )
}

data class DownloadItem(
    val id: String,
    val title: String,
    val status: String,
    val progress: Int,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val createdAt: Long,
    val errorMessage: String? = null,
    /** İndirilen dosyanın yolu (content:// URI veya mutlak dosya yolu). */
    val filePath: String = ""
)
