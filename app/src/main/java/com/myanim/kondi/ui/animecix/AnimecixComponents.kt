@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package com.myanim.kondi.ui.animecix

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.myanim.kondi.data.animecix.AnimecixVideo
import com.myanim.kondi.data.animecix.AnimecixTitle
import com.myanim.kondi.data.local.Favorite
import com.myanim.kondi.data.local.WatchHistory
import com.myanim.kondi.ui.common.GlassyCard

@Composable
fun SearchList(
    results: List<AnimecixTitle>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onClick: (Int) -> Unit,
    state: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
) {
    var sortMode by remember { mutableStateOf(SortMode.DEFAULT) }
    val sortedResults = remember(results, sortMode) {
        when (sortMode) {
            SortMode.DEFAULT -> results
            SortMode.AZ -> results.sortedBy { it.name ?: "" }
            SortMode.ZA -> results.sortedByDescending { it.name ?: "" }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Arama Sonuçları",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    AssistChip(
                        onClick = { showMenu = true },
                        label = { Text(sortMode.displayName, color = Color.Cyan, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Sort, null, tint = Color.Cyan, modifier = Modifier.size(16.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp)
                    )
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.9f))
                    ) {
                        SortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.displayName, color = Color.White) },
                                onClick = {
                                    sortMode = mode
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        items(sortedResults, key = { it.id ?: it.hashCode() }) { result ->
            val animeId = result.id ?: 0
            GlassyCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                shape = RoundedCornerShape(16.dp),
                onClick = if(animeId != 0) { { onClick(animeId) } } else null
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = result.poster,
                        contentDescription = null,
                        modifier = Modifier
                            .width(80.dp)
                            .fillMaxHeight(),
                        contentScale = ContentScale.Crop
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(12.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Top
                    ) {
                        Text(
                            text = result.name ?: "Bilinmeyen Anime",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnimeList(
    videos: List<AnimecixVideo>,
    favorites: List<Favorite>,
    watchHistory: List<WatchHistory> = emptyList(),
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onClick: (Int) -> Unit,
    onVideoClick: (String, String) -> Unit = { _, _ -> },
    onFavoriteToggle: (AnimecixVideo) -> Unit,
    state: LazyGridState = rememberLazyGridState()
) {
    val favoriteUrls = remember(favorites) { favorites.map { it.url }.toSet() }
    var sortMode by remember { mutableStateOf(SortMode.DEFAULT) }
    
    val sortedVideos = remember(videos, sortMode) {
        when (sortMode) {
            SortMode.DEFAULT -> videos
            SortMode.AZ -> videos.sortedBy { it.name ?: "" }
            SortMode.ZA -> videos.sortedByDescending { it.name ?: "" }
        }
    }

    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (watchHistory.isNotEmpty() && sortedVideos.isNotEmpty() && sortedVideos[0].episodeNumber != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ContinueWatchingRow(
                    history = watchHistory,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    onItemClick = onVideoClick
                )
            }
        }

        // Header Row with Sort option
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (sortedVideos.isNotEmpty() && sortedVideos[0].episodeNumber != null) "Son Eklenenler" else "Animeler",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    AssistChip(
                        onClick = { showMenu = true },
                        label = { Text(sortMode.displayName, color = Color.Cyan, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Sort, null, tint = Color.Cyan, modifier = Modifier.size(16.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp)
                    )
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.9f))
                    ) {
                        SortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.displayName, color = Color.White) },
                                onClick = {
                                    sortMode = mode
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        items(sortedVideos, key = { it.url ?: it.hashCode() }) { video ->
            val isFavorite = remember(favoriteUrls, video.url) { favoriteUrls.contains(video.url) }
            GlassyCard(
                modifier = Modifier
                    .padding(6.dp)
                    .fillMaxWidth()
                    .height(280.dp),
                shape = RoundedCornerShape(16.dp),
                onClick = video.animeId?.let { id -> { onClick(id) } }
            ) {
                Column {
                    Box(modifier = Modifier.weight(1f)) {
                        AsyncImage(
                            model = video.poster,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)), startY = 300f))
                        )
                        
                        IconButton(
                            onClick = { onFavoriteToggle(video) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.Favorite,
                                contentDescription = "Favori",
                                tint = if (isFavorite) Color.Red else Color.White
                            )
                        }
                    }
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = video.name ?: "Bölüm Bilinmiyor",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (video.episodeNumber != null) {
                            Text(
                                text = "Bölüm ${video.episodeNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContinueWatchingRow(
    history: List<WatchHistory>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onItemClick: (String, String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            "İzlemeye Devam Et",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(history, key = { it.videoUrl }) { item ->
                GlassyCard(
                    modifier = Modifier
                        .width(220.dp)
                        .height(140.dp),
                    shape = RoundedCornerShape(12.dp),
                    onClick = { onItemClick(item.videoUrl, item.title) }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = item.posterUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                        ) {
                            Text(
                                item.title,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val progress = if (item.durationMs > 0) item.positionMs.toFloat() / item.durationMs else 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class SortMode(val displayName: String) {
    DEFAULT("Varsayılan"),
    AZ("A-Z"),
    ZA("Z-A")
}
