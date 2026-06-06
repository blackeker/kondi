package com.myanim.kondi.ui.animecix

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.myanim.kondi.data.animecix.AnimecixAnime
import com.myanim.kondi.data.animecix.AnimecixSource
import com.myanim.kondi.data.animecix.AnimecixVideo
import com.myanim.kondi.data.local.Download
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.myanim.kondi.ui.common.*

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DetailHeader(
    details: AnimecixAnime,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onTrailerClick: (String, String) -> Unit,
    onMarkWatchedClick: () -> Unit
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        ) {
            // Blurred Background Banner
            AsyncImage(
                model = details.poster,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp)
                    .graphicsLayer(alpha = 0.4f),
                contentScale = ContentScale.Crop
            )
            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black
                            )
                        )
                    )
            )
            
            // Floating Poster & Info
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Floating Glassy Poster Card
                Card(
                    modifier = Modifier
                        .width(100.dp)
                        .height(145.dp)
                        .border(1.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    AsyncImage(
                        model = details.poster,
                        contentDescription = details.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Title and Quick Stats on the right
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = details.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Metadata badges
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        details.year?.let { y ->
                            Surface(
                                color = Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = y.toString(),
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        details.rating?.let { r ->
                            if (r.isNotBlank() && r != "0" && r != "0.0") {
                                Surface(
                                    color = Color(0xFFFFC107).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.5.dp, Color(0xFFFFC107).copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Rating",
                                            tint = Color(0xFFFFC107),
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = r,
                                            color = Color(0xFFFFC107),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        
                        if (details.seasonCount > 0) {
                            Surface(
                                color = Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${details.seasonCount} Sezon",
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Column(modifier = Modifier.padding(16.dp)) {
            // Genre labels
            if (!details.genres.isNullOrEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(details.genres.size) { index ->
                        val genre = details.genres[index]
                        Surface(
                            color = Color(0xFFFF2E93).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.5.dp, Color(0xFFFF2E93).copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = genre.name,
                                color = Color(0xFFFF2E93),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                text = details.description ?: "Açıklama yok.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(20.dp))
            
            // Side-by-side premium action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val hasTrailer = !details.trailerUrl.isNullOrBlank()
                val btnWeight = if (hasTrailer) 0.5f else 1f
                
                if (hasTrailer) {
                    Button(
                        onClick = { onTrailerClick(details.trailerUrl, "Fragman - ${details.title}") },
                        modifier = Modifier.weight(btnWeight).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2E93)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Fragman", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                
                Button(
                    onClick = onMarkWatchedClick,
                    modifier = Modifier.weight(1f - (if (hasTrailer) 0.5f else 0f)).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("İzledim İşaretle", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            if (!details.credits.isNullOrEmpty()) {
                Text(
                    text = "Oyuncular",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(details.credits.size) { index ->
                        val credit = details.credits[index]
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(90.dp)
                        ) {
                            AsyncImage(
                                model = credit.poster,
                                contentDescription = credit.name,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .size(70.dp)
                                    .background(Color.White.copy(alpha = 0.1f)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = credit.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodeItem(
    video: AnimecixVideo,
    downloadStatus: String?,
    animeTitle: String,
    selectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val containerBg = when {
        isSelected -> Color.White.copy(alpha = 0.22f)
        downloadStatus == "DOWNLOADING" || downloadStatus == "PENDING" -> Color(0xFFFF2E93).copy(alpha = 0.06f)
        else -> Color.White.copy(alpha = 0.05f)
    }
    val containerBorderColor = when {
        isSelected -> Color.White.copy(alpha = 0.4f)
        downloadStatus == "DOWNLOADING" || downloadStatus == "PENDING" -> Color(0xFFFF2E93).copy(alpha = 0.25f)
        else -> Color.White.copy(alpha = 0.1f)
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(containerBg)
            .border(1.dp, containerBorderColor, RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                if (selectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelection() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color.White,
                            uncheckedColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                } else {
                    val posterUrl = video.poster
                    if (!posterUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .width(90.dp)
                                .height(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = posterUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // Semi-transparent overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.25f))
                            )
                            // Episode number badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(4.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = (video.episodeNumber ?: "?").toString(),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.05f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (video.episodeNumber ?: "?").toString(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            headlineContent = { 
                Text(
                    video.name ?: "Bölüm ${video.episodeNumber}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                ) 
            },
            supportingContent = { 
                Column {
                    video.description?.let { 
                        Text(
                            it, 
                            color = Color.White.copy(alpha = 0.5f), 
                            maxLines = 1, 
                            style = MaterialTheme.typography.bodySmall,
                            overflow = TextOverflow.Ellipsis
                        ) 
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        video.quality?.let { 
                            Surface(
                                color = Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    it, 
                                    color = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        video.language?.let { 
                            Surface(
                                color = Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    it.uppercase(), 
                                    color = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            },
            trailingContent = { 
                when (downloadStatus) {
                    "COMPLETED" -> {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFF00C853).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "İndirildi",
                                tint = Color(0xFF00C853),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    "DOWNLOADING", "PENDING" -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = Color(0xFFFF2E93)
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSelectionBottomSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    title: String,
    sourcesList: List<com.myanim.kondi.data.animecix.AnimecixSource>,
    sourceSizes: Map<String, Long>,
    onResolveSize: (String) -> Unit,
    onPlayClick: (String) -> Unit,
    onDownloadClick: (com.myanim.kondi.data.animecix.AnimecixSource) -> Unit
) {
    if (show) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = Color(0xFF0F0F0F).copy(alpha = 0.95f),
            scrimColor = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(
                        text = title, 
                        style = MaterialTheme.typography.headlineSmall, 
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        text = "Kaynak seçimi yapın",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    items(sourcesList.size) { index ->
                        val source = sourcesList[index]
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onPlayClick(source.url) }
                        ) {
                            // Background layer
                            Box(
                                modifier = Modifier.matchParentSize().glassmorphismLayout(
                                    shape = RoundedCornerShape(20.dp),
                                    blurRadius = 16.dp,
                                    borderWidth = 0.5.dp,
                                    containerColor = Color.White.copy(alpha = 0.05f)
                                )
                            )
                            
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(44.dp).background(Color.White.copy(alpha = 0.08f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = source.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    source.quality?.let { 
                                        Text(
                                            text = "Kalite: $it",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                                
                                IconButton(
                                    onClick = { onDownloadClick(source) },
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.1f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "İndir",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
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

@Composable
fun BatchDownloadOverlay(
    count: Int,
    onCancel: () -> Unit,
    onDownload: () -> Unit
) {
    if (count > 0) {
        GlassyBox(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            blurRadius = 24.dp,
            borderWidth = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$count",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Bölüm Seçildi", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("İndirmeye hazır", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "İptal", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("İndir", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SibnetConverterDialog(
    onDismiss: () -> Unit,
    onPlay: (String, String) -> Unit,
    onDownload: (String, String) -> Unit,
    viewModel: AnimecixDetailViewModel
) {
    var url by remember { mutableStateOf("") }
    var resolvedUrl by remember { mutableStateOf<String?>(null) }
    var isResolving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sibnet Link Dönüştürücü", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sibnet linkini yapıştırın.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = null; resolvedUrl = null },
                    placeholder = { Text("https://video.sibnet.ru/...", color = Color.White.copy(alpha = 0.3f)) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = 0.3f))
                )
                if (isResolving) CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally), color = Color.White)
                error?.let { Text(it, color = Color.White, style = MaterialTheme.typography.bodySmall) }
                resolvedUrl?.let { Text("Link başarıyla çözüldü!", color = Color(0xFF00C853), style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (resolvedUrl == null) {
                    Button(
                        onClick = {
                            scope.launch {
                                isResolving = true
                                error = null
                                try {
                                    val resolved = viewModel.resolveUrl(url)
                                    if (resolved != null && resolved.startsWith("http") && resolved != url) resolvedUrl = resolved
                                    else error = "Link çözülemedi."
                                } catch (e: Exception) { error = "Hata: ${e.message}" }
                                finally { isResolving = false }
                            }
                        },
                        enabled = url.isNotBlank() && !isResolving,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                    ) { Text("Çöz") }
                } else {
                    Button(onClick = { onPlay(resolvedUrl!!, "Sibnet Video") }, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))) { Text("Oynat") }
                    Button(onClick = { onDownload(resolvedUrl!!, "Sibnet Video") }, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))) { Text("İndir") }
                }
            }
        },
        containerColor = Color(0xFF1A1A1A)
    )
}
@Composable
fun LoadingPlaceholder(padding: PaddingValues) {
    val brush = com.myanim.kondi.ui.common.shimmerBrush()
    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().height(250.dp).background(brush))
        Column(modifier = Modifier.padding(16.dp)) {
            Box(modifier = Modifier.fillMaxWidth(0.6f).height(30.dp).background(brush, shape = RoundedCornerShape(4.dp)))
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(100.dp).background(brush, shape = RoundedCornerShape(4.dp)))
        }
    }
}
