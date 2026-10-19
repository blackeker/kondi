package com.myanim.kondi.ui.hub

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.myanim.kondi.data.animecix.AnimecixVideo
import com.myanim.kondi.data.model.ProviderStatus
import com.myanim.kondi.ui.animecix.AnimecixViewModel
import com.myanim.kondi.ui.common.*
import com.myanim.kondi.ui.theme.*

@Composable
fun ProviderHubScreen(
    viewModel: AnimecixViewModel = viewModel(),
    onAnimeClick: (Int) -> Unit,
    onVideoClick: (String, String) -> Unit,
    onSearchClick: () -> Unit,
    onStorageClick: () -> Unit,
    onExploreAllClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val latestEpisodes by viewModel.latestEpisodes.collectAsStateWithLifecycle()
    val watchHistory by viewModel.watchHistory.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val favoriteUrls = remember(favorites) { favorites.map { it.url }.toSet() }

    val spotlightHero = remember(latestEpisodes) {
        latestEpisodes.firstOrNull()
    }

    ObsidianGlowBackground(
        modifier = modifier,
        primaryGlow = NeonMagenta,
        secondaryGlow = NeonCyan
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp)
        ) {
            // 1. Minimalist VIP Header (Animecix Prime)
            item {
                HubHeader(
                    onSearchClick = onSearchClick,
                    onStorageClick = onStorageClick,
                    onSettingsClick = onSettingsClick
                )
            }


            // 3. Spotlight Featured Banner
            if (spotlightHero != null) {
                item {
                    SpotlightHeroBanner(
                        title = spotlightHero.name ?: "Anime Seçimi",
                        posterUrl = spotlightHero.poster,
                        backdropUrl = spotlightHero.poster,
                        episodeText = if (spotlightHero.episodeNumber != null) "${spotlightHero.episodeNumber}. Bölüm" else "Yeni",
                        onPlayClick = {
                            val heroAnimeId = spotlightHero.animeId
                            val heroVideoUrl = spotlightHero.url
                            if (heroAnimeId != null) {
                                onAnimeClick(heroAnimeId)
                            } else if (heroVideoUrl != null) {
                                onVideoClick(heroVideoUrl, spotlightHero.name ?: "Anime")
                            }
                        },
                        onDetailsClick = {
                            spotlightHero.animeId?.let { onAnimeClick(it) }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            // 4. Continue Watching Shelf (İzlemeye Devam Et)
            if (watchHistory.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "İzlemeye Devam Et",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(watchHistory, key = { index, item -> "hub_hist_${item.videoUrl}_$index" }) { index, item ->
                                Column(
                                    modifier = Modifier
                                        .width(180.dp)
                                        .bounceClick()
                                        .clickable {
                                            onVideoClick(item.videoUrl, item.title)
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(105.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(GlassSurface)
                                            .border(0.8.dp, GlassBorderSubtle, RoundedCornerShape(14.dp))
                                    ) {
                                        AsyncImage(
                                            model = item.posterUrl ?: "file:///android_asset/placeholder.png",
                                            contentDescription = item.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )

                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            Color.Transparent,
                                                            Color.Black.copy(alpha = 0.75f)
                                                        )
                                                    )
                                                )
                                        )

                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.6f)),
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

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = item.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Dynamic Animecix Categories Deck
            if (viewModel.categories.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Kategoriler",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Text(
                                text = "Tümü",
                                fontSize = 12.sp,
                                color = TextMed,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { onExploreAllClick() }
                            )
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            items(viewModel.categories) { cat ->
                                NeonCategoryChip(
                                    text = cat.first,
                                    isSelected = false,
                                    onClick = {
                                        viewModel.selectCategory(cat)
                                        onExploreAllClick()
                                    },
                                    activeColor = NeonCyan
                                )
                            }
                        }
                    }
                }
            }

            // 6. Latest Episodes Shelf
            item {
                Column(modifier = Modifier.padding(vertical = 10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Yeni Bölümler",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        if (isLoading) {
                            CircularProgressIndicator(
                                color = NeonCyan,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Tümü",
                                fontSize = 12.sp,
                                color = TextMed,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { onExploreAllClick() }
                            )
                        }
                    }

                    if (latestEpisodes.isEmpty() && isLoading) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(4) {
                                Box(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .height(210.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                )
                            }
                        }
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(latestEpisodes, key = { "hub_ep_${it.url ?: it.name ?: it.hashCode()}_${latestEpisodes.indexOf(it)}" }) { video ->
                                val videoUrl = video.url
                                val animeId = video.animeId
                                MediaPosterCard(
                                    title = video.name ?: "Anime Bölümü",
                                    posterUrl = video.poster,
                                    episodeText = if (video.episodeNumber != null) "${video.episodeNumber}. Bölüm" else "Yeni",
                                    isFavorite = videoUrl != null && favoriteUrls.contains(videoUrl),
                                    onFavoriteToggle = {
                                        viewModel.toggleFavorite(video)
                                    },
                                    onClick = {
                                        if (animeId != null) {
                                            onAnimeClick(animeId)
                                        } else if (videoUrl != null) {
                                            onVideoClick(videoUrl, video.name ?: "Anime")
                                        }
                                    },
                                    modifier = Modifier.width(140.dp)
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
 * Minimalist VIP Media Hub Header (Animecix Exclusive)
 */
@Composable
private fun HubHeader(
    onSearchClick: () -> Unit,
    onStorageClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title & Glowing Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(NeonCyan, NeonMagenta)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Kondi",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NeonMagenta.copy(alpha = 0.2f))
                            .border(0.5.dp, NeonMagenta, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) 
                }
                
            }
        }

        // Action Buttons: Search + Storage + VIP Profile / Settings
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search Button
            LuxuryGlassCard(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                onClick = onSearchClick
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Ara",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // Storage / Downloads Pill
            LuxuryGlassCard(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                onClick = onStorageClick
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Depolama",
                        tint = NeonCyan,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // VIP Profile / Settings Button
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable { onSettingsClick() }
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(
                        BorderStroke(
                            1.5.dp,
                            Brush.sweepGradient(listOf(NeonCyan, NeonMagenta, ElectricPink, NeonCyan))
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Ayarlar & Profil",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}


