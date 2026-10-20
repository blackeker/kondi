package com.myanim.kondi.ui.animecix

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.myanim.kondi.data.animecix.AnimecixAnime
import com.myanim.kondi.data.animecix.AnimecixVideo
import com.myanim.kondi.ui.common.*
import com.myanim.kondi.ui.theme.*

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
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()

    LaunchedEffect(animeId) {
        viewModel.loadAnimeDetails(animeId)
        viewModel.checkFavoriteStatus(animeId)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = anime?.title ?: "Detaylar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = OnSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Geri",
                            tint = OnSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { anime?.let { viewModel.toggleFavorite(it) } }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favori",
                            tint = if (isFavorite) Color.Red else OnSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceCard
                )
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
                    val episodes by remember(details) {
                        derivedStateOf {
                            (details.videos ?: emptyList()).filter { video ->
                                val cat = video.category?.lowercase() ?: ""
                                cat != "trailer" &&
                                cat != "opening credits" &&
                                cat != "ending credits" &&
                                (video.episodeNumber ?: 0) > 0
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header with poster and info
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                AnimePoster(url = details.poster, size = 100.dp)

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

                                    if (details.rating != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = Color.Yellow,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = details.rating!!,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            if (details.description != null && details.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = details.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Episodes section
                        if (episodes.isNotEmpty()) {
                            SectionHeader(
                                title = "Bölümler (${episodes.size})",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                            )

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                items(episodes, key = { it.url ?: it.name ?: it.hashCode().toString() }) { video ->
                                    SimpleCard(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                        onClick = {
                                            val sourceUrl = video.url ?: ""
                                            val title = "${details.title} - Bölüm ${video.episodeNumber ?: 0}"
                                            onVideoClick(sourceUrl, title)
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AnimePoster(url = video.poster ?: details.poster, size = 70.dp)

                                            Spacer(modifier = Modifier.width(16.dp))

                                            Column {
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

                                            Spacer(modifier = Modifier.weight(1f))

                                            Icon(
                                                imageVector = Icons.Default.PlayCircleOutline,
                                                contentDescription = "Oynat",
                                                tint = Primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            EmptyState(
                                icon = Icons.Default.VideoLibrary,
                                title = "Bölüm bulunamadı",
                                subtitle = "Bu anime için bölüm listesi boş",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
