package com.myanim.kondi.ui.common

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.myanim.kondi.data.model.ProviderStatus
import com.myanim.kondi.ui.theme.*

val CapsuleShape = RoundedCornerShape(50)

fun Modifier.bounceClick() = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounceScale"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

fun Modifier.glassmorphismLayout(
    shape: Shape = RoundedCornerShape(24.dp),
    blurRadius: Dp = 24.dp,
    borderWidth: Dp = 1.dp,
    borderColor: Color = Color.White.copy(alpha = 0.25f),
    containerColor: Color? = null,
    backgroundGradient: Brush? = null,
    applyBlur: Boolean = true
): Modifier = this
    .clip(shape)
    .then(
        if (backgroundGradient != null) {
            Modifier.background(backgroundGradient)
        } else if (containerColor != null) {
            Modifier.background(containerColor)
        } else {
            Modifier.background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.02f)
                    )
                )
            )
        }
    )
    .border(borderWidth, borderColor, shape)
    .then(
        if (applyBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.blur(blurRadius)
        } else {
            Modifier
        }
    )

@Composable
fun GlassyCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    containerColor: Color? = null,
    blurRadius: Dp = 16.dp,
    borderWidth: Dp = 1.dp,
    borderColor: Color = Color.White.copy(alpha = 0.2f),
    onClick: (() -> Unit)? = null,
    applyBlur: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .glassmorphismLayout(
                    shape = shape,
                    containerColor = containerColor,
                    blurRadius = blurRadius,
                    borderWidth = borderWidth,
                    borderColor = borderColor,
                    applyBlur = applyBlur
                )
        )

        val interactiveModifier = if (onClick != null) {
            Modifier.bounceClick().clickable { onClick() }
        } else {
            Modifier
        }

        Box(
            modifier = Modifier.then(interactiveModifier),
            content = content
        )
    }
}

@Composable
fun GlassyBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    containerColor: Color? = null,
    blurRadius: Dp = 16.dp,
    borderWidth: Dp = 1.dp,
    borderColor: Color = Color.White.copy(alpha = 0.2f),
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .glassmorphismLayout(
                    shape = shape,
                    containerColor = containerColor,
                    blurRadius = blurRadius,
                    borderWidth = borderWidth,
                    borderColor = borderColor
                )
        )
        Box(
            modifier = Modifier.wrapContentSize(),
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassyTopAppBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        ),
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.6f),
                    Color.Transparent
                )
            )
        )
    )
}

/**
 * Derin obsidiyen/kömür siyahı (`#08080C`) ve hafif mor/mavi radyal ortam ışıltıları (ambient glow).
 */
@Composable
fun ObsidianGlowBackground(
    modifier: Modifier = Modifier,
    primaryGlow: Color = NeonMagenta,
    secondaryGlow: Color = NeonCyan,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambientGlowTransition")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        // Top Right Ambient Neon Radial Glow
        Box(
            modifier = Modifier
                .size(420.dp)
                .align(Alignment.TopEnd)
                .offset(x = 120.dp, y = (-80).dp)
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(90.dp)
                    } else Modifier
                )
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            primaryGlow.copy(alpha = glowAlpha),
                            primaryGlow.copy(alpha = glowAlpha * 0.4f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Bottom Left Ambient Neon Radial Glow
        Box(
            modifier = Modifier
                .size(460.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-140).dp, y = 120.dp)
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(100.dp)
                    } else Modifier
                )
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            secondaryGlow.copy(alpha = glowAlpha * 0.85f),
                            secondaryGlow.copy(alpha = glowAlpha * 0.3f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        content()
    }
}

/**
 * Lüks Cam Efekti Kartı (Yarı saydam dolgu, 1dp neon/beyaz ışıltılı kenar)
 */
@Composable
fun LuxuryGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    containerColor: Color = GlassSurface,
    borderColor: Color = GlassBorder,
    borderGradient: Brush? = null,
    borderWidth: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionModifier = if (onClick != null) {
        Modifier
            .bounceClick()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(interactionModifier)
            .clip(shape)
            .background(containerColor)
            .then(
                if (borderGradient != null) {
                    Modifier.border(borderWidth, borderGradient, shape)
                } else {
                    Modifier.border(borderWidth, borderColor, shape)
                }
            ),
        content = content
    )
}

/**
 * Zümrüt Yeşili / Neon Canlı Pulse Dot ve Durum Rozeti
 */
@Composable
fun GlowingStatusBadge(
    status: ProviderStatus = ProviderStatus.ONLINE,
    customText: String? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val badgeColor = status.color
    val text = customText ?: status.displayName

    Row(
        modifier = modifier
            .clip(CapsuleShape)
            .background(badgeColor.copy(alpha = 0.12f))
            .border(0.8.dp, badgeColor.copy(alpha = 0.35f), CapsuleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.size(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                    .background(badgeColor.copy(alpha = pulseAlpha), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(badgeColor, CircleShape)
            )
        }

        Text(
            text = text,
            color = badgeColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Neon Kategori Filtre Çipi
 */
@Composable
fun NeonCategoryChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    activeColor: Color = NeonCyan,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chipScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(CapsuleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .background(
                if (isSelected) activeColor.copy(alpha = 0.20f) else GlassSurface
            )
            .border(
                1.dp,
                if (isSelected) activeColor.copy(alpha = 0.85f) else GlassBorderSubtle,
                CapsuleShape
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.White else TextMed,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
        )
    }
}

/**
 * Spotlight Featured Hero Banner (Geniş Sinematik Vitrin Kartı)
 */
@Composable
fun SpotlightHeroBanner(
    title: String,
    posterUrl: String?,
    backdropUrl: String? = null,
    quality: String = "1080p FHD",
    rating: String = "9.8",
    episodeText: String? = null,
    synopsis: String = "",
    onPlayClick: () -> Unit,
    onDetailsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LuxuryGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = GlassSurface,
        borderGradient = Brush.linearGradient(
            listOf(
                NeonMagenta.copy(alpha = 0.6f),
                GlassBorder,
                NeonCyan.copy(alpha = 0.4f)
            )
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = backdropUrl ?: posterUrl ?: "file:///android_asset/placeholder.png",
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                ObsidianBlack.copy(alpha = 0.6f),
                                ObsidianBlack.copy(alpha = 0.96f)
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeonMagenta.copy(alpha = 0.85f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = quality,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(CapsuleShape)
                        .background(ObsidianBlack.copy(alpha = 0.7f))
                        .border(0.8.dp, AmberRating.copy(alpha = 0.4f), CapsuleShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = AmberRating,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = rating,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (episodeText != null) {
                    Text(
                        text = episodeText,
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                }

                Text(
                    text = title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (synopsis.isNotBlank()) {
                    Text(
                        text = synopsis,
                        fontSize = 11.sp,
                        color = TextMed,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPlayClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.Black
                            )
                            Text(
                                text = "Hemen İzle",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onDetailsClick,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = "Detaylar",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 2 veya 3 Sütunlu Medya Izgarası Poster Kartı
 * Kart Üzeri: Bölüm rozeti (`EP 12`), Kalite, Puan (`★ 9.8`), ve tek dokunuşla favori ekleme kalbi (`❤️`).
 */
@Composable
fun MediaPosterCard(
    title: String,
    posterUrl: String?,
    quality: String = "1080p FHD",
    episodeText: String? = null,
    rating: String = "9.8",
    isFavorite: Boolean = false,
    onFavoriteToggle: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var favoriteState by remember(title, isFavorite) { mutableStateOf(isFavorite) }

    LuxuryGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp),
        shape = RoundedCornerShape(20.dp),
        containerColor = GlassSurface,
        borderColor = GlassBorder,
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                AsyncImage(
                    model = posterUrl ?: "file:///android_asset/placeholder.png",
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.75f)
                                ),
                                startY = 120f
                            )
                        )
                )

                // Top Left: Quality / Year Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ObsidianBlack.copy(alpha = 0.8f))
                        .border(0.5.dp, GlassBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = quality,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeonCyan
                    )
                }

                // Top Right: Favorite Heart Button
                if (onFavoriteToggle != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(ObsidianBlack.copy(alpha = 0.75f))
                            .border(0.6.dp, GlassBorder, CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                favoriteState = !favoriteState
                                onFavoriteToggle()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (favoriteState) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favori",
                            tint = if (favoriteState) ElectricPink else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Bottom Left: Episode Badge (EP 12)
                if (episodeText != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.horizontalGradient(listOf(NeonCyan, NeonMagenta))
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = episodeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }
                }

                // Bottom Right: Rating Badge
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(CapsuleShape)
                        .background(ObsidianBlack.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = AmberRating,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = rating,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Animecix Simulcast",
                    fontSize = 11.sp,
                    color = TextMed,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Ekranın Altında Yüzen Cam Efektli Navigasyon Çubuğu (Floating Glass Dock)
 * Sekmeler: Ana Sayfa, Katalog, Arama, Favorilerim, Kasa
 */
data class DockNavigationItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tabIndex: Int
)

@Composable
fun FloatingGlassDock(
    selectedTab: Int,
    onTabSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember {
        listOf(
            DockNavigationItem("Ana Sayfa", Icons.Default.Home, Icons.Default.Home, 0),
            DockNavigationItem("Arama", Icons.Default.Search, Icons.Default.Search, 1),
            DockNavigationItem("Favorilerim", Icons.Default.Favorite, Icons.Outlined.FavoriteBorder, 2),
            DockNavigationItem("Kasa", Icons.Default.Shield, Icons.Default.Shield, 3)
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        LuxuryGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp),
            shape = RoundedCornerShape(33.dp),
            containerColor = Color(0xFF0C0C14).copy(alpha = 0.88f),
            borderGradient = Brush.horizontalGradient(
                listOf(
                    NeonCyan.copy(alpha = 0.4f),
                    GlassBorder,
                    NeonMagenta.copy(alpha = 0.4f)
                )
            ),
            borderWidth = 1.2.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { navItem ->
                    val isSelected = selectedTab == navItem.tabIndex

                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.15f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "dockIconScale"
                    )

                    val activeColor = when (navItem.tabIndex) {
                        0 -> NeonCyan
                        1 -> NeonMagenta
                        2 -> ElectricPink
                        3 -> EmeraldActive
                        else -> NeonCyan
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onTabSelect(navItem.tabIndex) }
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CapsuleShape)
                                .background(
                                    if (isSelected) activeColor.copy(alpha = 0.16f) else Color.Transparent
                                )
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isSelected) navItem.selectedIcon else navItem.unselectedIcon,
                                    contentDescription = navItem.title,
                                    tint = if (isSelected) activeColor else TextMed,
                                    modifier = Modifier
                                        .size(19.dp)
                                        .graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                                )

                                AnimatedVisibility(
                                    visible = isSelected,
                                    enter = fadeIn() + expandHorizontally(),
                                    exit = fadeOut() + shrinkHorizontally()
                                ) {
                                    Row {
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = navItem.title,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = activeColor,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

