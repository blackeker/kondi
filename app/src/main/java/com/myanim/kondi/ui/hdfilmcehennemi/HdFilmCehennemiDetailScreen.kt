@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package com.myanim.kondi.ui.hdfilmcehennemi

import android.widget.Toast
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.myanim.kondi.data.hdfilmcehennemi.HdFilmCehennemiDetail
import com.myanim.kondi.data.hdfilmcehennemi.HdFilmCehennemiEpisode
import com.myanim.kondi.data.hdfilmcehennemi.HdFilmCehennemiSource
import com.myanim.kondi.data.download.VideoDownloadManager
import com.myanim.kondi.ui.common.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HdFilmCehennemiDetailScreen(
    movieId: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onBackClick: () -> Unit,
    onVideoClick: (String, String) -> Unit, // url, title
    viewModel: HdFilmCehennemiDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val detail by viewModel.movieDetail.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val isSourcesLoading by viewModel.isSourcesLoading.collectAsStateWithLifecycle()

    var showSourceDialog by remember { mutableStateOf(false) }
    var activeEpisodeForSource by remember { mutableStateOf<HdFilmCehennemiEpisode?>(null) }
    var selectedEpisodeTitle by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    val downloadManager = remember { VideoDownloadManager.getInstance(context) }

    LaunchedEffect(movieId) {
        viewModel.loadMovieDetails(movieId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        KondiBackground()

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GlassyTopAppBar(
                    title = { 
                        Text(
                            text = detail?.title ?: "Yükleniyor...", 
                            color = Color.White, 
                            fontWeight = FontWeight.Bold, 
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { detail?.let { viewModel.toggleFavorite(it) } }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favori",
                                tint = if (isFavorite) Color(0xFFE50914) else Color.White
                            )
                        }
                    }
                )
            }
        ) { padding ->
            if (errorMessage != null && detail == null) {
                ErrorState(message = errorMessage!!, onRetry = { viewModel.loadMovieDetails(movieId) })
            } else if (isLoading && detail == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFE50914))
                }
            } else {
                detail?.let { info ->
                    LazyColumn(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                            ) {
                                AsyncImage(
                                    model = info.poster,
                                    contentDescription = info.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        item {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = info.title,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    info.rating?.let { rating ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Star, "IMDB", tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(rating, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    }

                                    info.year?.let { year ->
                                        Text(year, color = Color.LightGray, fontSize = 14.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                if (!info.isSerie) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                activeEpisodeForSource = info.episodes.firstOrNull()
                                                selectedEpisodeTitle = "Seçenekleri Yükle: ${info.title}"
                                                activeEpisodeForSource?.let { viewModel.loadVideoSources(it.url) }
                                                showSourceDialog = true
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Oynat", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        if (!info.description.isNullOrBlank()) {
                            item {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text("Özet", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(info.description, color = Color.LightGray, fontSize = 14.sp)
                                }
                            }
                        }

                        if (info.genres != null && info.genres.isNotEmpty()) {
                            item {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text("Türler", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(info.genres.joinToString(", "), color = Color.LightGray, fontSize = 14.sp)
                                }
                            }
                        }

                        if (info.isSerie) {
                            item {
                                Text(
                                    "Bölümler",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            items(info.episodes) { episode ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .clickable {
                                            activeEpisodeForSource = episode
                                            selectedEpisodeTitle = episode.name
                                            viewModel.loadVideoSources(episode.url)
                                            showSourceDialog = true
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = episode.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                        Icon(Icons.Default.PlayArrow, null, tint = Color.White.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showSourceDialog) {
            ModalBottomSheet(
                onDismissRequest = { showSourceDialog = false },
                containerColor = Color(0xFF0F080C)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = selectedEpisodeTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (isSourcesLoading) {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFFE50914))
                        }
                    } else if (sources.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text("Oynatılabilir kaynak bulunamadı.", color = Color.LightGray)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                            items(sources) { source ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    scope.launch {
                                                        Toast.makeText(context, "Kaynak çözülüyor...", Toast.LENGTH_SHORT).show()
                                                        val resolved = viewModel.resolveSource(source.url)
                                                        if (resolved != null) {
                                                            onVideoClick(resolved, detail?.title ?: "Film")
                                                        } else {
                                                            Toast.makeText(context, "Kaynak çözülemedi!", Toast.LENGTH_SHORT).show()
                                                        }
                                                        showSourceDialog = false
                                                    }
                                                }
                                        ) {
                                            Text(source.name, color = Color.White, fontWeight = FontWeight.Bold)
                                        }

                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    Toast.makeText(context, "Kaynak çözülüyor...", Toast.LENGTH_SHORT).show()
                                                    val resolved = viewModel.resolveSource(source.url)
                                                    if (resolved != null) {
                                                        val safeTitle = com.myanim.kondi.data.download.DownloadUtils.sanitizeFileName(
                                                            "${detail?.title ?: "Film"}_${activeEpisodeForSource?.name ?: "Bölüm"}.mp4"
                                                        )
                                                        downloadManager.startDownload(
                                                            title = safeTitle,
                                                            url = resolved,
                                                            source = "HDFILMCEHENNEMI",
                                                            forceDisplayName = true
                                                        )
                                                        Toast.makeText(context, "İndirme başlatıldı", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Kaynak çözülemedi!", Toast.LENGTH_SHORT).show()
                                                    }
                                                    showSourceDialog = false
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.Download, "İndir", tint = Color(0xFFE50914))
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
