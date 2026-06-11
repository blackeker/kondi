package com.myanim.kondi.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Backup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import kotlinx.coroutines.launch
import com.myanim.kondi.data.download.VideoDownloadManager
import com.myanim.kondi.ui.theme.AnimeTheme
import com.myanim.kondi.ui.theme.getPalette

@Composable
fun SettingsDialog(
    currentTheme: AnimeTheme,
    onThemeSelect: (AnimeTheme) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val downloadManager = remember { VideoDownloadManager.getInstance(context) }
    val prefManager = remember { com.myanim.kondi.data.prefs.UserPreferencesManager.getInstance(context) }
    
    val backupManager = remember { com.myanim.kondi.data.backup.GoogleDriveBackupManager(context) }
    var signedInAccount by remember { mutableStateOf(backupManager.getSignedInAccount()) }
    val coroutineScope = rememberCoroutineScope()
    
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                signedInAccount = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                Toast.makeText(context, "Giriş başarılı: ${signedInAccount?.email}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Giriş hatası: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    var concurrentDownloads by remember { mutableStateOf(downloadManager.maxConcurrentDownloads.toFloat()) }
    var enableMultiChunk by remember { mutableStateOf(prefManager.enableMultiChunk) }
    var downloadThreads by remember { mutableStateOf(prefManager.downloadThreads.toFloat()) }

    Dialog(onDismissRequest = onDismiss) {
        GlassyCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 16.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color.Cyan,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Kondi Ayarlar",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                // Section: İndirme Ayarları
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "İndirme Ayarları",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                
                // Concurrent Downloads Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Eş Zamanlı İndirme",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                            Text(
                                text = "${concurrentDownloads.toInt()} Video",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Cyan
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = concurrentDownloads,
                            onValueChange = { 
                                concurrentDownloads = it
                                downloadManager.maxConcurrentDownloads = it.toInt()
                            },
                            valueRange = 1f..10f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.Cyan,
                                activeTrackColor = Color.Cyan.copy(alpha = 0.4f),
                                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // MultiChunk Toggle Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Çoklu Bağlantı (Parçalı İndirme)",
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                                Text(
                                    text = "Dosyaları parçalara bölerek hızlı indirir.",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                            Switch(
                                checked = enableMultiChunk,
                                onCheckedChange = {
                                    enableMultiChunk = it
                                    prefManager.enableMultiChunk = it
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Cyan,
                                    checkedTrackColor = Color.Cyan.copy(alpha = 0.4f)
                                )
                            )
                        }


                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Divider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(20.dp))

                // Section: Temalar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Palette, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Görünüm Teması",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 240.dp),
                    userScrollEnabled = false
                ) {
                    items(AnimeTheme.entries) { theme ->
                        ThemeItem(
                            theme = theme,
                            isSelected = currentTheme == theme,
                            onClick = { onThemeSelect(theme) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(20.dp))

                // Section: Yedekleme (Backup)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Backup, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Bulut Yedekleme (Google Drive)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        if (signedInAccount == null) {
                            Text(
                                text = "Favorileriniz, geçmişiniz ve ayarlarınızı Google Drive'a yedekleyin.",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    signInLauncher.launch(backupManager.googleSignInClient.signInIntent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Google ile Giriş Yap", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text(
                                text = "Bağlı Hesap: ${signedInAccount?.email}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val success = backupManager.performBackup(signedInAccount!!)
                                            if (success) {
                                                Toast.makeText(context, "Yedekleme başarıyla tamamlandı!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Yedekleme başarısız oldu.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Yedekle", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val success = backupManager.performRestore(signedInAccount!!)
                                            if (success) {
                                                Toast.makeText(context, "Veriler başarıyla geri yüklendi!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Geri yükleme başarısız oldu veya yedek bulunamadı.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Geri Yükle", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    backupManager.googleSignInClient.signOut().addOnCompleteListener {
                                        signedInAccount = null
                                        Toast.makeText(context, "Oturum kapatıldı.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("Oturumu Kapat", color = Color.Red.copy(alpha = 0.8f), fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                ) {
                    Text("Tamam", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ThemeItem(
    theme: AnimeTheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val palette = theme.getPalette()
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = if (isSelected) 0.15f else 0.05f))
            .border(
                1.dp, 
                if (isSelected) palette.primary else Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(palette.primary))
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(20.dp).clip(CircleShape).background(palette.secondary))
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = theme.displayName,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) palette.primary else Color.White
            )

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = palette.primary,
                    modifier = Modifier.size(16.dp).padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    currentTheme: AnimeTheme,
    onThemeSelect: (AnimeTheme) -> Unit
) {
    val context = LocalContext.current
    val downloadManager = remember { VideoDownloadManager.getInstance(context) }
    val prefManager = remember { com.myanim.kondi.data.prefs.UserPreferencesManager.getInstance(context) }
    
    val backupManager = remember { com.myanim.kondi.data.backup.GoogleDriveBackupManager(context) }
    var signedInAccount by remember { mutableStateOf(backupManager.getSignedInAccount()) }
    val coroutineScope = rememberCoroutineScope()
    
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                signedInAccount = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                Toast.makeText(context, "Giriş başarılı: ${signedInAccount?.email}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Giriş hatası: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    var concurrentDownloads by remember { mutableStateOf(downloadManager.maxConcurrentDownloads.toFloat()) }
    var enableMultiChunk by remember { mutableStateOf(prefManager.enableMultiChunk) }
    var downloadThreads by remember { mutableStateOf(prefManager.downloadThreads.toFloat()) }
    
    // Cache sizes
    var imageCacheSize by remember { mutableStateOf(downloadManager.getImageCacheSize()) }
    var strayChunksSize by remember { mutableStateOf(downloadManager.getStrayChunksSize()) }
    var logsSize by remember { mutableStateOf(downloadManager.getLogsSize()) }

    Box(modifier = Modifier.fillMaxSize()) {
        KondiBackground()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 80.dp) // Leave room for bottom bar
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color.Cyan,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Kondi Ayarlar",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                // Section: İndirme Ayarları
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "İndirme Ayarları",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                
                // Concurrent Downloads Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Eş Zamanlı İndirme",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                            Text(
                                text = "${concurrentDownloads.toInt()} Video",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Cyan
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = concurrentDownloads,
                            onValueChange = { 
                                concurrentDownloads = it
                                downloadManager.maxConcurrentDownloads = it.toInt()
                            },
                            valueRange = 1f..10f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.Cyan,
                                activeTrackColor = Color.Cyan.copy(alpha = 0.4f),
                                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // MultiChunk Toggle Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Çoklu Bağlantı (Parçalı İndirme)",
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                                Text(
                                    text = "Dosyaları parçalara bölerek hızlı indirir.",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                            Switch(
                                checked = enableMultiChunk,
                                onCheckedChange = {
                                    enableMultiChunk = it
                                    prefManager.enableMultiChunk = it
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Cyan,
                                    checkedTrackColor = Color.Cyan.copy(alpha = 0.4f)
                                )
                            )
                        }


                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(20.dp))

                // Section: Depolama ve Önbellek
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteSweep, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Depolama & Önbellek",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                
                // Cache info card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("Görsel Önbelleği", fontSize = 14.sp, color = Color.White)
                                Text("${imageCacheSize / (1024 * 1024)} MB", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                            }
                            Button(
                                onClick = {
                                    downloadManager.clearImageCache()
                                    imageCacheSize = downloadManager.getImageCacheSize()
                                    Toast.makeText(context, "Görsel önbelleği temizlendi.", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Temizle", fontSize = 12.sp, color = Color.White)
                            }
                        }
                        
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("Geçici Parçalar (Chunks)", fontSize = 14.sp, color = Color.White)
                                Text("${strayChunksSize / (1024 * 1024)} MB", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                            }
                            Button(
                                onClick = {
                                    val deleted = downloadManager.clearStrayChunks()
                                    strayChunksSize = downloadManager.getStrayChunksSize()
                                    Toast.makeText(context, "$deleted geçici dosya temizlendi.", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Temizle", fontSize = 12.sp, color = Color.White)
                            }
                        }
                        
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("Uygulama Logları & Hata Kayıtları", fontSize = 14.sp, color = Color.White)
                                val kbSize = logsSize / 1024
                                Text(if (kbSize > 1024) "${kbSize / 1024} MB" else "$kbSize KB", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                            }
                            Button(
                                onClick = {
                                    downloadManager.clearLogs()
                                    logsSize = downloadManager.getLogsSize()
                                    Toast.makeText(context, "Log dosyaları temizlendi.", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Temizle", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(20.dp))

                // Section: Temalar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Palette, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Görünüm Teması",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 240.dp),
                    userScrollEnabled = false
                ) {
                    items(AnimeTheme.entries) { theme ->
                        ThemeItem(
                            theme = theme,
                            isSelected = currentTheme == theme,
                            onClick = { onThemeSelect(theme) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(20.dp))

                // Section: Yedekleme (Backup)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Backup, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Bulut Yedekleme (Google Drive)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        if (signedInAccount == null) {
                            Text(
                                text = "Favorileriniz, geçmişiniz ve ayarlarınızı Google Drive'a yedekleyin.",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    signInLauncher.launch(backupManager.googleSignInClient.signInIntent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Google ile Giriş Yap", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text(
                                text = "Bağlı Hesap: ${signedInAccount?.email}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val success = backupManager.performBackup(signedInAccount!!)
                                            if (success) {
                                                Toast.makeText(context, "Yedekleme başarıyla tamamlandı!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Yedekleme başarısız oldu.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Yedekle", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val success = backupManager.performRestore(signedInAccount!!)
                                            if (success) {
                                                Toast.makeText(context, "Veriler başarıyla geri yüklendi!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Geri yükleme başarısız oldu veya yedek bulunamadı.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Geri Yükle", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    backupManager.googleSignInClient.signOut().addOnCompleteListener {
                                        signedInAccount = null
                                        Toast.makeText(context, "Oturum kapatıldı.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("Oturumu Kapat", color = Color.Red.copy(alpha = 0.8f), fontSize = 12.sp)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
