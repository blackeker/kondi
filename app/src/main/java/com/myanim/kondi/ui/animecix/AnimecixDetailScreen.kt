package com.myanim.kondi.ui.animecix

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
// Relocated imports handled by common.* below.
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import com.myanim.kondi.data.animecix.*
import com.myanim.kondi.data.download.VideoDownloadManager
import com.myanim.kondi.ui.common.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AnimecixDetailScreen(
    animeId: Int,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onBackClick: () -> Unit,
    onVideoClick: (String, Int, Int, Int, String) -> Unit,
    viewModel: AnimecixDetailViewModel = viewModel()
) {
    val anime by viewModel.anime.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isAscending by viewModel.isAscending.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()

    var isSearchActive by remember { mutableStateOf(false) }

    var selectedEpisodeTitle by remember { mutableStateOf("") }
    var showSourceDialog by remember { mutableStateOf(false) }
    var showConverterDialog by remember { mutableStateOf(false) }
    val sourceSizes = remember { mutableStateMapOf<String, Long>() }
    val scope = rememberCoroutineScope()
    
    var selectedVideos by remember { mutableStateOf(setOf<AnimecixVideo>()) }
    val selectionMode = selectedVideos.isNotEmpty()
    
    var activeVideoForSource by remember { mutableStateOf<AnimecixVideo?>(null) }

    val context = LocalContext.current
    val downloadManager = remember { VideoDownloadManager.getInstance(context) }
    val downloads by downloadManager.downloadsFlow.collectAsStateWithLifecycle(emptyList())

    LaunchedEffect(animeId) { 
        viewModel.loadAnimeDetails(context, animeId)
        viewModel.checkFavoriteStatus(context, animeId)
    }

    val sourcesList = remember(sources, showSourceDialog, activeVideoForSource, anime) {
        if (showSourceDialog && activeVideoForSource != null) {
            val selectedVideo = anime?.videos?.find { 
                it.episodeId == activeVideoForSource?.episodeId ||
                (it.seasonNumber == activeVideoForSource?.seasonNumber && it.episodeNumber == activeVideoForSource?.episodeNumber)
            }
            selectedVideo?.videos?.map { AnimecixSource(name = it.name ?: "Kaynak", url = it.url ?: "", type = "embed") } ?: sources
        } else emptyList()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        KondiBackground()

        SourceSelectionBottomSheet(
            show = showSourceDialog,
            onDismiss = { 
                showSourceDialog = false 
                activeVideoForSource = null
            },
            title = selectedEpisodeTitle,
            sourcesList = sourcesList,
            sourceSizes = sourceSizes,
            onResolveSize = { url ->
                scope.launch {
                    val resolved = viewModel.resolveUrl(url)
                    if (resolved != null) sourceSizes[url] = downloadManager.getFileSize(resolved)
                }
            },
            onPlayClick = { url ->
                scope.launch {
                    com.myanim.kondi.data.prefs.UserPreferencesManager.getInstance(context).extractHostAndSave(url)
                    val resolvedUrl = viewModel.resolveUrl(url)
                    if (resolvedUrl != null) {
                        val seasonNum = activeVideoForSource?.seasonNumber ?: 1
                        val episodeNum = activeVideoForSource?.episodeNumber ?: 0
                        
                        onVideoClick(resolvedUrl, animeId, seasonNum, episodeNum, anime?.title ?: "Anime")
                    }
                    showSourceDialog = false
                    activeVideoForSource = null
                }
            },
            onDownloadClick = { source ->
                scope.launch {
                    com.myanim.kondi.data.prefs.UserPreferencesManager.getInstance(context).extractHostAndSave(source.url)
                    
                    val seasonNum = activeVideoForSource?.seasonNumber ?: 1
                    val episodeNum = activeVideoForSource?.episodeNumber ?: 0
                    
                    val fileName = com.myanim.kondi.data.download.DownloadUtils.createAnimecixFileName(
                        anime?.title ?: "Anime",
                        seasonNum,
                        episodeNum
                    )
                    
                    val existingDownload = downloadManager.downloadsFlow.value.find { it.title == fileName }
                    if (existingDownload != null && existingDownload.status == com.myanim.kondi.data.local.DownloadStatus.COMPLETED.name) {
                        Toast.makeText(context, "Bu bölüm zaten indirilmiş!", Toast.LENGTH_LONG).show()
                    } else {
                        val rawUrl = source.url
                        val resolvedUrl = viewModel.resolveUrl(rawUrl)
                        if (resolvedUrl != null && resolvedUrl.startsWith("http")) {
                            val lowerRawUrl = rawUrl.lowercase()
                            val urlToDownload = if (
                                lowerRawUrl.contains("sibnet") || 
                                lowerRawUrl.contains("ok.ru") || 
                                lowerRawUrl.contains("odnoklassniki") || 
                                lowerRawUrl.contains("streamtape") || 
                                lowerRawUrl.contains("voe") || 
                                lowerRawUrl.contains("uqload") || 
                                lowerRawUrl.contains("dood")
                            ) {
                                rawUrl // Pass original URL for yt-dlp to handle
                            } else {
                                resolvedUrl // Pass the resolved direct URL
                            }

                            downloadManager.startDownload(
                                title = fileName,
                                url = urlToDownload,
                                source = "ANIMECIX",
                                headers = mapOf("Referer" to "https://animecix.net"),
                                forceDisplayName = true
                            )
                            Toast.makeText(context, "İndirme başlatıldı", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Kaynak çözümlenemedi", Toast.LENGTH_SHORT).show()
                        }
                    }

                    showSourceDialog = false
                    activeVideoForSource = null
                }
            }
        )

        if (showConverterDialog) {
            SibnetConverterDialog(
                onDismiss = { showConverterDialog = false },
                onPlay = { url, title -> onVideoClick(url, animeId, 1, 0, title); showConverterDialog = false },
                onDownload = { url, title -> downloadManager.startDownload(title, url, source = "SIBNET", forceDisplayName = true); showConverterDialog = false },
                viewModel = viewModel
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GlassyTopAppBar(
                    title = {
                        if (isSearchActive) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                placeholder = { Text("Bölüm ara...", color = Color.White.copy(alpha = 0.5f)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    cursorColor = Color.White,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true
                            )
                        } else {
                            Text(anime?.title ?: "Yükleniyor...", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isSearchActive) {
                                isSearchActive = false
                                viewModel.updateSearchQuery("")
                            } else {
                                onBackClick()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White)
                        }
                    },
                    actions = {
                        if (isSearchActive) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Temizle", tint = Color.White)
                            }
                        } else {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Ara", tint = Color.White)
                            }
                            IconButton(onClick = { anime?.let { viewModel.toggleFavorite(context, it) } }) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favori",
                                    tint = if (isFavorite) Color.Red else Color.White
                                )
                            }
                            IconButton(onClick = { viewModel.toggleSortOrder() }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sırala", tint = Color.White)
                            }
                            IconButton(onClick = { showConverterDialog = true }) {
                                Icon(Icons.Default.Link, contentDescription = "Sibnet", tint = Color.White)
                            }
                        }
                    }
                )
            }
        ) { padding ->
            if (errorMessage != null && anime == null) {
                ErrorState(message = errorMessage!!, onRetry = { viewModel.retry(context, animeId) })
            } else if (isLoading && anime == null) {
                LoadingPlaceholder(padding)
            } else {
                anime?.let { details ->
                    val episodes = details.videos ?: emptyList()
                    val filteredEpisodes = remember(episodes, searchQuery) {
                        if (searchQuery.isBlank()) {
                            episodes
                        } else {
                            episodes.filter {
                                it.name?.contains(searchQuery, ignoreCase = true) == true ||
                                it.episodeNumber?.toString()?.contains(searchQuery) == true
                            }
                        }
                    }

                    val sortedEpisodes = remember(filteredEpisodes, isAscending) {
                        if (isAscending) {
                            filteredEpisodes.sortedWith(compareBy({ it.seasonNumber ?: 1 }, { it.episodeNumber ?: 0 }))
                        } else {
                            filteredEpisodes.sortedWith(compareByDescending<AnimecixVideo> { it.seasonNumber ?: 1 }.thenByDescending { it.episodeNumber ?: 0 })
                        }
                    }

                    // Pre-calculate downloads map to avoid O(N) lookup inside each EpisodeItem
                    val downloadsMap = remember(downloads, details.title) {
                        downloads.filter { it.source == "ANIMECIX" }.associateBy { it.title }
                    }

                    val displaySeasons = remember(sortedEpisodes, isAscending) {
                        val episodesBySeason = sortedEpisodes.groupBy { it.seasonNumber ?: 1 }
                        if (isAscending) episodesBySeason.toSortedMap() else episodesBySeason.toSortedMap(reverseOrder())
                    }

                    Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item { 
                                    DetailHeader(
                                        details = details,
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedContentScope = animatedContentScope,
                                        onTrailerClick = { url, title -> onVideoClick(url, animeId, 0, 0, title) },
                                        onMarkWatchedClick = {
                                            scope.launch {
                                                val db = com.myanim.kondi.data.local.KondiDatabase.getDatabase(context)
                                                db.watchedAnimeDao().insertWatchedAnime(
                                                    com.myanim.kondi.data.local.WatchedAnime(
                                                        animeId = details.id,
                                                        title = details.title,
                                                        lastWatchedEpisode = 0,
                                                        posterUrl = details.poster
                                                    )
                                                )
                                                Toast.makeText(context, "İzlenmiş olarak işaretlendi", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) 
                                }
                            
                            displaySeasons.forEach { (seasonNum, seasonEpisodes) ->
                                item {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .height(18.dp)
                                                .background(Color(0xFFFF2E93), RoundedCornerShape(2.dp))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Sezon $seasonNum",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                                items(seasonEpisodes, key = { it.episodeId ?: it.url ?: "${it.seasonNumber}_${it.episodeNumber}" }) { video ->
                                    val episodeNumber = video.episodeNumber ?: 0
                                    
                                    val expectedTitleOld = if (video.seasonNumber == null || video.seasonNumber == 1) "${details.title} - $episodeNumber. Bölüm" else null
                                    val expectedTitleNew = com.myanim.kondi.data.download.DownloadUtils.createAnimecixFileName(
                                        details.title,
                                        video.seasonNumber ?: 1,
                                        episodeNumber
                                    )
                                    val downloadStatus = downloadsMap[expectedTitleNew]?.status ?: if (expectedTitleOld != null) downloadsMap[expectedTitleOld]?.status else null
                                    
                                    EpisodeItem(
                                        video = video,
                                        downloadStatus = downloadStatus,
                                        animeTitle = details.title,
                                        selectionMode = selectionMode,
                                        isSelected = selectedVideos.contains(video),
                                        onToggleSelection = { 
                                            selectedVideos = if (selectedVideos.contains(video)) selectedVideos - video else selectedVideos + video
                                        },
                                        onLongClick = { if (!selectionMode) selectedVideos = setOf(video) },
                                        onClick = {
                                            activeVideoForSource = video
                                            selectedEpisodeTitle = "Sezon ${video.seasonNumber ?: 1} - Bölüm ${video.episodeNumber ?: "?"}"
                                            viewModel.loadSources(video.episodeId ?: 0, video.animeId ?: animeId, video.seasonNumber, video.episodeNumber)
                                            showSourceDialog = true
                                        }
                                    )
                                }
                            }
                        }

                        if (selectionMode) {
                            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                                BatchDownloadOverlay(
                                    count = selectedVideos.size,
                                    onCancel = { selectedVideos = emptySet() },
                                    onDownload = { 
                                        val currentMaxOrder = downloads.maxOfOrNull { it.queueOrder } ?: 0
                                        val startOrder = if (currentMaxOrder > 0) currentMaxOrder + 1 else ((System.currentTimeMillis() / 1000) % Int.MAX_VALUE).toInt()
                                        val orderedEpisodes = selectedVideos.sortedWith(compareBy({ it.seasonNumber ?: 1 }, { it.episodeNumber ?: 0 }))
                                        var addedCount = 0
                                        orderedEpisodes.forEachIndexed { index, video ->
                                            val fileName = com.myanim.kondi.data.download.DownloadUtils.createAnimecixFileName(
                                                details.title,
                                                video.seasonNumber ?: 1,
                                                video.episodeNumber ?: 0
                                            )
                                            val existingDownload = downloads.find { it.title == fileName }
                                            if (existingDownload != null && existingDownload.status == com.myanim.kondi.data.local.DownloadStatus.COMPLETED.name) {
                                                // Already downloaded, skip
                                            } else {
                                                val lazyUrl = "animecix://resolve?episodeId=${video.episodeId ?: 0}&animeId=${video.animeId ?: animeId}&season=${video.seasonNumber ?: 1}&episode=${video.episodeNumber ?: 0}"
                                                downloadManager.startDownload(
                                                    title = fileName,
                                                    url = lazyUrl,
                                                    source = "ANIMECIX",
                                                    headers = mapOf("Referer" to "https://animecix.net"),
                                                    forceDisplayName = true,
                                                    queueOrder = startOrder + index
                                                )
                                                addedCount++
                                            }
                                        }
                                        if (addedCount > 0) {
                                            val skippedCount = orderedEpisodes.size - addedCount
                                            val msg = if (skippedCount > 0) "$addedCount bölüm kuyruğa eklendi ($skippedCount zaten indirilmiş)" else "$addedCount bölüm kuyruğa eklendi"
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Seçilen tüm bölümler zaten indirilmiş!", Toast.LENGTH_SHORT).show()
                                        }
                                        selectedVideos = emptySet()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Redundant local ErrorState and LoadingPlaceholder removed, now using AnimecixDetailComponents.kt versions.
