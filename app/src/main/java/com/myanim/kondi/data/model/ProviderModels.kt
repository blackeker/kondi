package com.myanim.kondi.data.model

import androidx.compose.ui.graphics.Color
import com.myanim.kondi.ui.theme.*

enum class ProviderStatus(val displayName: String, val color: Color) {
    ONLINE("ACTIVE", Primary),
    FAST("FAST 10G", Secondary),
    MAINTENANCE("BAKIM", Color(0xFFFF9800))
}

