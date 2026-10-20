package com.myanim.kondi.ui.animecix

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.myanim.kondi.data.animecix.AnimecixTitle
import com.myanim.kondi.data.animecix.AnimecixVideo
import com.myanim.kondi.ui.common.*
import com.myanim.kondi.ui.download.DownloadsTab
import com.myanim.kondi.ui.download.StorageTab
import com.myanim.kondi.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AnimecixViewModel,
    onAnimeClick: (Int) -> Unit,
    onVideoClick: (String, String) -> Unit,
    onStorageClick: () -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    val episodes by viewModel.latestEpisodes.collectAsStateWithLifecycle()
    val watchHistory by viewModel.watchHistory.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showStorage by remember { mutableStateOf(false) }

    // Debounced search
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            kotlinx.coroutines.delay(500L)
            viewModel.search(searchQuery)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Kondi",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                },
                actions = {
                    IconButton(onClick = onStorageClick) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = "Depolama",
                            tint = OnSurface
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ayarlar",
                            tint = OnSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceCard
                )
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(SurfaceCard)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomNavItem(
                        selected = selectedTab == 0,
                        icon = Icons.Default.Home,
                        label = "Ana Sayfa",
                        onClick = { selectedTab = 0; searchQuery = ""; viewModel.clearSearch() }
                    )
                    BottomNavItem(
                        selected = selectedTab == 1,
                        icon = Icons.Default.Search,
                        label = "Arama",
                        onClick = { selectedTab = 1; searchQuery = ""; viewModel.clearSearch() }
                    )
                    BottomNavItem(
                        selected = selectedTab == 2,
                        icon = Icons.Default.Favorite,
                        label = "Favoriler",
                        onClick = { selectedTab = 2; searchQuery = ""; viewModel.clearSearch() }
                    )
                    BottomNavItem(
                        selected = selectedTab == 3,
                        icon = Icons.Default.DownloadDone,
                        label = "İndirilenler",
                        onClick = { selectedTab = 3; searchQuery = ""; viewModel.clearSearch() }
                    )
                    BottomNavItem(
                        selected = selectedTab == 4,
                        icon = Icons.Default.Settings,
                        label = "Ayarlar",
                        onClick = { selectedTab = 4; searchQuery = ""; viewModel.clearSearch() }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> HomeTab(
                    viewModel = viewModel,
                    episodes = episodes,
                    watchHistory = watchHistory,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onAnimeClick = onAnimeClick,
                    onVideoClick = onVideoClick
                )
                1 -> SearchTab(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    searchResults = viewModel.searchResults.collectAsState().value,
                    isSearchLoading = viewModel.isSearchLoading.collectAsState().value,
                    onAnimeClick = onAnimeClick
                )
                2 -> FavoritesTab(
                    favorites = viewModel.favorites.collectAsState().value,
                    onAnimeClick = onAnimeClick
                )
                3 -> DownloadsTab(
                    onItemClick = { Log.d("HomeScreen", "İndirme detayına tıklandı") }
                )
                4 -> SettingsTab(
                    onStorageClick = { showStorage = true },
                    onSettingsClick = onSettingsClick
                )
            }

            if (showStorage) {
                StorageTab(
                    modifier = Modifier.fillMaxSize(),
                    onClose = { showStorage = false }
                )
            }
        }
    }
}

@Composable
fun HomeTab(
    viewModel: AnimecixViewModel,
    episodes: List<AnimecixVideo>,
    watchHistory: List<com.myanim.kondi.data.local.WatchHistory>,
    isLoading: Boolean,
    errorMessage: String?,
    onAnimeClick: (Int) -> Unit,
    onVideoClick: (String, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (errorMessage != null && episodes.isEmpty()) {
            ErrorState(message = errorMessage ?: "Bilinmeyen hata", onRetry = { viewModel.retryLatestEpisodes() })
        } else if (isLoading && episodes.isEmpty()) {
            LoadingState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                // Continue Watching
                if (watchHistory.isNotEmpty()) {
                    item {
                        SectionHeader(title = "İzlemeye Devam Et")
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(watchHistory, key = { it.videoUrl }) { history ->
                                ContinueWatchingItem(
                                    title = history.title,
                                    posterUrl = history.posterUrl,
                                    onClick = { onVideoClick(history.videoUrl, history.title) }
                                )
                            }
                        }
                    }
                }

                // Latest Episodes
                item {
                    SectionHeader(title = "Son Eklenen Bölümler")
                }
                if (isLoading && episodes.isEmpty()) {
                    item {
                        LoadingState(message = "Bölümler yükleniyor...")
                    }
                } else {
                    items(episodes, key = { it.url ?: it.name ?: it.hashCode().toString() }) { video ->
                        EpisodeItem(
                            title = video.name ?: "Bilinmeyen Anime",
                            episodeNumber = video.episodeNumber ?: 0,
                            posterUrl = video.poster,
                            onClick = { video.animeId?.let(onAnimeClick) }
                        )
                    }
                    if (isLoading) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchTab(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<AnimecixTitle>,
    isSearchLoading: Boolean,
    onAnimeClick: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SimpleTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = "Anime Ara",
            leadingIcon = Icons.Default.Search,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (searchQuery.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (searchResults.isNotEmpty()) "${searchResults.size} sonuç bulundu" else "Sonuç bulunamadı",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        if (isSearchLoading && searchResults.isEmpty()) {
            LoadingState()
        } else if (searchResults.isEmpty() && searchQuery.isNotEmpty()) {
            EmptyState(
                title = "Sonuç bulunamadı",
                subtitle = "Başka bir kelime deneyin"
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(searchResults, key = { it.id?.toString() ?: it.name ?: it.hashCode().toString() }) { item ->
                    SearchResultItem(
                        title = item.name ?: "Bilinmeyen Anime",
                        posterUrl = item.poster,
                        onClick = { item.id?.let(onAnimeClick) }
                    )
                }
            }
        }
    }
}

@Composable
fun FavoritesTab(
    favorites: List<com.myanim.kondi.data.local.Favorite>,
    onAnimeClick: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (favorites.isEmpty()) {
            EmptyState(
                icon = Icons.Default.FavoriteBorder,
                title = "Henüz favori yok",
                subtitle = "İçerikleri favorilere ekleyebilirsiniz"
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(favorites, key = { it.url }) { favorite ->
                    FavoriteItem(
                        title = favorite.title,
                        posterUrl = favorite.posterUrl,
                        animeId = favorite.animeId ?: 0,
                        onClick = { onAnimeClick(favorite.animeId ?: 0) }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsTab(
    onStorageClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SectionHeader(title = "Ayarlar")
        SimpleListItem(
            leading = { Icon(Icons.Default.Storage, contentDescription = null) },
            title = { Text("Depolama Yönetimi") },
            onClick = onStorageClick,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        SimpleDivider(modifier = Modifier.padding(horizontal = 16.dp))
        SimpleListItem(
            leading = { Icon(Icons.Default.Settings, contentDescription = null) },
            title = { Text("Uygulama Ayarları") },
            onClick = onSettingsClick,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
fun ContinueWatchingItem(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        AnimePoster(url = posterUrl, size = 160.dp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EpisodeItem(
    title: String,
    episodeNumber: Int,
    posterUrl: String?,
    onClick: () -> Unit
) {
    SimpleCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimePoster(url = posterUrl, size = 80.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Bölüm $episodeNumber",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun SearchResultItem(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit
) {
    SimpleCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimePoster(url = posterUrl, size = 80.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FavoriteItem(
    title: String,
    posterUrl: String?,
    animeId: Int,
    onClick: () -> Unit
) {
    SimpleCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimePoster(url = posterUrl, size = 80.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favori",
                    tint = Color.Red,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun BottomNavItem(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) Primary else TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Primary else TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
