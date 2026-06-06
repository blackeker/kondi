package com.myanim.kondi.ui.storage

import android.widget.Toast
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myanim.kondi.data.download.VideoDownloadManager
import com.myanim.kondi.data.local.DownloadStatus
import com.myanim.kondi.ui.common.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageManagerScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val downloadManager = remember { VideoDownloadManager.getInstance(context) }
    val downloadsFlow = remember { downloadManager.downloadsFlow }
    val downloads by downloadsFlow.collectAsState(initial = emptyList())
    
    val completedDownloads = downloads.filter { it.status == DownloadStatus.COMPLETED.name }
    val totalSize = completedDownloads.sumOf { it.totalBytes }
    
    // Dynamic sizes for caches
    var imageCacheSize by remember { mutableStateOf(downloadManager.getImageCacheSize()) }
    var strayChunksSize by remember { mutableStateOf(downloadManager.getStrayChunksSize()) }
    var logsSize by remember { mutableStateOf(downloadManager.getLogsSize()) }
    
    val scope = rememberCoroutineScope()
    
    Box(modifier = Modifier.fillMaxSize()) {
        KondiBackground()
        
        Column(modifier = Modifier.fillMaxSize()) {
            GlassyTopAppBar(
                title = { Text("Depolama Yönetimi", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                }
            )
            
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Total Storage Info Card
                item {
                    GlassyCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = Color.Cyan,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    val sizeInMb = (totalSize + imageCacheSize + strayChunksSize + logsSize) / (1024 * 1024)
                                    Text("Toplam Kullanılan Alan", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                                    Text("$sizeInMb MB", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
                
                // Detailed Breakdown Section
                item {
                    Text(
                        text = "Depolama Kırılımı",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                
                item {
                    // Breakdown Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.03f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Downloads
                            StorageBreakdownItem(
                                title = "İndirilen Videolar",
                                sizeText = "${totalSize / (1024 * 1024)} MB",
                                icon = Icons.Default.Download,
                                onCleanClick = if (completedDownloads.isNotEmpty()) {
                                    {
                                        completedDownloads.forEach { downloadManager.deleteDownload(it.id) }
                                        Toast.makeText(context, "Tüm indirilen videolar temizlendi.", Toast.LENGTH_SHORT).show()
                                    }
                                } else null
                            )
                            
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            
                            // Stray chunks
                            StorageBreakdownItem(
                                title = "Geçici Parçalar (Chunks)",
                                sizeText = "${strayChunksSize / (1024 * 1024)} MB",
                                icon = Icons.Default.DeleteSweep,
                                onCleanClick = if (strayChunksSize > 0) {
                                    {
                                        val deleted = downloadManager.clearStrayChunks()
                                        strayChunksSize = downloadManager.getStrayChunksSize()
                                        Toast.makeText(context, "$deleted geçici dosya temizlendi.", Toast.LENGTH_SHORT).show()
                                    }
                                } else null
                            )
                            
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            
                            // Image cache
                            StorageBreakdownItem(
                                title = "Görsel Önbelleği (Coil)",
                                sizeText = "${imageCacheSize / (1024 * 1024)} MB",
                                icon = Icons.Default.Cached,
                                onCleanClick = if (imageCacheSize > 0) {
                                    {
                                        downloadManager.clearImageCache()
                                        imageCacheSize = downloadManager.getImageCacheSize()
                                        Toast.makeText(context, "Görsel önbelleği temizlendi.", Toast.LENGTH_SHORT).show()
                                    }
                                } else null
                            )
                            
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            
                            // Logs
                            val kbSize = logsSize / 1024
                            val logsText = if (kbSize > 1024) "${kbSize / 1024} MB" else "$kbSize KB"
                            StorageBreakdownItem(
                                title = "Uygulama Logları & Kayıtları",
                                sizeText = logsText,
                                icon = Icons.Default.Description,
                                onCleanClick = if (logsSize > 0) {
                                    {
                                        downloadManager.clearLogs()
                                        logsSize = downloadManager.getLogsSize()
                                        Toast.makeText(context, "Log dosyaları temizlendi.", Toast.LENGTH_SHORT).show()
                                    }
                                } else null
                            )
                        }
                    }
                }
                
                // Completed Downloads Title
                item {
                    Text(
                        text = "İndirilen Videolar (${completedDownloads.size})",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                
                // List of downloads
                if (completedDownloads.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("İndirilmiş anime bulunmamaktadır.", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                        }
                    }
                } else {
                    items(completedDownloads, key = { it.id }) { download ->
                        ListItem(
                            headlineContent = { Text(download.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("${download.totalBytes / (1024 * 1024)} MB", color = Color.White.copy(alpha = 0.5f)) },
                            trailingContent = {
                                IconButton(onClick = { downloadManager.deleteDownload(download.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red.copy(alpha = 0.7f))
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .glassmorphismLayout(shape = RoundedCornerShape(14.dp), blurRadius = 15.dp, borderWidth = 0.5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StorageBreakdownItem(
    title: String,
    sizeText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onCleanClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(sizeText, color = Color.Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        
        Button(
            onClick = onCleanClick ?: {},
            enabled = onCleanClick != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.1f),
                disabledContainerColor = Color.White.copy(alpha = 0.02f)
            ),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Temizle",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (onCleanClick != null) Color.White else Color.White.copy(alpha = 0.3f)
            )
        }
    }
}
