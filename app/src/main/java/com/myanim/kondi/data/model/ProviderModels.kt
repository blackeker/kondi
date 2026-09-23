package com.myanim.kondi.data.model

import androidx.compose.ui.graphics.Color
import com.myanim.kondi.ui.theme.*

enum class ProviderStatus(val displayName: String, val color: Color) {
    ONLINE("ACTIVE", EmeraldActive),
    FAST("FAST 10G", NeonCyan),
    MAINTENANCE("BAKIM", Color(0xFFFF9800))
}

