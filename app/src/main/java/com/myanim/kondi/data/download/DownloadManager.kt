package com.myanim.kondi.data.download

import com.myanim.kondi.data.local.Download
import kotlinx.coroutines.flow.StateFlow

interface DownloadManager {
    val downloadsFlow: StateFlow<List<Download>>

    fun cancelDownload(id: String)
    fun retryDownload(id: String)
    fun pauseDownload(id: String)
    fun resumeDownload(id: String)
    fun deleteDownload(id: String)
    fun clearAllCompleted()
    fun clearImageCache(): Boolean
    fun clearStrayChunks(): Int
    fun clearLogs(): Boolean
    fun destroy()
}
