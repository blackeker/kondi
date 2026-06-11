@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package com.myanim.kondi.ui.hdfilmcehennemi

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.myanim.kondi.data.hdfilmcehennemi.HdFilmCehennemiTitle
import com.myanim.kondi.data.local.Favorite
import com.myanim.kondi.ui.common.*
import com.myanim.kondi.ui.theme.AnimeTheme
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HdFilmCehennemiHomeScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onMovieClick: (String) -> Unit, // URL id as key identifier
    onBackToHome: () -> Unit = {},
    onDownloadsClick: () -> Unit,
    onSnifferClick: () -> Unit,
    onStorageClick: () -> Unit,
    activeTheme: AnimeTheme = AnimeTheme.DEFAULT,
    onThemeChange: (AnimeTheme) -> Unit = {},
    viewModel: HdFilmCehennemiViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val movies by viewModel.latestMovies.collectAsStateWithLifecycle()
    val categoryItems by viewModel.categoryItems.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isSearchLoading by viewModel.isSearchLoading.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        KondiBackground()
 
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                HdFilmCehennemiTopBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { 
                        searchQuery = it
                        if (it.isEmpty()) {
                            viewModel.clearSearch()
                            if (selectedTab == 2) viewModel.selectTab(0)
                        }
                    },
                    onSearchSubmit = {
                        if (searchQuery.isNotBlank()) {
                            viewModel.selectTab(2)
                            viewModel.search(searchQuery)
                        }
                    },
                    categories = viewModel.categories,
                    selectedCategory = selectedCategory,
                    onCategorySelect = { category ->
                        viewModel.selectTab(3)
                        viewModel.selectCategory(category)
                        searchQuery = ""
                        viewModel.clearSearch()
                    },
                    selectedTab = selectedTab,
                    onTabSelect = { 
                        viewModel.selectTab(it)
                        if (it != 2) {
                            searchQuery = ""
                            viewModel.clearSearch()
                        }
                    },
                    onStorageClick = onStorageClick,
                    onBackToHome = onBackToHome,
                    onSettingsClick = { showSettingsDialog = true },
                    isSearchExpanded = isSearchExpanded,
                    onSearchExpandToggle = { expanded ->
                        isSearchExpanded = expanded
                        if (expanded) {
                            viewModel.selectTab(2)
                        } else {
                            viewModel.selectTab(0)
                            searchQuery = ""
                            viewModel.clearSearch()
                        }
                    }
                )
            },
            bottomBar = {
                HdFilmCehennemiBottomBar(
                    selectedTab = selectedTab,
                    onTabSelect = { tab ->
                        viewModel.selectTab(tab)
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
                when (selectedTab) {
                    0 -> { // Latest
                        if (errorMessage != null && movies.isEmpty()) {
                            ErrorState(message = errorMessage!!, onRetry = { viewModel.retryLatestMovies() })
                        } else if (isLoading && movies.isEmpty()) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(8) { ShimmerItem(brush) }
                            }
                        } else {
                            val gridState = rememberLazyGridState()
                            LaunchedEffect(gridState) {
                                snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                                    .collect { lastIndex ->
                                        if (lastIndex == movies.size - 1) viewModel.loadMoreLatest()
                                    }
                            }
                            
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                state = gridState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(movies) { movie ->
                                    HdFilmMovieCard(
                                        movie = movie,
                                        isFavorite = favorites.any { it.url == movie.id },
                                        onClick = { onMovieClick(movie.id) },
                                        onFavoriteClick = { viewModel.toggleFavorite(movie) }
                                    )
                                }
                            }
                        }
                    }
                    1 -> { // Favorites
                        if (favorites.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Henüz favori film/dizi eklenmedi.", color = Color.White.copy(alpha = 0.7f))
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(favorites) { fav ->
                                    val movie = HdFilmCehennemiTitle(fav.url, fav.title, fav.posterUrl)
                                    HdFilmMovieCard(
                                        movie = movie,
                                        isFavorite = true,
                                        onClick = { onMovieClick(movie.id) },
                                        onFavoriteClick = { viewModel.toggleFavorite(movie) }
                                    )
                                }
                            }
                        }
                    }
                    2 -> { // Search
                        if (isSearchLoading) {
                            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                                items(5) { ShimmerSearchItem(brush) }
                            }
                        } else if (searchResults.isEmpty() && searchQuery.isNotEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Sonuç bulunamadı.", color = Color.White.copy(alpha = 0.5f))
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(searchResults) { movie ->
                                    HdFilmListRowCard(
                                        movie = movie,
                                        onClick = { onMovieClick(movie.id) }
                                    )
                                }
                            }
                        }
                    }
                    3 -> { // Categories
                        if (errorMessage != null && categoryItems.isEmpty()) {
                            ErrorState(message = errorMessage!!, onRetry = { viewModel.loadCategoryItems() })
                        } else if (isLoading && categoryItems.isEmpty()) {
                            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                                items(5) { ShimmerSearchItem(brush) }
                            }
                        } else {
                            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                            LaunchedEffect(gridState) {
                                snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                                    .collect { lastIndex ->
                                        if (lastIndex == categoryItems.size - 1) viewModel.loadCategoryItems()
                                    }
                            }
                            
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                state = gridState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(categoryItems) { movie ->
                                    HdFilmMovieCard(
                                        movie = movie,
                                        isFavorite = favorites.any { it.url == movie.id },
                                        onClick = { onMovieClick(movie.id) },
                                        onFavoriteClick = { viewModel.toggleFavorite(movie) }
                                    )
                                }
                            }
                        }
                    }
                    4 -> { // Downloads / Library
                        com.myanim.kondi.ui.common.LibraryScreen(
                            isEmbedded = true
                        )
                    }
                }
            }
        }

        if (showSettingsDialog) {
            SettingsDialog(
                currentTheme = activeTheme,
                onThemeSelect = onThemeChange,
                onDismiss = { showSettingsDialog = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HdFilmCehennemiTopBar(
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
                        placeholder = { Text("Film/Dizi Ara...", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium) },
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
                title = { Text("HDFilmCehennemi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFFE50914)) },
                actions = {
                    IconButton(onClick = { onSearchExpandToggle(true) }) { Icon(Icons.Default.Search, "Ara", tint = Color.White) }
                    IconButton(onClick = onStorageClick) { Icon(Icons.Default.Storage, "Depolama", tint = Color.White) }
                    IconButton(onClick = onBackToHome) { Icon(Icons.Default.Home, "Ana Menü", tint = Color.White) }
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Palette, "Ayarlar", tint = Color.White) }
                }
            )
        }
        
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
                            selectedContainerColor = Color(0xFFE50914).copy(alpha = 0.2f), 
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        border = BorderStroke(0.5.dp, if (isSelected) Color(0xFFE50914) else Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HdFilmCehennemiBottomBar(
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
                .fillMaxWidth(0.9f)
                .height(64.dp),
            shape = RoundedCornerShape(32.dp),
            blurRadius = 16.dp,
            borderWidth = 1.dp,
            borderColor = Color.White.copy(alpha = 0.15f),
            containerColor = Color.Black.copy(alpha = 0.4f)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HdFilmBottomBarItem(
                    selected = selectedTab == 0,
                    icon = Icons.Default.DateRange,
                    label = "Son Filmler",
                    onClick = { onTabSelect(0) }
                )
                HdFilmBottomBarItem(
                    selected = selectedTab == 1,
                    icon = Icons.Default.Favorite,
                    label = "Favoriler",
                    onClick = { onTabSelect(1) }
                )
                HdFilmBottomBarItem(
                    selected = selectedTab == 4,
                    icon = Icons.Default.Download,
                    label = "İndirilenler",
                    onClick = { onTabSelect(4) }
                )
            }
        }
    }
}

@Composable
fun RowScope.HdFilmBottomBarItem(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val color = if (selected) Color(0xFFE50914) else Color.White.copy(alpha = 0.5f)
    
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
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun HdFilmMovieCard(
    movie: HdFilmCehennemiTitle,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    GlassyCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        shape = RoundedCornerShape(16.dp),
        borderColor = Color(0xFFE50914).copy(alpha = 0.25f),
        containerColor = Color.Black.copy(alpha = 0.4f),
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                AsyncImage(
                    model = movie.poster,
                    contentDescription = movie.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Favorite indicator icon overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    IconButton(
                        onClick = onFavoriteClick,
                        modifier = Modifier.size(32.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favori",
                            tint = if (isFavorite) Color(0xFFE50914) else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // IMDB score tag
                movie.rating?.let { rating ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Surface(
                            color = Color(0xFFFFB300),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.wrapContentSize()
                        ) {
                            Text(
                                text = rating,
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = movie.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
                
                movie.year?.let { year ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = year,
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}

@Composable
fun HdFilmListRowCard(
    movie: HdFilmCehennemiTitle,
    onClick: () -> Unit
) {
    GlassyCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        shape = RoundedCornerShape(16.dp),
        borderColor = Color(0xFFE50914).copy(alpha = 0.25f),
        containerColor = Color.Black.copy(alpha = 0.4f),
        onClick = onClick
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .fillMaxHeight()
            ) {
                AsyncImage(
                    model = movie.poster,
                    contentDescription = movie.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = movie.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
