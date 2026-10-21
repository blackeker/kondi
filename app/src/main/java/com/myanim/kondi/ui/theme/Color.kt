package com.myanim.kondi.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Temel yüzey renkleri
// ---------------------------------------------------------------------------
val SurfaceBackground = Color(0xFFF4F5FA)   // Sayfa arka planı (hafif mavi-gri)
val SurfaceCard = Color(0xFFFFFFFF)         // Kart yüzeyi
val SurfaceElevated = Color(0xFFF0F1F8)     // Yükseltilmiş yüzey
val SurfaceGradientTop = Color(0xFFEEF1FB)  // Hero gradyanı üst
val SurfaceGradientBottom = Color(0xFFF7F8FC)

// ---------------------------------------------------------------------------
// Marka renkleri
// ---------------------------------------------------------------------------
val Primary = Color(0xFF4F46E5)           // Canlı indigo - birincil
val PrimaryDark = Color(0xFF3730A3)        // Koyu indigo
val PrimaryLight = Color(0xFF818CF8)       // Açık indigo
val PrimaryContainer = Color(0xFFE0E7FF)   // Açık indigo tonu
val OnPrimary = Color(0xFFFFFFFF)

val Secondary = Color(0xFF0EA5E9)          // Camgöbeği - ikincil/vurgu
val SecondaryDark = Color(0xFF0369A1)
val SecondaryContainer = Color(0xFFE0F2FE)
val OnSecondary = Color(0xFFFFFFFF)

val Accent = Color(0xFFF59E0B)             // Amber - vurgu/rozet
val AccentContainer = Color(0xFFFEF3C7)
val Success = Color(0xFF10B981)            // Yeşil - tamamlandı
val SuccessContainer = Color(0xFFD1FAE5)
val Warning = Color(0xFFF59E0B)            // Turuncu - uyarı
val Error = Color(0xFFDC2626)              // Kırmızı - hata
val ErrorContainer = Color(0xFFFEE2E2)
val OnError = Color(0xFFFFFFFF)

// ---------------------------------------------------------------------------
// Yüzey / metin
// ---------------------------------------------------------------------------
val Background = Color(0xFFF4F5FA)
val OnBackground = Color(0xFF111827)

val Surface = Color(0xFFFFFFFF)
val OnSurface = Color(0xFF111827)

val Outline = Color(0xFFD1D5DB)
val OutlineVariant = Color(0xFFE5E7EB)

val TextPrimary = Color(0xFF111827)
val TextSecondary = Color(0xFF6B7280)
val TextDisabled = Color(0xFF9CA3AF)
val Divider = Color(0xFFEDEFF5)

// ---------------------------------------------------------------------------
// Destekleyici yardımcılar
// ---------------------------------------------------------------------------

/** Kart köşelerine yumuşak gölge veren gradyan fırçası. */
val CardShadowBrush = Brush.verticalGradient(
    colors = listOf(Color.Black.copy(alpha = 0.04f), Color.Transparent)
)

/** Üst bar / hero alanı için marka gradyanı. */
val HeroGradient = Brush.linearGradient(
    colors = listOf(Primary, PrimaryDark)
)

/** Kart üst bandı için yumuşak marka gradyanı. */
val AccentGradient = Brush.linearGradient(
    colors = listOf(Primary.copy(alpha = 0.10f), Secondary.copy(alpha = 0.05f))
)

/** Durum rozeti arka planları. */
val StatusCompletedContainer = SuccessContainer
val StatusFailedContainer = ErrorContainer
val StatusPausedContainer = Color(0xFFFEF3C7)
val StatusDownloadingContainer = PrimaryContainer
val StatusPendingContainer = SurfaceElevated

// ---------------------------------------------------------------------------
// Koyu tema renkleri
// ---------------------------------------------------------------------------
val Color0B = Color(0xFF0B0B12)             // Koyu temada "beyaz" karşılığı

val DarkBackground = Color(0xFF0E0E17)
val DarkSurface = Color(0xFF171722)
val DarkSurfaceElevated = Color(0xFF20202E)
val DarkOnBackground = Color(0xFFF3F4F6)
val DarkOnSurface = Color(0xFFF3F4F6)
val DarkTextSecondary = Color(0xFF9CA3AF)
val DarkOutline = Color(0xFF3A3A4D)
val DarkOutlineVariant = Color(0xFF2A2A3A)
val DarkError = Color(0xFFF87171)
