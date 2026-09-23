package com.myanim.kondi.ui.catalog

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myanim.kondi.data.animecix.AnimecixVideo
import com.myanim.kondi.data.model.ProviderStatus
import com.myanim.kondi.ui.animecix.AnimecixViewModel
import com.myanim.kondi.ui.common.*
import com.myanim.kondi.ui.theme.*

enum class CatalogSortOption(val displayName: String) {
    POPULAR("En Popüler"),
    RATING("En Yüksek Puan"),
    LATEST("En Yeniler"),
    AZ("A'dan Z'ye")
}

data class CatalogDisplayItem(
    val id: String,
    val title: String,
    val posterUrl: String?,
    val episodeText: String?,
    val rating: String = "9.8",
    val animeId: Int? = null,
    val videoUrl: String? = null,
    val isFavorite: Boolean = false
)

@Composable
fun ProviderCatalogScreen(
    onBackClick: () -> Unit,
    onAnimeClick: (Int) -> Unit,
    onVideoClick: (String, String) -> Unit,
    animecixViewModel: AnimecixViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    // Animecix Live Dynamic States
    val episodes by animecixViewModel.latestEpisodes.collectAsStateWithLifecycle()
    val categoryItems by animecixViewModel.categoryItems.collectAsStateWithLifecycle()
    val searchResults by animecixViewModel.searchResults.collectAsStateWithLifecycle()
    val favorites by animecixViewModel.favorites.collectAsStateWithLifecycle()
    val isAnimecixLoading by animecixViewModel.isLoading.collectAsStateWithLifecycle()
    val isCategoryLoading by animecixViewModel.isCategoryLoading.collectAsStateWithLifecycle()
    val isSearchLoading by animecixViewModel.isSearchLoading.collectAsStateWithLifecycle()

    val favoriteUrls = remember(favorites) { favorites.map { it.url }.toSet() }

    var selectedCategory by remember { mutableStateOf("Tümü") }
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(CatalogSortOption.POPULAR) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Live search handling for Animecix
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            kotlinx.coroutines.delay(400L)
            animecixViewModel.search(searchQuery)
        } else {
            animecixViewModel.clearSearch()
        }
    }

    // Category selection handling for Animecix
    LaunchedEffect(selectedCategory) {
        if (selectedCategory != "Tümü" && searchQuery.isBlank()) {
            val matchedCategory = animecixViewModel.categories.find { 
                it.first.equals(selectedCategory, ignoreCase = true) || it.second.equals(selectedCategory, ignoreCase = true)
            }
            if (matchedCategory != null) {
                animecixViewModel.selectCategory(matchedCategory)
            }
        } else if (selectedCategory == "Tümü" && searchQuery.isBlank()) {
            animecixViewModel.clearSearch()
        }
    }

    // Convert dynamic Animecix data into CatalogDisplayItems
    val dynamicCatalog: List<CatalogDisplayItem> = remember(
        episodes, categoryItems, searchResults, searchQuery, selectedCategory, sortOption, favoriteUrls
    ) {
        val rawList: List<CatalogDisplayItem> = when {
            searchQuery.isNotBlank() -> {
                searchResults.mapIndexed { index, title ->
                    CatalogDisplayItem(
                        id = "ac-search-${title.id ?: title.name.hashCode()}_$index",
                        title = title.name ?: "Anime",
                        posterUrl = title.poster ?: "file:///android_asset/placeholder.png",
                        rating = "9.5",
                        episodeText = "ANİME",
                        animeId = title.id,
                        isFavorite = false
                    )
                }
            }
            selectedCategory != "Tümü" && categoryItems.isNotEmpty() -> {
                categoryItems.mapIndexed { index, title ->
                    CatalogDisplayItem(
                        id = "ac-cat-${title.id ?: title.name.hashCode()}_$index",
                        title = title.name ?: "Anime",
                        posterUrl = title.poster ?: "file:///android_asset/placeholder.png",
                        rating = "9.4",
                        episodeText = "BÖLÜMLER",
                        animeId = title.id,
                        isFavorite = false
                    )
                }
            }
            episodes.isNotEmpty() -> {
                episodes.mapIndexed { index, video ->
                    CatalogDisplayItem(
                        id = "ac-ep-${video.url?.hashCode() ?: video.name.hashCode()}_$index",
                        title = video.name ?: "Anime Bölümü",
                        posterUrl = video.poster ?: "file:///android_asset/placeholder.png",
                        rating = "9.8",
                        episodeText = if (video.episodeNumber != null) "EP ${video.episodeNumber}" else "YENİ",
                        videoUrl = video.url,
                        animeId = video.animeId,
                        isFavorite = favoriteUrls.contains(video.url)
                    )
                }
            }
            else -> emptyList()
        }

        when (sortOption) {
            CatalogSortOption.POPULAR -> rawList
            CatalogSortOption.RATING -> rawList.sortedByDescending { it.rating }
            CatalogSortOption.LATEST -> rawList
            CatalogSortOption.AZ -> rawList.sortedBy { it.title }
        }
    }

    val spotlightItem = remember(episodes) {
        episodes.firstOrNull()
    }

    val gridState = rememberLazyGridState()

    // Infinite scroll for Animecix
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && lastIndex >= dynamicCatalog.size - 4 && dynamicCatalog.isNotEmpty()) {
                    when {
                        searchQuery.isNotBlank() -> animecixViewModel.loadMoreSearch()
                        selectedCategory != "Tümü" -> animecixViewModel.loadCategoryItems()
                        else -> animecixViewModel.loadMoreLatest()
                    }
                }
            }
    }

    ObsidianGlowBackground(
        modifier = modifier,
        primaryGlow = NeonMagenta,
        secondaryGlow = NeonCyan
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Glassy Navigation & Search Bar
            CatalogTopBar(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onBackClick = onBackClick,
                currentSort = sortOption,
                onSortSelect = { sortOption = it },
                showSortMenu = showSortMenu,
                onToggleSortMenu = { showSortMenu = it }
            )

            // 2. Dynamic Category Filter Chips (Yatay Kaydırılabilir Neon Çipler)
            if (searchQuery.isBlank()) {
                val allCategories = remember(animecixViewModel.categories) {
                    listOf("Tümü") + animecixViewModel.categories.map { it.first }
                }

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(allCategories) { category ->
                        NeonCategoryChip(
                            text = category,
                            isSelected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            activeColor = NeonMagenta
                        )
                    }
                }
            }

            // 3. Media Grid & Spotlight Content
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 90.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Spotlight Banner on top (only shown when not searching and category is "Tümü")
                if (searchQuery.isBlank() && selectedCategory == "Tümü" && spotlightItem != null) {
                    item(span = { GridItemSpan(2) }) {
                        Column(modifier = Modifier.padding(bottom = 8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Animecix Vitrini",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "CANLI YAYIN",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = EmeraldActive
                                )
                            }
                            SpotlightHeroBanner(
                                title = spotlightItem.name ?: "Anime",
                                posterUrl = spotlightItem.poster,
                                backdropUrl = spotlightItem.poster,
                                quality = "1080p FHD",
                                rating = "9.9",
                                episodeText = if (spotlightItem.episodeNumber != null) "EP ${spotlightItem.episodeNumber}" else "YENİ BÖLÜM",
                                synopsis = "En yeni bölüm Animecix üzerinden yayında. Hemen Türkçe altyazılı izleyin.",
                                onPlayClick = {
                                    val animeId = spotlightItem.animeId
                                    val videoUrl = spotlightItem.url
                                    if (animeId != null) {
                                        onAnimeClick(animeId)
                                    } else if (videoUrl != null) {
                                        onVideoClick(videoUrl, spotlightItem.name ?: "Anime")
                                    }
                                },
                                onDetailsClick = {
                                    spotlightItem.animeId?.let { onAnimeClick(it) }
                                }
                            )
                        }
                    }

                    // Section Title
                    item(span = { GridItemSpan(2) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Katalog Arşivi",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${dynamicCatalog.size} İçerik",
                                fontSize = 12.sp,
                                color = TextMed,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Grid Items
                if (dynamicCatalog.isEmpty() && (isAnimecixLoading || isSearchLoading || isCategoryLoading)) {
                    items(6) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                        )
                    }
                } else if (dynamicCatalog.isEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.SearchOff,
                                    contentDescription = null,
                                    tint = TextMed,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Bu kriterlere uygun içerik bulunamadı.",
                                    color = TextMed,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(dynamicCatalog, key = { index, item -> "cat_${item.id ?: item.title}_$index" }) { index, item ->
                        MediaPosterCard(
                            title = item.title,
                            posterUrl = item.posterUrl,
                            quality = "1080p FHD",
                            episodeText = item.episodeText,
                            rating = item.rating,
                            isFavorite = item.isFavorite,
                            onFavoriteToggle = {
                                val video = AnimecixVideo(
                                    episodeNumber = null,
                                    seasonNumber = null,
                                    poster = item.posterUrl,
                                    name = item.title,
                                    directUrl = item.videoUrl ?: "",
                                    directEpisodeId = null,
                                    animeId = item.animeId,
                                    description = null,
                                    language = null,
                                    category = null,
                                    quality = "1080p FHD"
                                )
                                animecixViewModel.toggleFavorite(video)
                            },
                            onClick = {
                                if (item.animeId != null) {
                                    onAnimeClick(item.animeId)
                                } else if (item.videoUrl != null) {
                                    onVideoClick(item.videoUrl, item.title)
                                }
                            }
                        )
                    }

                    if (isAnimecixLoading || isSearchLoading || isCategoryLoading) {
                        item(span = { GridItemSpan(2) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = NeonMagenta,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Catalog Top Navigation Bar with Back Button, Glowing Provider Title, and Search & Sort
 */
@Composable
private fun CatalogTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBackClick: () -> Unit,
    currentSort: CatalogSortOption,
    onSortSelect: (CatalogSortOption) -> Unit,
    showSortMenu: Boolean,
    onToggleSortMenu: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Navigation Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            LuxuryGlassCard(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                onClick = onBackClick
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Glowing Animecix Title & Status
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ANIMECIX",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    GlowingStatusBadge(status = ProviderStatus.ONLINE, customText = "PRIME")
                }
                Text(
                    text = "Anime Kataloğu & Arşivi",
                    fontSize = 11.sp,
                    color = TextMed
                )
            }

            // Sort Button with Dropdown
            Box {
                LuxuryGlassCard(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    onClick = { onToggleSortMenu(true) }
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Sırala",
                            tint = NeonMagenta,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { onToggleSortMenu(false) },
                    modifier = Modifier
                        .background(ObsidianDeep)
                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                ) {
                    CatalogSortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.displayName,
                                    color = if (currentSort == option) NeonMagenta else Color.White,
                                    fontWeight = if (currentSort == option) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onSortSelect(option)
                                onToggleSortMenu(false)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // In-source Instant Search Bar
        LuxuryGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = CapsuleShape,
            containerColor = Color.White.copy(alpha = 0.05f),
            borderColor = Color.White.copy(alpha = 0.12f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = NeonMagenta.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Animecix içinde canlı anime ara...",
                            color = TextMed.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(NeonMagenta),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Temizle",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onSearchQueryChange("") }
                    )
                }
            }
        }
    }
}


