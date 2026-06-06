package com.myanim.kondi.data.download

import okhttp3.OkHttpClient
import java.io.File

class HlsDownloadStrategy(private val context: android.content.Context, private val client: OkHttpClient) : DownloadStrategy {
    override suspend fun download(
        id: String,
        title: String,
        url: String,
        file: File,
        headers: Map<String, String>,
        onProgress: (progress: Int, downloaded: Long, total: Long) -> Unit
    ): Boolean {
        val hlsDownloader = HlsDownloader(context, client)
        return hlsDownloader.downloadHls(url, file, headers, onProgress)
    }
}
