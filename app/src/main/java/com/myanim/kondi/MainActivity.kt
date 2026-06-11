package com.myanim.kondi

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.myanim.kondi.ui.common.*
import androidx.compose.animation.ExperimentalSharedTransitionApi
import com.myanim.kondi.ui.animecix.*
import com.myanim.kondi.ui.storage.StorageManagerScreen
import com.myanim.kondi.ui.download.DownloadsScreen
import com.myanim.kondi.ui.theme.KondiTheme
import com.myanim.kondi.ui.theme.AnimeTheme
import com.myanim.kondi.ui.navigation.Screen
import com.myanim.kondi.util.ExternalPlayerHelper
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.nio.charset.StandardCharsets


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

    @OptIn(ExperimentalSharedTransitionApi::class)
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
                
                KondiTheme(animeTheme = activeTheme) {
                    // Observe navigationRoute state
                    val targetRoute by navigationRoute
                LaunchedEffect(targetRoute) {
                    targetRoute?.let { route ->
                        navController.navigate(route)
                        navigationRoute.value = null // Reset
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.animation.SharedTransitionLayout {
                        NavHost(
                            navController = navController,
                            startDestination = if (isOffline) Screen.AnimecixDownloads.route else Screen.AnimecixHome.route,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Splash removed, starting directly at AnimecixHome

                            // Animecix Graph
                            composable(Screen.AnimecixHome.route) {
                                AnimecixHomeScreen(
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedContentScope = this@composable,
                                    onAnimeClick = { id ->
                                        navController.navigate(Screen.AnimecixDetail.createRoute(id))
                                    },
                                    onVideoClick = { url, _, _, _, title ->
                                        ExternalPlayerHelper.launchPlayer(context, url, title, "ANIMECIX")
                                    },
                                    onBackToHome = {
                                        (context as? android.app.Activity)?.finish()
                                    },
                                    onDownloadsClick = {
                                        navController.navigate(Screen.AnimecixDownloads.route)
                                    },
                                    onSnifferClick = {
                                        navController.navigate(Screen.WebSniffer.route)
                                    },
                                    onStorageClick = {
                                        navController.navigate(Screen.StorageManager.route)
                                    },
                                    activeTheme = activeTheme,
                                    onThemeChange = { theme ->
                                        activeTheme = theme
                                        sharedPrefs.edit().putString("active_theme", theme.name).apply()
                                    }
                                )
                            }
                            
                            composable(Screen.StorageManager.route) {
                                StorageManagerScreen(
                                    onBackClick = { navController.popBackStack() }
                                )
                            }

                            composable(
                                Screen.AnimecixDetail.route,
                                arguments = listOf(navArgument("id") { type = NavType.IntType })
                            ) { backStackEntry ->
                                val id = backStackEntry.arguments?.getInt("id") ?: 0
                                AnimecixDetailScreen(
                                    animeId = id,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedContentScope = this@composable,
                                    onBackClick = { navController.popBackStack() },
                                    onVideoClick = { url, _, _, _, title ->
                                        ExternalPlayerHelper.launchPlayer(context, url, title, "ANIMECIX")
                                    }
                                )
                            }

                         composable(Screen.AnimecixDownloads.route) {
                            DownloadsScreen(
                                sourceFilter = "ANIMECIX",
                                onBackClick = { navController.popBackStack() },
                                onPlayClick = { download ->
                                    val uriString = if (download.filePath.startsWith("content://") || download.filePath.startsWith("file://")) {
                                        download.filePath
                                    } else {
                                        "file://" + download.filePath
                                    }
                                    ExternalPlayerHelper.launchPlayer(context, uriString, download.title, "LOCAL")
                                }
                            )
                        }

                        // Hentaizm Graph removed

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