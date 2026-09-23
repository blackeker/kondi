package com.myanim.kondi.ui.animecix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.myanim.kondi.data.animecix.AnimecixTitle
import com.myanim.kondi.data.animecix.AnimecixVideo
import com.myanim.kondi.data.local.WatchHistory
import com.myanim.kondi.ui.common.*

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
    val categoryItems by viewModel.categoryItems.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val watchHistory by viewModel.watchHistory.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isCategoryLoading by viewModel.isCategoryLoading.collectAsStateWithLifecycle()
    val isSearchLoading by viewModel.isSearchLoading.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }

    // Debounced search logic
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            kotlinx.coroutines.delay(400L)
            viewModel.search(searchQuery)
        } else {
            viewModel.clearSearch()
        }
    }

    val brush = shimmerBrush()
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060608)) // Immersive dark background
    ) {
        // Glowing Ambient Blobs (Neon lighting behind the glass elements)
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .offset(x = 80.dp, y = (-80).dp)
                .blur(80.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.25f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-60).dp, y = 100.dp)
                .blur(80.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(secondaryColor.copy(alpha = 0.18f), Color.Transparent)
                    )
                )
        )

        // Main Layout Container
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Premium Floating Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Kondi",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Anime Dünyasını Keşfet",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Glassy Storage Button
                    GlassyCard(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        onClick = onStorageClick
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = "Depolama",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Glassy Settings Button
                    GlassyCard(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        onClick = onSettingsClick
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Ayarlar",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Glassy Floating Search Pill
            GlassyCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .height(54.dp),
                shape = CapsuleShape,
                borderColor = Color.White.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "En sevdiğin animeyi ara...",
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = 15.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Temizle",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Animated Screen Switching based on search/category states
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    // Search results mode
                    searchQuery.isNotBlank() -> {
                        if (isSearchLoading && searchResults.isEmpty()) {
                            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
                                items(5) { ShimmerSearchItem(brush) }
                            }
                        } else if (searchResults.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Arama sonucu bulunamadı.", color = Color.Gray, fontSize = 15.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(searchResults, key = { "search_${it.id ?: it.name ?: it.hashCode()}_${searchResults.indexOf(it)}" }) { item ->
                                    AnimeGlassyRowCard(title = item, onClick = { item.id?.let(onAnimeClick) })
                                }
                            }
                        }
                    }
                    
                    // Category results mode
                    selectedCategory != null -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlassyCard(
                                    modifier = Modifier.size(36.dp),
                                    shape = CircleShape,
                                    onClick = { viewModel.clearSearch() }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = selectedCategory?.first ?: "",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            if (isCategoryLoading && categoryItems.isEmpty()) {
                                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
                                    items(5) { ShimmerSearchItem(brush) }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(categoryItems, key = { "cat_${it.id ?: it.name ?: it.hashCode()}_${categoryItems.indexOf(it)}" }) { item ->
                                        AnimeGlassyRowCard(title = item, onClick = { item.id?.let(onAnimeClick) })
                                    }
                                }
                            }
                        }
                    }

                    // Dashboard dashboard mode (Default)
                    else -> {
                        val mainListState = rememberLazyListState()
                        LaunchedEffect(mainListState) {
                            snapshotFlow { mainListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                                .collect { lastIndex ->
                                    val totalItemsCount = mainListState.layoutInfo.totalItemsCount
                                    if (lastIndex != null && lastIndex >= totalItemsCount - 4 && totalItemsCount > 0) {
                                        viewModel.loadMoreLatest()
                                    }
                                }
                        }
                        
                        LazyColumn(
                            state = mainListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            
                            // 1. Continue Watching Section (İzlemeye Devam Et - On Top of Home!)
                            if (watchHistory.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "İzlemeye Devam Et",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 12.dp)
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        itemsIndexed(watchHistory, key = { index, history -> "hist_${history.videoUrl}_$index" }) { index, history ->
                                            GlassyContinueWatchingCard(history = history, onPlayClick = onVideoClick)
                                        }
                                    }
                                }
                            }

                            // 2. Spotlight Spotlight / Featured Card
                            if (episodes.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Öne Çıkan Bölüm",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp)
                                    )
                                    SpotlightCard(
                                        video = episodes.first(),
                                        onClick = { episodes.first().animeId?.let(onAnimeClick) }
                                    )
                                }
                            }

                            // 3. Horizontal Categories (Türler)
                            item {
                                Text(
                                    text = "Türler",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(viewModel.categories) { category ->
                                        GlassyCategoryChip(
                                            category = category,
                                            onClick = { viewModel.selectCategory(category) }
                                        )
                                    }
                                }
                            }

                            // 4. Latest Episodes Header
                            item {
                                Text(
                                    text = "Son Eklenen Bölümler",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 12.dp)
                                )
                            }

                            // 5. Latest Episodes Grid/List of glassy cards
                            if (isLoading && episodes.isEmpty()) {
                                items(4) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp)
                                            .padding(horizontal = 20.dp, vertical = 6.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(brush)
                                    )
                                }
                            } else {
                                // Exclude the first episode since it is in Spotlight
                                val remainingEpisodes = if (episodes.size > 1) episodes.drop(1) else episodes
                                items(remainingEpisodes, key = { "ep_${it.url ?: it.name ?: it.hashCode()}_${remainingEpisodes.indexOf(it)}" }) { video ->
                                    GlassyEpisodeCard(
                                        video = video,
                                        onClick = { video.animeId?.let(onAnimeClick) }
                                    )
                                }
                                
                                if (isLoading) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = MaterialTheme.colorScheme.primary,
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
        }
    }
}

@Composable
fun GlassyCategoryChip(
    category: Pair<String, String>,
    onClick: () -> Unit
) {
    GlassyCard(
        shape = CapsuleShape,
        containerColor = Color.White.copy(alpha = 0.05f),
        borderColor = Color.White.copy(alpha = 0.08f),
        onClick = onClick
    ) {
        Text(
            text = category.first,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun GlassyContinueWatchingCard(
    history: WatchHistory,
    onPlayClick: (String, String) -> Unit
) {
    GlassyCard(
        modifier = Modifier.width(180.dp),
        shape = RoundedCornerShape(18.dp),
        containerColor = Color.White.copy(alpha = 0.03f),
        borderColor = Color.White.copy(alpha = 0.06f),
        onClick = { onPlayClick(history.videoUrl, history.title) }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            ) {
                AsyncImage(
                    model = history.posterUrl ?: "file:///android_asset/placeholder.png",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Frosted Play Glassy Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Oynat",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Progress Bar
                if (history.durationMs > 0) {
                    val ratio = history.positionMs.toFloat() / history.durationMs.toFloat()
                    LinearProgressIndicator(
                        progress = { ratio.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            }
            Text(
                text = history.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                color = Color.White
            )
        }
    }
}

@Composable
fun SpotlightCard(
    video: AnimecixVideo,
    onClick: () -> Unit
) {
    GlassyCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        borderColor = Color.White.copy(alpha = 0.15f),
        onClick = onClick
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = video.poster,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Rich Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
            )

            // Spotlight details
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "YENİ BÖLÜM",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                
                Text(
                    text = video.name ?: "Bilinmeyen Anime",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = "${video.episodeNumber ?: 0}. Bölüm yayında",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun GlassyEpisodeCard(
    video: AnimecixVideo,
    onClick: () -> Unit
) {
    GlassyCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        containerColor = Color.White.copy(alpha = 0.02f),
        borderColor = Color.White.copy(alpha = 0.08f),
        onClick = onClick
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
            ) {
                AsyncImage(
                    model = video.poster,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = video.name ?: "Bilinmeyen Anime",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "${video.episodeNumber ?: 0}. Bölüm",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AnimeGlassyRowCard(
    title: AnimecixTitle,
    onClick: () -> Unit
) {
    GlassyCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp),
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White.copy(alpha = 0.03f),
        borderColor = Color.White.copy(alpha = 0.08f),
        onClick = onClick
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = title.poster,
                contentDescription = null,
                modifier = Modifier
                    .width(64.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title.name ?: "Bilinmeyen Anime",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
