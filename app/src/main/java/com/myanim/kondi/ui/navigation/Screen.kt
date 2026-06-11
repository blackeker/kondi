package com.myanim.kondi.ui.navigation

sealed class Screen(val route: String) {
    object AnimecixHome : Screen("animecix_home")
    object AnimecixDetail : Screen("animecix_detail/{id}") {
        fun createRoute(id: Int) = "animecix_detail/$id"
    }
    object AnimecixDownloads : Screen("animecix_downloads")
    object StorageManager : Screen("storage_manager")
    object WebSniffer : Screen("web_sniffer")
    object HdFilmHome : Screen("hdfilm_home")
    object HdFilmDetail : Screen("hdfilm_detail/{url}") {
        fun createRoute(url: String): String {
            val encodedUrl = android.util.Base64.encodeToString(url.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
            return "hdfilm_detail/$encodedUrl"
        }
    }
}
