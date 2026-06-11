@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package com.myanim.kondi.ui.animecix

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanim.kondi.ui.common.*
import com.myanim.kondi.ui.theme.AnimeTheme
import com.myanim.kondi.ui.download.DownloadsScreen
import com.myanim.kondi.data.animecix.AnimecixVideo
import com.myanim.kondi.util.ExternalPlayerHelper
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AnimecixHomeScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onAnimeClick: (Int) -> Unit,
    onVideoClick: (String, Int, Int, Int, String) -> Unit,
    onBackToHome: () -> Unit = {},
    onDownloadsClick: () -> Unit,
    onSnifferClick: () -> Unit,
    onStorageClick: () -> Unit,
    activeTheme: AnimeTheme = AnimeTheme.DEFAULT,
    onThemeChange: (AnimeTheme) -> Unit = {},
    viewModel: AnimecixViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val episodes by viewModel.latestEpisodes.collectAsStateWithLifecycle()
    val categoryItems by viewModel.categoryItems.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val watchHistory by viewModel.watchHistory.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isCategoryLoading by viewModel.isCategoryLoading.collectAsStateWithLifecycle()
    val isSearchLoading by viewModel.isSearchLoading.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val playbackProgressList by viewModel.playbackProgress.collectAsStateWithLifecycle()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    
    val pagerState = rememberPagerState(pageCount = { 4 })
    
    // Debounced Search Optimization
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            kotlinx.coroutines.delay(500L)
            selectedTab = 2 // Switch to Search tab
            viewModel.search(searchQuery)
        } else {
            viewModel.clearSearch()
            if (selectedTab == 2) {
                selectedTab = 0
                isSearchExpanded = false
            }
        }
    }
    
    // Sync selectedTab -> pagerState
    LaunchedEffect(selectedTab) {
        val targetPage = when (selectedTab) {
            0 -> 0
            1 -> 1
            4 -> 2
            5 -> 3
            else -> null
        }
        if (targetPage != null && pagerState.currentPage != targetPage && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    // Sync pagerState -> selectedTab
    LaunchedEffect(pagerState.currentPage) {
        val targetTab = when (pagerState.currentPage) {
            0 -> 0
            1 -> 1
            2 -> 4
            3 -> 5
            else -> 0
        }
        if (selectedTab != targetTab && selectedTab != 2 && selectedTab != 3) {
            selectedTab = targetTab
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        KondiBackground()
 
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                AnimecixTopBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { 
                        searchQuery = it
                        if (it.isEmpty()) {
                            viewModel.clearSearch()
                            if (selectedTab == 2) selectedTab = 0
                        }
                    },
                    onSearchSubmit = {
                        if (searchQuery.isNotBlank()) {
                            selectedTab = 2
                            viewModel.search(searchQuery)
                        }
                    },
                    categories = viewModel.categories,
                    selectedCategory = selectedCategory,
                    onCategorySelect = { category ->
                        selectedTab = 3
                        viewModel.selectCategory(category)
                        searchQuery = ""
                        viewModel.clearSearch()
                    },
                    selectedTab = selectedTab,
                    onTabSelect = { 
                        selectedTab = it
                        if (it != 2) {
                            searchQuery = ""
                            viewModel.clearSearch()
                        }
                    },
                    onStorageClick = onStorageClick,
                    onBackToHome = onBackToHome,
                    onSettingsClick = {},
                    isSearchExpanded = isSearchExpanded,
                    onSearchExpandToggle = { expanded ->
                        isSearchExpanded = expanded
                        if (expanded) {
                            selectedTab = 2
                        } else {
                            selectedTab = 0
                            searchQuery = ""
                            viewModel.clearSearch()
                        }
                    }
                )
            },
            bottomBar = {
                GlassyBottomBar(
                    selectedTab = selectedTab,
                    onTabSelect = { tab ->
                        selectedTab = tab
                        if (tab != 2) {
                            isSearchExpanded = false
                            searchQuery = ""
                            viewModel.clearSearch()
                        }
                    }
                )
            }
        ) { padding ->
            val brush = shimmerBrush()
            
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (selectedTab == 2) { // Search
                    SearchTabContent(
                        searchQuery = searchQuery,
                        searchResults = searchResults,
                        isSearchLoading = isSearchLoading,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope,
                        onAnimeClick = onAnimeClick,
                        onClearSearch = {
                            searchQuery = ""
                            viewModel.clearSearch()
                            selectedTab = 0
                        },
                        onLoadMore = { viewModel.loadMoreSearch() }
                    )
                } else if (selectedTab == 3) { // Categories
                    if (errorMessage != null && categoryItems.isEmpty()) {
                        ErrorState(message = errorMessage!!, onRetry = { viewModel.loadCategoryItems() })
                    } else if (isCategoryLoading && categoryItems.isEmpty()) {
                        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                            items(5) { ShimmerSearchItem(brush) }
                        }
                    } else {
                        val listState = rememberLazyListState()
                        LaunchedEffect(listState) {
                            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                                .collect { lastIndex ->
                                    if (lastIndex == categoryItems.size - 1) viewModel.loadCategoryItems()
                                }
                        }
                        SearchList(
                            results = categoryItems,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedContentScope = animatedContentScope,
                            onClick = onAnimeClick,
                            state = listState
                        )
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = true
                    ) { page ->
                        when (page) {
                            0 -> { // Latest
                                if (errorMessage != null && episodes.isEmpty()) {
                                    ErrorState(message = errorMessage!!, onRetry = { viewModel.retryLatestEpisodes() })
                                } else if (isLoading && episodes.isEmpty()) {
                                    LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(8.dp)) {
                                        items(8) { ShimmerItem(brush) }
                                    }
                                } else {
                                    val gridState = rememberLazyGridState()
                                    LaunchedEffect(gridState) {
                                        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                                            .collect { lastIndex ->
                                                if (lastIndex == episodes.size - 1) viewModel.loadMoreLatest()
                                            }
                                    }
                                    
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        AnimeList(
                                            videos = episodes,
                                            favorites = favorites,
                                            watchHistory = watchHistory,
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedContentScope = animatedContentScope,
                                            onClick = onAnimeClick,
                                            onVideoClick = { url, title -> onVideoClick(url, -1, 0, 0, title) },
                                            onFavoriteToggle = { viewModel.toggleFavorite(it) },
                                            state = gridState
                                        )
                                    }
                                }
                            }
                            1 -> { // Favorites
                                if (favorites.isEmpty()) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Henüz favori eklenmedi.", color = Color.White.copy(alpha = 0.7f))
                                    }
                                } else {
                                    val favoriteVideos = remember(favorites) {
                                        favorites.map { it.toAnimecixVideo() }
                                    }
                                    AnimeList(
                                        videos = favoriteVideos,
                                        favorites = favorites,
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedContentScope = animatedContentScope,
                                        onClick = onAnimeClick,
                                        onFavoriteToggle = { viewModel.toggleFavorite(it) }
                                    )
                                }
                            }
                            2 -> { // Library (Watch/Downloads)
                                com.myanim.kondi.ui.common.LibraryScreen(isEmbedded = true)
                            }
                            3 -> { // Settings
                                com.myanim.kondi.ui.common.SettingsScreen(
                                    currentTheme = activeTheme,
                                    onThemeSelect = onThemeChange
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimecixTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    categories: List<Pair<String, String>>,
    selectedCategory: Pair<String, String>?,
    onCategorySelect: (Pair<String, String>) -> Unit,
    selectedTab: Int,
    onTabSelect: (Int) -> Unit,
    onStorageClick: () -> Unit,
    onBackToHome: () -> Unit,
    onSettingsClick: () -> Unit,
    isSearchExpanded: Boolean,
    onSearchExpandToggle: (Boolean) -> Unit
) {
    Column(modifier = Modifier.background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))).wrapContentHeight()) {
        if (isSearchExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { 
                    onSearchExpandToggle(false)
                    onSearchQueryChange("")
                }) {
                    Icon(Icons.Default.ArrowBack, "Geri", tint = Color.White)
                }
                
                GlassyBox(
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    blurRadius = 12.dp,
                    borderWidth = 1.dp,
                    borderColor = Color.White.copy(alpha = 0.15f)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.fillMaxSize(),
                        placeholder = { Text("Anime Ara...", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearchSubmit() }),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.6f)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = 0.6f))
                                }
                            }
                        }
                    )
                }
            }
        } else {
            GlassyTopAppBar(
                title = { Text("AnimeciX", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White) },
                actions = {
                    IconButton(onClick = { onSearchExpandToggle(true) }) { Icon(Icons.Default.Search, "Ara", tint = Color.White) }
                    IconButton(onClick = onStorageClick) { Icon(Icons.Default.Storage, "Depolama", tint = Color.White) }
                    IconButton(onClick = onBackToHome) { Icon(Icons.Default.Home, "Ana Menü", tint = Color.White) }
                }
            )
        }
        
        // Show categories row only if library mode (4) and search mode are inactive
        if (selectedTab != 4 && !isSearchExpanded) {
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { onCategorySelect(category) },
                        label = { Text(category.first, color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f), fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.Cyan.copy(alpha = 0.2f), 
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        border = BorderStroke(0.5.dp, if (isSelected) Color.Cyan else Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SearchTabContent(
    searchQuery: String,
    searchResults: List<com.myanim.kondi.data.animecix.AnimecixTitle>,
    isSearchLoading: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onAnimeClick: (Int) -> Unit,
    onClearSearch: () -> Unit,
    onLoadMore: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        if (searchQuery.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (searchResults.isNotEmpty()) "\"$searchQuery\" için ${searchResults.size} sonuç" else if (isSearchLoading) "\"$searchQuery\" aranıyor..." else "\"$searchQuery\" için sonuç bulunamadı",
                    color = Color.White, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClearSearch) { Text("Kapat", color = Color.White) }
            }
        }

        if (searchResults.isNotEmpty()) {
            val state = rememberLazyListState()
            LaunchedEffect(state) {
                snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                    .collect { lastIndex -> if (lastIndex == searchResults.size - 1) onLoadMore() }
            }
            SearchList(results = searchResults, sharedTransitionScope = sharedTransitionScope, animatedContentScope = animatedContentScope, onClick = onAnimeClick, state = state)
        } else if (isSearchLoading) {
            val brush = shimmerBrush()
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                items(5) { ShimmerSearchItem(brush) }
            }
        }
    }
}

private fun com.myanim.kondi.data.local.Favorite.toAnimecixVideo() = AnimecixVideo(
    episodeNumber = null,
    seasonNumber = null,
    poster = posterUrl,
    name = title,
    directUrl = url,
    directEpisodeId = null,
    animeId = animeId,
    description = null,
    language = null,
    category = null,
    quality = null
)

@Composable
fun GlassyBottomBar(
    selectedTab: Int,
    onTabSelect: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassyBox(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(64.dp),
            shape = RoundedCornerShape(32.dp),
            blurRadius = 16.dp,
            borderWidth = 1.dp,
            borderColor = Color.White.copy(alpha = 0.15f),
            containerColor = Color.Black.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomBarItem(
                    selected = selectedTab == 0,
                    icon = Icons.Default.Tv,
                    label = "Son Bölümler",
                    onClick = { onTabSelect(0) }
                )
                BottomBarItem(
                    selected = selectedTab == 1,
                    icon = Icons.Default.Favorite,
                    label = "Favoriler",
                    onClick = { onTabSelect(1) }
                )
                BottomBarItem(
                    selected = selectedTab == 4,
                    icon = Icons.Default.VideoLibrary,
                    label = "Kütüphane",
                    onClick = { onTabSelect(4) }
                )
                BottomBarItem(
                    selected = selectedTab == 5,
                    icon = Icons.Default.Settings,
                    label = "Ayarlar",
                    onClick = { onTabSelect(5) }
                )
            }
        }
    }
}

@Composable
fun RowScope.BottomBarItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "iconScale"
    )
    val color = if (selected) Color.Cyan else Color.White.copy(alpha = 0.5f)

    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(38.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (selected) Color.Cyan.copy(alpha = 0.12f) else Color.Transparent
                )
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                )
                
                AnimatedVisibility(
                    visible = selected,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Row {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = color,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
