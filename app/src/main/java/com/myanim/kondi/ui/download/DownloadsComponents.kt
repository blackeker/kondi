package com.myanim.kondi.ui.download

// Removed animation imports
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myanim.kondi.data.local.Download
import com.myanim.kondi.data.local.DownloadStatus
import com.myanim.kondi.ui.common.GlassyBox
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast

data class DownloadStats(
    val total: Int,
    val completed: Int,
    val downloading: Int,
    val failed: Int,
    val totalSize: Long
)

data class ParsedTitle(
    val animeName: String,
    val subtitle: String
)

fun parseDownloadTitle(title: String): ParsedTitle {
    val regex = Regex("(?i)(.*)_s(\\d+)_e(\\d+).*")
    val matchResult = regex.matchEntire(title)
    if (matchResult != null) {
        val rawAnimeName = matchResult.groupValues[1].replace("_", " ").trim()
        val cleanAnimeName = rawAnimeName.split(" ").filter { it.isNotEmpty() }.joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        val seasonVal = matchResult.groupValues[2].toIntOrNull() ?: 1
        val episodeVal = matchResult.groupValues[3].toIntOrNull() ?: 1
        return ParsedTitle(
            animeName = cleanAnimeName,
            subtitle = "Sezon $seasonVal - Bölüm $episodeVal"
        )
    }
    
    val cleanTitle = title.substringBeforeLast(".")
        .replace("_mp4", "")
        .replace("_", " ")
        .trim()
    val capitalized = cleanTitle.split(" ").filter { it.isNotEmpty() }.joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
    return ParsedTitle(
        animeName = capitalized,
        subtitle = ""
    )
}

@Composable
fun StatsCard(stats: DownloadStats) {
    GlassyBox(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White.copy(alpha = 0.1f),
        blurRadius = 12.dp,
        borderWidth = 1.dp,
        borderColor = Color.White.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(icon = Icons.Default.CheckCircle, value = stats.completed.toString(), label = "Tamamlandı", color = MaterialTheme.colorScheme.primary)
            VerticalDivider(modifier = Modifier.height(48.dp))
            StatItem(icon = Icons.Default.Download, value = stats.downloading.toString(), label = "İndiriliyor", color = MaterialTheme.colorScheme.tertiary)
            VerticalDivider(modifier = Modifier.height(48.dp))
            StatItem(icon = Icons.Default.Storage, value = formatBytes(stats.totalSize), label = "Toplam", color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
fun StatItem(icon: ImageVector, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
fun FilterSortBottomSheet(
    selectedFilter: DownloadFilter,
    selectedSort: DownloadSort,
    onFilterChange: (DownloadFilter) -> Unit,
    onSortChange: (DownloadSort) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
        Text("Filtrele ve Sırala", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
        HorizontalDivider()
        Text("FİLTRE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        DownloadFilter.entries.forEach { filter ->
            ListItem(
                headlineContent = { Text(when (filter) { DownloadFilter.ALL -> "Tümü"; DownloadFilter.COMPLETED -> "Tamamlananlar"; DownloadFilter.DOWNLOADING -> "İndirilenler"; DownloadFilter.FAILED -> "Başarısız" }) },
                leadingContent = { RadioButton(selected = selectedFilter == filter, onClick = { onFilterChange(filter) }) },
                modifier = Modifier.clickable { onFilterChange(filter) }
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("SIRALAMA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        DownloadSort.entries.forEach { sort ->
            ListItem(
                headlineContent = { Text(when (sort) { DownloadSort.DATE_DESC -> "Tarihe Göre (Yeni → Eski)"; DownloadSort.DATE_ASC -> "Tarihe Göre (Eski → Yeni)"; DownloadSort.NAME_ASC -> "İsme Göre (A → Z)"; DownloadSort.NAME_DESC -> "İsme Göre (Z → A)"; DownloadSort.SIZE_DESC -> "Boyuta Göre (Büyük → Küçük)" }) },
                leadingContent = { RadioButton(selected = selectedSort == sort, onClick = { onSortChange(sort) }) },
                modifier = Modifier.clickable { onSortChange(sort) }
            )
        }
    }
}

@Composable
fun EmptyDownloadsState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.DownloadDone, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            Text("Henüz indirme yok", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("İçerik indirdiğinizde burada görünecek", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EmptyFilterState(filter: DownloadFilter) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.FilterList, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(when (filter) { DownloadFilter.COMPLETED -> "Tamamlanmış indirme yok"; DownloadFilter.DOWNLOADING -> "Devam eden indirme yok"; DownloadFilter.FAILED -> "Başarısız indirme yok"; else -> "Sonuç bulunamadı" }, style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    }
}

@Composable
fun ImprovedDownloadItem(
    download: Download,
    queuePosition: Int = 0,
    onDelete: (Download) -> Unit,
    onOpen: (Download) -> Unit,
    onCancel: (Download) -> Unit = {},
    onPause: (Download) -> Unit = {},
    onResume: (Download) -> Unit = {},
    onRetry: (Download) -> Unit = {},
    onMoveUp: (Download) -> Unit = {},
    onMoveDown: (Download) -> Unit = {},
    onShowError: ((Download) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    GlassyBox(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(24.dp),
        blurRadius = 12.dp,
        borderWidth = 1.dp,
        borderColor = Color.White.copy(alpha = 0.25f)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    val parsed = remember(download.title) { parseDownloadTitle(download.title) }
                    Text(text = parsed.animeName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                    if (parsed.subtitle.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = parsed.subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.Cyan, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    StatusChip(status = download.status)
                }
                Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(when (download.status) { DownloadStatus.COMPLETED.name -> MaterialTheme.colorScheme.primaryContainer; DownloadStatus.DOWNLOADING.name -> MaterialTheme.colorScheme.tertiaryContainer; DownloadStatus.FAILED.name -> MaterialTheme.colorScheme.errorContainer; else -> MaterialTheme.colorScheme.surfaceVariant }), contentAlignment = Alignment.Center) {
                    when (download.status) {
                        DownloadStatus.COMPLETED.name -> Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                        DownloadStatus.DOWNLOADING.name -> CircularProgressIndicator(progress = { download.progress / 100f }, modifier = Modifier.size(24.dp), strokeWidth = 3.dp, color = Color.White)
                        DownloadStatus.FAILED.name -> Icon(Icons.Default.Error, null, tint = Color.White)
                        DownloadStatus.PAUSED.name -> Icon(Icons.Default.Pause, null, tint = Color.White.copy(alpha = 0.6f))
                        DownloadStatus.PENDING.name -> Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp, color = Color.White.copy(alpha = 0.5f))
                            if (queuePosition > 0) Text(text = queuePosition.toString(), style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            when (download.status) {
                DownloadStatus.DOWNLOADING.name, DownloadStatus.PAUSED.name -> {
                    LinearProgressIndicator(progress = { download.progress / 100f }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)), color = Color.Cyan, trackColor = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "${download.progress}%", style = MaterialTheme.typography.labelMedium, color = Color.Cyan, fontWeight = FontWeight.Bold)
                        Text(text = "${formatBytes(download.downloadedBytes)} / ${formatBytes(download.totalBytes)}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
                    }
                }
                DownloadStatus.COMPLETED.name -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoChip(icon = Icons.Default.Storage, text = formatBytes(download.totalBytes))
                    InfoChip(icon = Icons.Default.VideoLibrary, text = "Video")
                }
                DownloadStatus.FAILED.name -> download.errorMessage?.let {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)) {
                        Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(8.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                when (download.status) {
                    DownloadStatus.COMPLETED.name -> {
                        FilledTonalButton(onClick = { onOpen(download) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp)); Text("Oynat") }
                        OutlinedButton(onClick = { onDelete(download) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp)); Text("Sil") }
                    }
                    DownloadStatus.DOWNLOADING.name -> {
                        FilledTonalButton(onClick = { onPause(download) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Pause, null, modifier = Modifier.size(18.dp)); Text("Duraklat") }
                        OutlinedButton(onClick = { onCancel(download) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)); Text("İptal") }
                    }
                    DownloadStatus.PENDING.name -> {
                        FilledTonalButton(onClick = { onMoveUp(download) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(18.dp)); Text("Öne Al") }
                        OutlinedButton(onClick = { onCancel(download) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)); Text("İptal") }
                    }
                    DownloadStatus.PAUSED.name -> {
                        FilledTonalButton(onClick = { onResume(download) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp)); Text("Devam") }
                        OutlinedButton(onClick = { onCancel(download) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)); Text("İptal") }
                    }
                    DownloadStatus.FAILED.name -> {
                        FilledTonalButton(onClick = { onRetry(download) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp)); Text("Tekrar") }
                        if (download.errorMessage != null && onShowError != null) {
                            OutlinedButton(onClick = { onShowError(download) }, modifier = Modifier.weight(1.2f)) { Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Detay") }
                        }
                        OutlinedButton(onClick = { onDelete(download) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp)); Text("Sil") }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val (label, color) = when (status) {
        DownloadStatus.COMPLETED.name -> "Tamamlandı" to Color(0xFF4CAF50)
        DownloadStatus.DOWNLOADING.name -> "İndiriliyor" to Color(0xFF2196F3)
        DownloadStatus.PAUSED.name -> "Duraklatıldı" to Color(0xFFFF9800)
        DownloadStatus.PENDING.name -> "Bekliyor" to Color(0xFF9E9E9E)
        DownloadStatus.FAILED.name -> "Hata" to Color(0xFFF44336)
        else -> status to Color.Gray
    }
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.2f), border = BorderStroke(0.5.dp, color.copy(alpha = 0.5f))) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun InfoChip(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.5f))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroup = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroup.toDouble()), units[digitGroup])
}

@Composable
fun DownloadErrorDialog(
    download: Download,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                Text("İndirme Hatası", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(text = "Dosya: ${download.title}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = download.errorMessage ?: "Bilinmeyen hata",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Kapat")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                clipboardManager.setText(AnnotatedString(download.errorMessage ?: "Bilinmeyen hata"))
                Toast.makeText(context, "Hata panoya kopyalandı", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Kopyala")
            }
        }
    )
}

@Composable
fun EpisodeListItem(
    download: Download,
    onPlayClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val parsed = remember(download.title) { parseDownloadTitle(download.title) }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = parsed.subtitle.ifEmpty { parsed.animeName },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = formatBytes(download.totalBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                    Text(
                        text = "Video",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Oynat",
                        tint = Color.Cyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Sil",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AnimeGroupCard(
    animeName: String,
    posterUrl: String?,
    episodes: List<Download>,
    onPlayClick: (Download) -> Unit,
    onDeleteClick: (Download) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    GlassyBox(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(24.dp),
        blurRadius = 12.dp,
        borderWidth = 1.dp,
        borderColor = Color.White.copy(alpha = 0.25f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover art image
                Box(
                    modifier = Modifier
                        .size(height = 90.dp, width = 64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                ) {
                    if (posterUrl != null) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Anime Details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = animeName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${episodes.size} Bölüm",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                
                // Expand Icon
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(28.dp)
                )
            }
            
            // Expandable Episode List
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Column {
                    episodes.forEachIndexed { index, download ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.08f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                        EpisodeListItem(
                            download = download,
                            onPlayClick = { onPlayClick(download) },
                            onDeleteClick = { onDeleteClick(download) }
                        )
                    }
                }
            }
        }
    }
}
