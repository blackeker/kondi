package com.myanim.kondi.data.download

import java.io.File

interface DownloadStrategy {
    suspend fun download(
        id: String,
        title: String,
        url: String,
        file: File,
        headers: Map<String, String>,
        onProgress: (progress: Int, downloaded: Long, total: Long) -> Unit
    ): Boolean
}
