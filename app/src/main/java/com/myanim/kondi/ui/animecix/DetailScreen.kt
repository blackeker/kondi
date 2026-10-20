import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import coil.compose.AsyncImage
import com.myanim.kondi.data.animecix.AnimecixAnime
import com.myanim.kondi.data.animecix.AnimecixSource
import com.myanim.kondi.data.animecix.AnimecixVideo
import com.myanim.kondi.data.download.VideoDownloadManager
import com.myanim.kondi.data.local.DownloadStatus
import com.myanim.kondi.ui.animecix.AnimecixDetailViewModel
import com.myanim.kondi.ui.common.*
import com.myanim.kondi.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    animeId: Int,
    onBackClick: () -> Unit,
    onVideoClick: (String, String) -> Unit,
    viewModel: AnimecixDetailViewModel = viewModel()
) {
    val anime by viewModel.anime.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()

    var isSearchActive by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var seasonToDownload by remember { mutableStateOf<Pair<Int, List<AnimecixVideo>>?>(null) }
    val sourceSizes = remember { mutableStateMapOf<String, Long>() }
    val scope = rememberCoroutineScope()
    
    var selectedVideos by remember { mutableStateOf(setOf<AnimecixVideo>()) }
    val selectionMode = selectedVideos.isNotEmpty()
    var activeVideoForSource by remember { mutableStateOf<AnimecixVideo?>(null) }
    var selectedEpisodeTitle by remember { mutableStateOf("") }

    val context = LocalContext.current
    val downloadManager = remember { VideoDownloadManager.getInstance(context) }
    val downloads by downloadManager.downloadsFlow.collectAsStateWithLifecycle(emptyList())

    LaunchedEffect(animeId) { 
        viewModel.loadAnimeDetails(animeId)
        viewModel.checkFavoriteStatus(animeId)
    }

    // Source Selection Bottom Sheet
    if (showSourceDialog) {
        SourceSelectionBottomSheet(
            show = true,
            onDismiss = { 
                showSourceDialog = false 
                activeVideoForSource = null
            },
            title = selectedEpisodeTitle,
            sourcesList = sources,
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
                        onVideoClick(resolvedUrl, "${anime?.title ?: "Anime"} - S${seasonNum}E${episodeNum}")
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
                    if (existingDownload != null && existingDownload.status == DownloadStatus.COMPLETED.name) {
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
                                rawUrl
                            } else {
                                resolvedUrl
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
            },
            onHdwpClick = { source ->
                scope.launch {
                    val rawUrl = source.url
                    val resolvedUrl = viewModel.resolveUrl(rawUrl)
                    val urlToDownload = if (resolvedUrl != null && resolvedUrl.startsWith("http")) {
                        val lowerRawUrl = rawUrl.lowercase()
                        if (
                            lowerRawUrl.contains("sibnet") || 
                            lowerRawUrl.contains("ok.ru") || 
                            lowerRawUrl.contains("odnoklassniki") || 
                            lowerRawUrl.contains("streamtape") || 
                            lowerRawUrl.contains("voe") || 
                            lowerRawUrl.contains("uqload") || 
                            lowerRawUrl.contains("dood")
                        ) {
                            rawUrl
                        } else {
                            resolvedUrl
                        }
                    } else {
                        rawUrl
                    }

                    if (urlToDownload.isNotBlank()) {
                        val seasonNum = activeVideoForSource?.seasonNumber ?: 1
                        val episodeNum = activeVideoForSource?.episodeNumber ?: 0
                        val animeName = anime?.title ?: "Anime"
                        val formattedTitle = "$animeName - Sezon $seasonNum Bölüm $episodeNum"

                        com.myanim.kondi.util.HdwpDownloaderHelper.sendDownloadRequest(
                            context = context, 
                            videoUrl = urlToDownload,
                            title = formattedTitle
                        )
                    } else {
                        Toast.makeText(context, "Kaynak bulunamadı", Toast.LENGTH_SHORT).show()
                    }
                    showSourceDialog = false
                    activeVideoForSource = null
                }
            }
        )
    }

    // Season Download Dialog
    seasonToDownload?.let { pair ->
        val (seasonNum, seasonEpisodes) = pair
        SeasonDownloadDialog(
            seasonNumber = seasonNum,
            episodes = seasonEpisodes,
            animeTitle = anime?.title ?: "Anime",
            onDismiss = { seasonToDownload = null },
            onDownloadKondi = {
                val currentMaxOrder = downloads.maxOfOrNull { it.queueOrder } ?: 0
                val orderedEpisodes = seasonEpisodes
                    .filter { video ->
                        val cat = video.category?.lowercase() ?: ""
                        cat != "trailer" && cat != "opening credits" && cat != "ending credits" && (video.episodeNumber ?: 0) > 0
                    }
                    .sortedWith(compareBy({ it.seasonNumber ?: 1 }, { it.episodeNumber ?: 0 }))
                val startOrder = if (currentMaxOrder > 0) currentMaxOrder + 1 else ((System.currentTimeMillis() / 1000) % Int.MAX_VALUE).toInt()
                var addedCount = 0
                orderedEpisodes.forEachIndexed { index, video ->
                    val fileName = com.myanim.kondi.data.download.DownloadUtils.createAnimecixFileName(
                        anime?.title ?: "Anime",
                        video.seasonNumber ?: 1,
                        video.episodeNumber ?: 0
                    )
                    val existingDownload = downloads.find { it.title == fileName }
                    if (existingDownload != null && existingDownload.status == DownloadStatus.COMPLETED.name) {
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
                    val msg = if (skippedCount > 0) "$addedCount bölüm kuyruğa eklendi ($skippedCount zaten indirilmiş)" else "$addedCount bölüm indirme kuyruğuna eklendi"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Sezonun tüm bölümleri zaten indirilmiş!", Toast.LENGTH_SHORT).show()
                }
                seasonToDownload = null
            },
            onDownloadHdwp = {
                viewModel.bulkHdwpDownload(seasonEpisodes, anime?.title ?: "Anime", context)
                seasonToDownload = null
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        SimpleTextField(
                            value = "",
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = "Bölüm ara...",
                            leadingIcon = Icons.Default.Search,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = anime?.title ?: "Detaylar",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    if (isSearchActive) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Temizle")
                        }
                    } else {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Ara")
                        }
                        IconButton(onClick = { anime?.let { viewModel.toggleFavorite(it) } }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favori",
                                tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { viewModel.toggleSortOrder() }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sırala")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceCard)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (errorMessage != null && anime == null) {
                ErrorState(message = errorMessage!!, onRetry = { viewModel.retry(animeId) })
            } else if (isLoading && anime == null) {
                LoadingState()
            } else {
                anime?.let { details ->
                    val episodes = remember(details.videos) {
                        (details.videos ?: emptyList()).filter { video ->
                            val cat = video.category?.lowercase() ?: ""
                            cat != "trailer" &&
                            cat != "opening credits" &&
                            cat != "ending credits" &&
                            cat != "clip" &&
                            cat != "teaser" &&
                            (video.episodeNumber ?: 0) > 0
                        }
                    }
                    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                    val filteredEpisodes = remember(episodes, searchQuery) {
                        if (searchQuery.isBlank()) episodes else episodes.filter {
                            it.name?.contains(searchQuery, ignoreCase = true) == true ||
                            it.episodeNumber?.toString()?.contains(searchQuery) == true
                        }
                    }

                    val isAscending by viewModel.isAscending.collectAsStateWithLifecycle()
                    val sortedEpisodes = remember(filteredEpisodes, isAscending) {
                        if (isAscending) {
                            filteredEpisodes.sortedWith(compareBy({ it.seasonNumber ?: 1 }, { it.episodeNumber ?: 0 }))
                        } else {
                            filteredEpisodes.sortedWith(compareByDescending<AnimecixVideo> { it.seasonNumber ?: 1 }.thenByDescending { it.episodeNumber ?: 0 })
                        }
                    }

                    val downloadsMap = remember(downloads, details.title) {
                        downloads.filter { it.source == "ANIMECIX" }.associateBy { it.title }
                    }

                    val displaySeasons = remember(sortedEpisodes, isAscending) {
                        val episodesBySeason = sortedEpisodes.groupBy { it.seasonNumber ?: 1 }
                        if (isAscending) episodesBySeason.toSortedMap() else episodesBySeason.toSortedMap(reverseOrder())
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header
                        DetailHeader(
                            details = details,
                            onTrailerClick = { url, title -> onVideoClick(url, title) },
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

                        // Seasons & Episodes
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = if (selectionMode) 120.dp else 0.dp)
                        ) {
                            displaySeasons.forEach { (seasonNum, seasonEpisodes) ->
                                item(key = "season_header_$seasonNum") {
                                    SeasonHeader(
                                        seasonNumber = seasonNum,
                                        episodeCount = seasonEpisodes.size,
                                        onDownloadClick = { seasonToDownload = seasonNum to seasonEpisodes }
                                    )
                                }
                                itemsIndexed(seasonEpisodes, key = { index, video -> "season_${seasonNum}_ep_${video.episodeId ?: video.url ?: video.episodeNumber ?: index}_$index" }) { index, video ->
                                    val episodeNumber = video.episodeNumber ?: 0
                                    val expectedTitleNew = com.myanim.kondi.data.download.DownloadUtils.createAnimecixFileName(
                                        details.title,
                                        video.seasonNumber ?: 1,
                                        episodeNumber
                                    )
                                    val expectedTitleOld = if (video.seasonNumber == null || video.seasonNumber == 1) "${details.title} - $episodeNumber. Bölüm" else null
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

                        // Batch Download Overlay
                        if (selectionMode) {
                            BatchDownloadOverlay(
                                count = selectedVideos.size,
                                onCancel = { selectedVideos = emptySet() },
                                onDownload = { 
                                    val currentMaxOrder = downloads.maxOfOrNull { it.queueOrder } ?: 0
                                    val startOrder = if (currentMaxOrder > 0) currentMaxOrder + 1 else ((System.currentTimeMillis() / 1000) % Int.MAX_VALUE).toInt()
                                    val orderedEpisodes = selectedVideos.toList().sortedWith(compareBy({ it.seasonNumber ?: 1 }, { it.episodeNumber ?: 0 }))
                                    var addedCount = 0
                                    orderedEpisodes.forEachIndexed { index, video ->
                                        val fileName = com.myanim.kondi.data.download.DownloadUtils.createAnimecixFileName(
                                            details.title,
                                            video.seasonNumber ?: 1,
                                            video.episodeNumber ?: 0
                                        )
                                        val existingDownload = downloads.find { it.title == fileName }
                                        if (existingDownload != null && existingDownload.status == DownloadStatus.COMPLETED.name) {
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
                                },
                                onHdwpDownload = {
                                    viewModel.bulkHdwpDownload(selectedVideos.toList(), details.title, context)
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

// ===== DETAIL HEADER =====
@Composable
fun DetailHeader(
    details: AnimecixAnime,
    onTrailerClick: (String, String) -> Unit,
    onMarkWatchedClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Poster + Info Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, OutlineVariant, RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = details.poster ?: "file:///android_asset/placeholder.png",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = details.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (details.year != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = details.year.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                if (details.rating != null && details.rating != "0") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(16.dp))
                        Text(text = details.rating!!, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }

                if (details.seasonCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        details.genres?.take(3)?.forEach { genre ->
                            Surface(
                                color = PrimaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = genre.name,
                                    color = Primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Description
        if (details.description != null && details.description.isNotBlank()) {
            Text(
                text = details.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
            )
        }

        // Action Buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (details.trailerUrl != null && details.trailerUrl.isNotBlank()) {
                SimpleButton(
                    text = "Fragmanı İzle",
                    onClick = { onTrailerClick(details.trailerUrl!!, "Fragman - ${details.title}") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            SimpleOutlinedButton(
                text = "İzlenmiş Olarak İşaretle",
                onClick = onMarkWatchedClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ===== SEASON HEADER =====
@Composable
fun SeasonHeader(
    seasonNumber: Int,
    episodeCount: Int,
    onDownloadClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .background(Primary, RoundedCornerShape(2.dp))
            )
            Text(
                text = "Sezon $seasonNumber",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Surface(
                color = SurfaceElevated,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "$episodeCount Bölüm",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        SimpleOutlinedButton(
            text = "Sezonu İndir",
            onClick = onDownloadClick,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

// ===== EPISODE ITEM =====
@Composable
fun EpisodeItem(
    video: AnimecixVideo,
    downloadStatus: String?,
    animeTitle: String,
    selectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val containerBg = when {
        isSelected -> PrimaryContainer
        downloadStatus == DownloadStatus.DOWNLOADING.name || downloadStatus == DownloadStatus.PENDING.name -> PrimaryContainer.copy(alpha = 0.3f)
        else -> SurfaceCard
    }
    val containerBorder = when {
        isSelected -> BorderStroke(2.dp, Primary)
        downloadStatus == DownloadStatus.DOWNLOADING.name || downloadStatus == DownloadStatus.PENDING.name -> BorderStroke(1.dp, Primary)
        else -> BorderStroke(1.dp, OutlineVariant)
    }

    SimpleCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection checkbox or thumbnail
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    colors = CheckboxDefaults.colors(checkedColor = Primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceElevated)
                ) {
                    AsyncImage(
                        model = video.poster ?: "file:///android_asset/placeholder.png",
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    // Episode number badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${video.episodeNumber ?: "?"}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Episode info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.name ?: "Bölüm ${video.episodeNumber ?: 0}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Sezon ${video.seasonNumber ?: 1} • Bölüm ${video.episodeNumber ?: 0}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            // Trailing: Download status or Play icon
            when (downloadStatus) {
                DownloadStatus.COMPLETED.name -> {
                    Box(
                        modifier = Modifier.size(32.dp).background(Color.Green.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "İndirildi", tint = Color.Green, modifier = Modifier.size(18.dp))
                    }
                }
                DownloadStatus.DOWNLOADING.name, DownloadStatus.PENDING.name -> {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp, color = Primary)
                }
                else -> {
                    Icon(Icons.Default.PlayCircleOutline, contentDescription = "Oynat", tint = Primary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

// ===== SOURCE SELECTION BOTTOM SHEET =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSelectionBottomSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    title: String,
    sourcesList: List<AnimecixSource>,
    sourceSizes: Map<String, Long>,
    onResolveSize: (String) -> Unit,
    onPlayClick: (String) -> Unit,
    onDownloadClick: (AnimecixSource) -> Unit,
    onHdwpClick: (AnimecixSource) -> Unit
) {
    if (show) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = SurfaceCard,
            scrimColor = Color.Black.copy(alpha = 0.5f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(text = "Kaynak seçimi yapın", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    items(sourcesList.size) { index ->
                        val source = sourcesList[index]
                        val size = sourceSizes[source.url]
                        val sizeText = if (size != null && size > 0) {
                            val mb = size / (1024 * 1024)
                            if (mb > 0) "${mb} MB" else "${size / 1024} KB"
                        } else "Bilinmiyor"

                        SimpleCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onPlayClick(source.url) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(40.dp).background(PrimaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    source.quality?.let { Text(text = "Kalite: $it", style = MaterialTheme.typography.labelSmall, color = TextSecondary) }
                                    Text(text = sizeText, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                                IconButton(onClick = { onDownloadClick(source) }) {
                                    Icon(Icons.Default.Download, contentDescription = "İndir", tint = Primary, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { onHdwpClick(source) },
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Green.copy(alpha = 0.1f))
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = "HDWP ile İndir", tint = Color.Green, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===== BATCH DOWNLOAD OVERLAY =====
@Composable
fun BatchDownloadOverlay(
    count: Int,
    onCancel: () -> Unit,
    onDownload: () -> Unit,
    onHdwpDownload: () -> Unit
) {
    if (count > 0) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = PrimaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(48.dp).background(Primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "$count", color = OnPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("$count Bölüm Seçildi", color = Primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("İndirmeye hazır", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "İptal", tint = TextSecondary)
                    }
                    SimpleButton(
                        text = "HDWP Gönder",
                        onClick = onHdwpDownload,
                        modifier = Modifier.height(40.dp)
                    )
                    SimpleButton(
                        text = "İndir",
                        onClick = onDownload,
                        modifier = Modifier.height(40.dp)
                    )
                }
            }
        }
    }
}

// ===== SEASON DOWNLOAD DIALOG =====
@Composable
fun SeasonDownloadDialog(
    seasonNumber: Int,
    episodes: List<AnimecixVideo>,
    animeTitle: String,
    onDismiss: () -> Unit,
    onDownloadKondi: () -> Unit,
    onDownloadHdwp: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Download, contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp))
                Text("Sezon $seasonNumber İndir", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "\"$animeTitle\" dizisine ait Sezon $seasonNumber kapsamındaki ${episodes.size} bölümün tamamı indirme kuyruğuna eklenecektir.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Daha önce indirilmiş olan bölümler otomatik olarak atlanacaktır.",
                    color = TextSecondary.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SimpleButton(
                    text = "Kondi İle İndir (${episodes.size} Bölüm)",
                    onClick = onDownloadKondi,
                    modifier = Modifier.fillMaxWidth()
                )
                SimpleButton(
                    text = "HDWP / WhatsApp ile Gönder",
                    onClick = onDownloadHdwp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Vazgeç", color = TextSecondary)
            }
        },
        containerColor = SurfaceCard,
        shape = RoundedCornerShape(16.dp)
    )
}