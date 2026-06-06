package com.myanim.kondi.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.platform.LocalContext
import com.myanim.kondi.data.local.KondiDatabase
import kotlinx.coroutines.flow.collectLatest
import com.myanim.kondi.data.local.WatchedAnime
import com.myanim.kondi.data.local.DownloadedAnime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBackClick: () -> Unit = {},
    isEmbedded: Boolean = false
) {
    val context = LocalContext.current
    val database = remember { KondiDatabase.getDatabase(context) }
    
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("İzlenilen", "İndirilen")
    
    val watchedList = remember { mutableStateListOf<WatchedAnime>() }
    val downloadedList = remember { mutableStateListOf<DownloadedAnime>() }

    LaunchedEffect(Unit) {
        database.watchedAnimeDao().getAllWatchedAnime().collectLatest {
            watchedList.clear()
            watchedList.addAll(it)
        }
    }
    
    LaunchedEffect(Unit) {
        database.downloadedAnimeDao().getAllDownloadedAnime().collectLatest {
            downloadedList.clear()
            downloadedList.addAll(it)
        }
    }

    val content = @Composable { padding: PaddingValues ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }
            
            if (selectedTabIndex == 0) {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    items(watchedList) { anime ->
                        Card(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(text = anime.title, style = MaterialTheme.typography.titleMedium)
                                Text(text = "Son İzlenen Bölüm: ${anime.lastWatchedEpisode}")
                            }
                        }
                    }
                }
            } else {
                com.myanim.kondi.ui.download.DownloadsScreen(
                    sourceFilter = null,
                    isEmbedded = true,
                    onPlayClick = { download ->
                        com.myanim.kondi.util.ExternalPlayerHelper.launchPlayer(context, download.filePath, download.title, "LOCAL")
                    }
                )
            }
        }
    }

    if (isEmbedded) {
        content(PaddingValues(0.dp))
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Kütüphane") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                        }
                    }
                )
            }
        ) { padding ->
            content(padding)
        }
    }
}
