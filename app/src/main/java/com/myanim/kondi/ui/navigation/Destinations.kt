package com.myanim.kondi.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object HomeDestination

@Serializable
object FavoritesDestination

@Serializable
object DownloadsDestination

@Serializable
object SettingsDestination

@Serializable
data class DetailDestination(val id: Int)

@Serializable
object StorageDestination

@Serializable
object MainDestination

