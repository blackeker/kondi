package com.myanim.kondi

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.myanim.kondi.ui.animecix.FavoritesScreen
import com.myanim.kondi.ui.animecix.HomeScreen
import com.myanim.kondi.ui.animecix.AnimecixDetailScreen
import com.myanim.kondi.ui.catalog.ProviderCatalogScreen
import com.myanim.kondi.ui.common.FloatingGlassDock
import com.myanim.kondi.ui.common.SettingsDialog
import com.myanim.kondi.ui.hub.ProviderHubScreen
import com.myanim.kondi.ui.storage.StorageManagerScreen
import com.myanim.kondi.ui.download.DownloadsScreen
import com.myanim.kondi.ui.theme.KondiTheme
import com.myanim.kondi.ui.theme.AnimeTheme
import com.myanim.kondi.ui.navigation.*
import com.myanim.kondi.util.ExternalPlayerHelper
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.launch
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.CoroutineScope

class MainActivity : ComponentActivity() {
    // Navigation trigger state
    private val navigationRoute = mutableStateOf<String?>(null)

    // Storage + Notification permission request
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Permissions result handled silently */ }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    @OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request storage and notification permissions at start
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(android.Manifest.permission.READ_MEDIA_VIDEO)
                add(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
        permissionLauncher.launch(permissions)
        
        val sharedPrefs = getSharedPreferences("kondi_prefs", Context.MODE_PRIVATE)
        val savedTheme = sharedPrefs.getString("active_theme", AnimeTheme.DEFAULT.name)
        val initialTheme = AnimeTheme.entries.find { it.name == savedTheme } ?: AnimeTheme.DEFAULT

        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val isOffline = capabilities == null || 
            !(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || 
              capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || 
              capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))

        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            var activeTheme by remember { mutableStateOf(initialTheme) }
            val navController = rememberNavController()
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            val initialPage = remember { mutableIntStateOf(if (isOffline) 3 else 0) }
            
            KondiTheme(animeTheme = activeTheme) {
                // Observe navigationRoute state (For notification clicks)
                val targetRoute by navigationRoute
                LaunchedEffect(targetRoute) {
                    targetRoute?.let { route ->
                        if (route == "animecix_downloads") {
                            initialPage.intValue = 3
                        }
                        navigationRoute.value = null // Reset
                    }
                }

                androidx.compose.animation.SharedTransitionLayout {
                    NavHost(
                        navController = navController,
                        startDestination = MainDestination,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable<MainDestination> {
                            MainScreen(
                                activeTheme = activeTheme,
                                onThemeChange = { theme ->
                                    activeTheme = theme
                                    sharedPrefs.edit().putString("active_theme", theme.name).apply()
                                },
                                onAnimeClick = { id ->
                                    navController.navigate(DetailDestination(id))
                                },
                                onVideoClick = { url, title ->
                                    coroutineScope.launch {
                                        ExternalPlayerHelper.launchPlayer(context, url, title, "ANIMECIX")
                                    }
                                },
                                onStorageClick = {
                                    navController.navigate(StorageDestination)
                                },
                                isOffline = isOffline,
                                context = context,
                                coroutineScope = coroutineScope,
                                initialPage = initialPage.intValue,
                                onPageSelected = { initialPage.intValue = it }
                            )
                        }

                        composable<StorageDestination> {
                            StorageManagerScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable<DetailDestination> { backStackEntry ->
                            val detail = backStackEntry.toRoute<DetailDestination>()
                            AnimecixDetailScreen(
                                animeId = detail.id,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedContentScope = this@composable,
                                onBackClick = { navController.popBackStack() },
                                onVideoClick = { url, _, _, _, title ->
                                    coroutineScope.launch {
                                        ExternalPlayerHelper.launchPlayer(context, url, title, "ANIMECIX")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent == null) return
        
        // Check manual explicit navigation
        val navigateTo = intent.getStringExtra("navigate_to")
        if (navigateTo != null) {
            navigationRoute.value = navigateTo
            intent.removeExtra("navigate_to")
        }
    }
}

@Composable
fun MainScreen(
    activeTheme: AnimeTheme,
    onThemeChange: (AnimeTheme) -> Unit,
    onAnimeClick: (Int) -> Unit,
    onVideoClick: (String, String) -> Unit,
    onStorageClick: () -> Unit,
    isOffline: Boolean,
    context: Context,
    coroutineScope: CoroutineScope,
    initialPage: Int,
    onPageSelected: (Int) -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { 4 }
    )
    var showSettingsDialog by remember { mutableStateOf(false) }

    if (showSettingsDialog) {
        SettingsDialog(
            currentTheme = activeTheme,
            onThemeSelect = onThemeChange,
            onDismiss = { showSettingsDialog = false }
        )
    }

    // Sync external page changes (e.g. from notification clicks)
    LaunchedEffect(initialPage) {
        if (pagerState.currentPage != initialPage) {
            pagerState.scrollToPage(initialPage)
        }
    }

    // Sync internal page swipes back to the parent state only when scroll settles
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            onPageSelected(pagerState.currentPage)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        bottomBar = {
            FloatingGlassDock(
                selectedTab = pagerState.currentPage,
                onTabSelect = { targetTab ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(targetTab)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true
            ) { page ->
                when (page) {
                    0 -> ProviderHubScreen(
                        onAnimeClick = onAnimeClick,
                        onVideoClick = onVideoClick,
                        onSearchClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        },
                        onStorageClick = onStorageClick,
                        onExploreAllClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        },
                        onSettingsClick = {
                            showSettingsDialog = true
                        }
                    )
                    1 -> HomeScreen(
                        viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
                        onAnimeClick = onAnimeClick,
                        onVideoClick = onVideoClick,
                        onStorageClick = onStorageClick,
                        onSettingsClick = {
                            showSettingsDialog = true
                        }
                    )
                    2 -> FavoritesScreen(
                        viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
                        onAnimeClick = onAnimeClick
                    )
                    3 -> DownloadsScreen(
                        sourceFilter = "ANIMECIX",
                        isEmbedded = true,
                        onBackClick = {},
                        onPlayClick = { download ->
                            val uriString = if (download.filePath.startsWith("content://") || download.filePath.startsWith("file://")) {
                                download.filePath
                            } else {
                                "file://" + download.filePath
                            }
                            coroutineScope.launch {
                                ExternalPlayerHelper.launchPlayer(context, uriString, download.title, "LOCAL")
                            }
                        }
                    )
                }
            }
        }
    }
}