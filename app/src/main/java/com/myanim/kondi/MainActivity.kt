package com.myanim.kondi

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.myanim.kondi.ui.animecix.DetailScreen
import com.myanim.kondi.ui.animecix.HomeScreen
import com.myanim.kondi.ui.download.StorageTab
import com.myanim.kondi.ui.navigation.*
import com.myanim.kondi.ui.theme.KondiTheme
import com.myanim.kondi.util.ExternalPlayerHelper
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    private val navigationRoute = mutableStateOf<String?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Permissions result handled silently */ }

    @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            val navController = rememberNavController()
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()

            KondiTheme(darkTheme = false, dynamicColor = true) {
                val targetRoute by navigationRoute
                LaunchedEffect(targetRoute) {
                    targetRoute?.let { route ->
                        if (route.startsWith("anime_detail/")) {
                            val id = route.substringAfter("anime_detail/").toIntOrNull()
                            if (id != null) {
                                navController.navigate(DetailDestination(id))
                            }
                        }
                        navigationRoute.value = null
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = MainDestination,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable<MainDestination> {
                        HomeScreen(
                            viewModel = viewModel(),
                            onAnimeClick = { id ->
                                navController.navigate(DetailDestination(id))
                            },
                            onVideoClick = { url, title ->
                                coroutineScope.launch {
                                    ExternalPlayerHelper.launchPlayer(context, url, title, "ANIMECIX")
                                }
                            },
                            onStorageClick = { navController.navigate(StorageDestination) },
                            onSettingsClick = {}
                        )
                    }

                    composable<DetailDestination> { backStackEntry ->
                        val detail = backStackEntry.toRoute<DetailDestination>()
                        DetailScreen(
                            animeId = detail.id,
                            onBackClick = { navController.popBackStack() },
                            onVideoClick = { url, title ->
                                coroutineScope.launch {
                                    ExternalPlayerHelper.launchPlayer(context, url, title, "ANIMECIX")
                                }
                            }
                        )
                    }

                    composable<StorageDestination> {
                        StorageTab(
                            modifier = Modifier.fillMaxSize(),
                            onClose = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent == null) return

        val navigateTo = intent.getStringExtra("navigate_to")
        if (navigateTo != null) {
            navigationRoute.value = navigateTo
            intent.removeExtra("navigate_to")
        }
    }
}
