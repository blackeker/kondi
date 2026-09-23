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

            // 2. VIP Server & Health Telemetry Banner
            item {
                ServerTelemetryStrip(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }

            // 3. Spotlight Featured Banner (Canlı Simulcast / Vitrin Anime)
            if (spotlightHero != null) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "GÜNÜN ÖNE ÇIKANI",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NeonMagenta,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Haftanın Anime Seçimi",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "1080p FHD",
                                fontSize = 11.sp,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        SpotlightHeroBanner(
                            title = spotlightHero.name ?: "Anime Seçimi",
                            posterUrl = spotlightHero.poster,
                            backdropUrl = spotlightHero.poster,
                            quality = "1080p FHD",
                            rating = "9.9",
                            episodeText = if (spotlightHero.episodeNumber != null) "EP ${spotlightHero.episodeNumber} • YENİ" else "SİMULCAST",
                            synopsis = "En son Animecix bölümü yayında. Kesintisiz Türkçe altyazılı izleyin.",
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
                            }
                        )
                    }
                }
            }

            // 4. Continue Watching Shelf (Son İzlenenler)
            if (watchHistory.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 10.dp, bottom = 14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "İzlemeye Devam Et",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(watchHistory, key = { index, item -> "hub_hist_${item.videoUrl}_$index" }) { index, item ->
                                LuxuryGlassCard(
                                    modifier = Modifier
                                        .width(220.dp)
                                        .height(130.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    onClick = {
                                        onVideoClick(item.videoUrl, item.title)
                                    }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
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
                                                            ObsidianBlack.copy(alpha = 0.90f)
                                                        )
                                                    )
                                                )
                                        )

                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(ElectricPink.copy(alpha = 0.85f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "DEVAM ET",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White
                                                )
                                            }

                                            Column {
                                                Text(
                                                    text = item.title,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Son İzlenen",
                                                    fontSize = 10.sp,
                                                    color = TextMed
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

            // 5. Dynamic Animecix Categories Deck
            if (viewModel.categories.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 10.dp, bottom = 12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "KEŞFET",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NeonCyan,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Anime Türleri & Kategoriler",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Text(
                                text = "Tümünü Gör",
                                fontSize = 12.sp,
                                color = NeonMagenta,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onExploreAllClick() }
                            )
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
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

            // 6. Latest Simulcast Episodes Shelf (Son Eklenen Anime Bölümleri)
            item {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "CANLI SİMULCAST",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = ElectricPink,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Yeni Eklenen Bölümler",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        if (isLoading) {
                            CircularProgressIndicator(
                                color = NeonCyan,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Tümü",
                                fontSize = 12.sp,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onExploreAllClick() }
                            )
                        }
                    }

                    if (latestEpisodes.isEmpty() && isLoading) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(4) {
                                Box(
                                    modifier = Modifier
                                        .width(160.dp)
                                        .height(260.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                )
                            }
                        }
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(latestEpisodes, key = { "hub_ep_${it.url ?: it.name ?: it.hashCode()}_${latestEpisodes.indexOf(it)}" }) { video ->
                                val videoUrl = video.url
                                val animeId = video.animeId
                                MediaPosterCard(
                                    title = video.name ?: "Anime Bölümü",
                                    posterUrl = video.poster,
                                    quality = "1080p FHD",
                                    episodeText = if (video.episodeNumber != null) "EP ${video.episodeNumber}" else "YENİ",
                                    rating = "9.8",
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
                                    modifier = Modifier.width(160.dp)
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
                        text = "ANIMECIX",
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
                    ) {
                        Text(
                            text = "PRIME",
                            color = NeonMagenta,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Text(
                    text = "Resmi Anime & Simulcast Akışı",
                    fontSize = 11.sp,
                    color = TextMed,
                    fontWeight = FontWeight.Medium
                )
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

/**
 * Server Telemetry & Status Strip
 */
@Composable
private fun ServerTelemetryStrip(
    modifier: Modifier = Modifier
) {
    LuxuryGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White.copy(alpha = 0.03f),
        borderColor = Color.White.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlowingStatusBadge(status = ProviderStatus.ONLINE, customText = "ANIMECIX AKTİF")
                Text(
                    text = "• Canlı Simulcast",
                    fontSize = 11.sp,
                    color = TextMed,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "12ms • 10G Hat",
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

