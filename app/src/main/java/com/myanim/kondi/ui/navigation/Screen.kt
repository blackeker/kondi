package com.myanim.kondi.ui.navigation

sealed class Screen(val route: String) {
    object AnimecixHome : Screen("animecix_home")
    object AnimecixDetail : Screen("animecix_detail/{id}") {
        fun createRoute(id: Int) = "animecix_detail/$id"
    }
    object AnimecixDownloads : Screen("animecix_downloads")
    object StorageManager : Screen("storage_manager")
    object WebSniffer : Screen("web_sniffer")
}
