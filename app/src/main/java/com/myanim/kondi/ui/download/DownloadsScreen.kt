package com.myanim.kondi.ui.download

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myanim.kondi.data.download.VideoDownloadManager
import com.myanim.kondi.ui.common.*
import com.myanim.kondi.ui.theme.*

/**
 * İndirilenler ekranı - sade ve klasik
 */
@Composable
fun DownloadsTab(
    onItemClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: DownloadsViewModel = viewModel {
        DownloadsViewModel(
            VideoDownloadManager.getInstance(context.applicationContext)
        )
    }
    val downloadsState = viewModel.downloads.collectAsStateWithLifecycle()
    val downloads: List<DownloadItem> = downloadsState.value
    val errorState = viewModel.error.collectAsStateWithLifecycle()
    val error: String? = errorState.value

    Column(modifier = modifier.fillMaxSize()) {
        SectionHeader(
            title = "İndirilenler",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )

        if (error != null) {
            ErrorState(
                message = error,
                onRetry = { viewModel.retry() },
                modifier = Modifier.fillMaxSize()
            )
        } else if (downloads.isEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                item {
                    EmptyState(
                        icon = Icons.Default.DownloadDone,
                        title = "Henüz indirme yok",
                        subtitle = "İndirdiğiniz içerikler burada görünecek",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(
                    items = downloads,
                    key = { it.id }
                ) { download ->
                    DownloadItemRow(
                        download = download,
                        onRetry = { viewModel.retryDownload(download.id) },
                        onCancel = { viewModel.cancelDownload(download.id) },
                        onPause = { viewModel.pauseDownload(download.id) },
                        onResume = { viewModel.resumeDownload(download.id) },
                        onDelete = { viewModel.deleteDownload(download.id) },
                        onItemClick = onItemClick
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Bottom action bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .background(SurfaceCard)
            ) {
                val completedCount = downloads.count { it.status == "COMPLETED" }
                if (completedCount > 0) {
                    SimpleOutlinedButton(
                        text = "Tamamlananları Temizle ($completedCount)",
                        onClick = { viewModel.clearAllCompleted() },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }
}

/**
 * Tek bir indirme öğesi satırı
 */
@Composable
internal fun DownloadItemRow(
    download: DownloadItem,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onItemClick: (() -> Unit)? = null
) {
    val statusColor = when (download.status) {
        "COMPLETED" -> Primary
        "DOWNLOADING" -> Primary
        "PENDING" -> Secondary
        "PAUSED" -> TextDisabled
        "FAILED" -> Error
        else -> TextDisabled
    }

    val statusText = when (download.status) {
        "COMPLETED" -> "Tamamlandı"
        "DOWNLOADING" -> "İndiriliyor (%${download.progress})"
        "PENDING" -> "Bekliyor"
        "PAUSED" -> "Durduruldu"
        "FAILED" -> "Hata: ${download.errorMessage ?: "Bilinmeyen hata"}"
        else -> download.status
    }

    SimpleCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onItemClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = download.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.wrapContentSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (download.status) {
                        "DOWNLOADING" -> {
                            SimpleOutlinedButton(
                                text = "Durdur",
                                onClick = onPause,
                                modifier = Modifier.size(80.dp, 32.dp)
                            )
                            SimpleOutlinedButton(
                                text = "İptal",
                                onClick = onCancel,
                                modifier = Modifier.size(80.dp, 32.dp)
                            )
                        }
                        "PENDING" -> {
                            SimpleOutlinedButton(
                                text = "İptal",
                                onClick = onCancel,
                                modifier = Modifier.size(80.dp, 32.dp)
                            )
                        }
                        "PAUSED" -> {
                            SimpleOutlinedButton(
                                text = "Devam",
                                onClick = onResume,
                                modifier = Modifier.size(80.dp, 32.dp)
                            )
                            SimpleOutlinedButton(
                                text = "İptal",
                                onClick = onCancel,
                                modifier = Modifier.size(80.dp, 32.dp)
                            )
                        }
                        "FAILED" -> {
                            SimpleOutlinedButton(
                                text = "Yeniden Dene",
                                onClick = onRetry,
                                modifier = Modifier.size(100.dp, 32.dp)
                            )
                            SimpleOutlinedButton(
                                text = "Sil",
                                onClick = onDelete,
                                modifier = Modifier.size(60.dp, 32.dp)
                            )
                        }
                        "COMPLETED" -> {
                            SimpleOutlinedButton(
                                text = "Sil",
                                onClick = onDelete,
                                modifier = Modifier.size(60.dp, 32.dp)
                            )
                        }
                    }
                }
            }

            if (download.status == "DOWNLOADING" || download.status == "PENDING") {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { download.progress / 100f },
                    color = Primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "DownloadsTab - Empty State")
@Composable
fun PreviewDownloadsTabEmpty() {
    KondiTheme {
        DownloadsTab(
            onItemClick = {}
        )
    }
}

@Preview(showBackground = true, name = "DownloadsTab - With Downloads")
@Composable
fun PreviewDownloadsTabWithDownloads() {
    KondiTheme {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            DownloadItemRow(
                download = DownloadItem(
                    id = "1",
                    title = "Örnek Anime Bölüm 1",
                    status = "DOWNLOADING",
                    progress = 45,
                    totalBytes = 500000000,
                    downloadedBytes = 225000000,
                    createdAt = System.currentTimeMillis()
                ),
                onRetry = {},
                onCancel = {},
                onPause = {},
                onResume = {},
                onDelete = {},
                onItemClick = {}
            )
            Spacer(modifier = Modifier.height(16.dp))
            DownloadItemRow(
                download = DownloadItem(
                    id = "2",
                    title = "Örnek Anime Bölüm 2",
                    status = "COMPLETED",
                    progress = 100,
                    totalBytes = 300000000,
                    downloadedBytes = 300000000,
                    createdAt = System.currentTimeMillis()
                ),
                onRetry = {},
                onCancel = {},
                onPause = {},
                onResume = {},
                onDelete = {},
                onItemClick = {}
            )
        }
    }
}

/**
 * Depolama yönetimi ekranı - sade ve klasik
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageTab(
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val downloadManager = VideoDownloadManager.getInstance(context)

    Column(modifier = modifier.fillMaxSize()) {
        if (onClose != null) {
            TopAppBar(
                title = { Text("Depolama Yönetimi") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Geri"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceCard)
            )
        }
        SectionHeader(
            title = "Depolama Yönetimi",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )

        SimpleCard(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text(
                    text = "Önbellek",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Uygulama önbelleğini temizleyerek depolama alanı açabilirsiniz.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                SimpleOutlinedButton(
                    text = "Önbelleği Temizle",
                    onClick = {
                        downloadManager.clearImageCache()
                        downloadManager.clearStrayChunks()
                        downloadManager.clearLogs()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SimpleCard(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text(
                    text = "İndirilen Dosyalar",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "İndirilen içeriklerin yönetimi burada yapılacak.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "StorageTab")
@Composable
fun PreviewStorageTab() {
    KondiTheme {
        StorageTab(
            onClose = {}
        )
    }
}
