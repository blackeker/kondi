package com.myanim.kondi.data.download

import java.io.File

class YtDlpDownloadStrategy(private val ytDlpDownloader: YtDlpDownloader) : DownloadStrategy {
    override suspend fun download(
        id: String,
        title: String,
        url: String,
        file: File,
        headers: Map<String, String>,
        onProgress: (progress: Int, downloaded: Long, total: Long) -> Unit
    ): Boolean {
        return ytDlpDownloader.downloadVideo(id, url, file, headers) { progress, downloaded, total ->
            onProgress(progress.toInt(), downloaded, total)
        }
    }
}
