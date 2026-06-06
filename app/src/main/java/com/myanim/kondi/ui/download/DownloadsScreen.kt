package com.myanim.kondi.ui.download

// Removed animation imports
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myanim.kondi.data.local.Download
import com.myanim.kondi.data.local.DownloadStatus
import com.myanim.kondi.data.download.VideoDownloadManager
import com.myanim.kondi.ui.common.KondiBackground
import java.io.File

enum class DownloadFilter { ALL, COMPLETED, DOWNLOADING, FAILED }
enum class DownloadSort { DATE_DESC, DATE_ASC, NAME_ASC, NAME_DESC, SIZE_DESC }

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(
    sourceFilter: String? = null,
    isEmbedded: Boolean = false,
    onBackClick: () -> Unit = {},
    onPlayClick: (Download) -> Unit = {}
) {
    val context = LocalContext.current
    val downloadManager = remember { VideoDownloadManager.getInstance(context) }
    val downloads by downloadManager.downloadsFlow.collectAsState()
    
    val favorites by remember(context) { 
        com.myanim.kondi.data.local.KondiDatabase.getDatabase(context).favoriteDao().getAllFavorites() 
    }.collectAsState(initial = emptyList())
    
    val watchHistory by remember(context) { 
        com.myanim.kondi.data.local.KondiDatabase.getDatabase(context).watchHistoryDao().getAllHistory() 
    }.collectAsState(initial = emptyList())
    
    val posterMap = remember(favorites, watchHistory) {
        val map = mutableMapOf<String, String>()
        watchHistory.forEach { history ->
            val cleanTitle = history.title.substringBefore(" - ").trim().lowercase()
            history.posterUrl?.let { map[cleanTitle] = it }
        }
        favorites.forEach { fav ->
            fav.posterUrl?.let { map[fav.title.trim().lowercase()] = it }
        }
        map
    }
    
    fun getPosterForAnime(animeName: String): String? {
        val cleanName = animeName.trim().lowercase()
        val poster = posterMap[cleanName]
        if (poster != null) return poster
        
        val bestMatch = posterMap.keys.find { key ->
            cleanName.contains(key) || key.contains(cleanName)
        }
        if (bestMatch != null) return posterMap[bestMatch]
        return null
    }
    
    LaunchedEffect(Unit) {
        downloadManager.cleanupDeletedFiles()
    }
    
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Devam Edenler", "Tamamlananlar")
    val currentFilter = if (selectedTabIndex == 0) DownloadFilter.DOWNLOADING else DownloadFilter.COMPLETED
    
    var selectedSort by remember { mutableStateOf(DownloadSort.DATE_DESC) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var downloadToShowError by remember { mutableStateOf<Download?>(null) }

    val filteredDownloads = remember(downloads, currentFilter, selectedSort, sourceFilter) {
        downloads
            .filter { download ->
                (sourceFilter == null || download.source == sourceFilter) &&
                when (currentFilter) {
                    DownloadFilter.COMPLETED -> download.status == DownloadStatus.COMPLETED.name
                    DownloadFilter.DOWNLOADING -> download.status in listOf(DownloadStatus.DOWNLOADING.name, DownloadStatus.PENDING.name, DownloadStatus.PAUSED.name, DownloadStatus.FAILED.name)
                    else -> true
                }
            }
            .sortedWith(when (selectedSort) {
                DownloadSort.DATE_DESC -> compareByDescending { it.createdAt }
                DownloadSort.DATE_ASC -> compareBy { it.createdAt }
                DownloadSort.NAME_ASC -> compareBy { it.title }
                DownloadSort.NAME_DESC -> compareByDescending { it.title }
                DownloadSort.SIZE_DESC -> compareByDescending { it.totalBytes }
            })
    }

    val stats = remember(downloads) {
        DownloadStats(
            total = downloads.size,
            completed = downloads.count { it.status == DownloadStatus.COMPLETED.name },
            downloading = downloads.count { it.status == DownloadStatus.DOWNLOADING.name },
            failed = downloads.count { it.status == DownloadStatus.FAILED.name },
            totalSize = downloads.filter { it.status == DownloadStatus.COMPLETED.name }.sumOf { it.totalBytes }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Tüm İndirmeleri Sil") },
            text = { Text("${stats.completed} tamamlanmış indirmeyi silmek istediğinizden emin misiniz?") },
            confirmButton = {
                Button(onClick = {
                    downloads.filter { it.status == DownloadStatus.COMPLETED.name }.forEach { downloadManager.deleteDownload(it.id) }
                    showDeleteAllDialog = false
                }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAllDialog = false }) { Text("İptal") } }
        )
    }
    
    downloadToShowError?.let { download ->
        DownloadErrorDialog(
            download = download,
            onDismiss = { downloadToShowError = null }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isEmbedded) KondiBackground()

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (!isEmbedded) {
                    TopAppBar(
                        title = { Column { Text("İndirilenler", fontWeight = FontWeight.Bold); Text("${filteredDownloads.size} dosya", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f)) } },
                        actions = {
                            if (stats.completed > 0) IconButton(onClick = { showDeleteAllDialog = true }) { Icon(Icons.Default.DeleteSweep, "Tümünü Sil") }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        modifier = Modifier.background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (downloads.isEmpty()) {
                    EmptyDownloadsState()
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        StatsCard(stats = stats)
                        TabRow(
                            selectedTabIndex = selectedTabIndex,
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                    color = Color.White
                                )
                            }
                        ) {
                            tabs.forEachIndexed { index, title ->
                                val count = if (index == 0) downloads.count { it.status in listOf(DownloadStatus.DOWNLOADING.name, DownloadStatus.PENDING.name, DownloadStatus.PAUSED.name, DownloadStatus.FAILED.name) } else stats.completed
                                Tab(
                                    selected = selectedTabIndex == index,
                                    onClick = { selectedTabIndex = index },
                                    text = { Text("$title ($count)") }
                                )
                            }
                        }
                        
                        val hasActiveOrPending = remember(downloads) {
                            downloads.any { it.status == DownloadStatus.DOWNLOADING.name || it.status == DownloadStatus.PENDING.name }
                        }
                        val hasPausedOrFailed = remember(downloads) {
                            downloads.any { it.status == DownloadStatus.PAUSED.name || it.status == DownloadStatus.FAILED.name }
                        }

                        if (downloads.any { it.status != DownloadStatus.COMPLETED.name }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { downloadManager.resumeAllDownloads() },
                                    modifier = Modifier.weight(1f),
                                    enabled = hasPausedOrFailed,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color.White.copy(alpha = 0.15f),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Tümünü Başlat", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                
                                FilledTonalButton(
                                    onClick = { downloadManager.pauseAllDownloads() },
                                    modifier = Modifier.weight(1f),
                                    enabled = hasActiveOrPending,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color.White.copy(alpha = 0.15f),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Default.Pause, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Tümünü Durdur", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                        
                        if (filteredDownloads.isEmpty()) {
                            EmptyFilterState(filter = currentFilter)
                        } else {
                            if (currentFilter == DownloadFilter.COMPLETED) {
                                val groupedCompleted = remember(filteredDownloads) {
                                    filteredDownloads.groupBy { download ->
                                        parseDownloadTitle(download.title).animeName
                                    }
                                }
                                
                                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    groupedCompleted.forEach { (animeName, episodes) ->
                                        item(key = animeName) {
                                            AnimeGroupCard(
                                                animeName = animeName,
                                                posterUrl = getPosterForAnime(animeName),
                                                episodes = episodes,
                                                onPlayClick = { onPlayClick(it) },
                                                onDeleteClick = { downloadManager.deleteDownload(it.id) }
                                            )
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(filteredDownloads, key = { it.id }) { download ->
                                        ImprovedDownloadItem(
                                            download = download,
                                            queuePosition = if (download.status == DownloadStatus.PENDING.name) downloads.filter { it.status == DownloadStatus.PENDING.name }.sortedBy { it.queueOrder }.indexOf(download) + 1 else 0,
                                            onDelete = { downloadManager.deleteDownload(it.id) },
                                            onOpen = { onPlayClick(it) },
                                            onCancel = { downloadManager.cancelDownload(it.id) },
                                            onPause = { downloadManager.pauseDownload(it.id) },
                                            onResume = { downloadManager.resumeDownload(it.id) },
                                            onRetry = { downloadManager.retryDownload(it.id) },
                                            onMoveUp = { targetDownload ->
                                                downloadManager.moveQueueItemUp(targetDownload.id)
                                            },
                                            onShowError = { downloadToShowError = it }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}