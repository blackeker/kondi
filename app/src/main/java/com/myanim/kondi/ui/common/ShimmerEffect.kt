package com.myanim.kondi.ui.common

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class ShimmerConfig(
    val colors: List<Color> = defaultShimmerColors,
    val duration: Int = 1000,
    val delay: Int = 0,
    val easing: Easing = LinearEasing,
    val direction: ShimmerDirection = ShimmerDirection.LeftToRight,
    val intensity: Float = 1f,
    val width: Float = 0.3f,
    val blur: Dp = 0.dp
) {
    companion object {
        val defaultShimmerColors = listOf(
            Color.LightGray.copy(alpha = 0.2f),
            Color.LightGray.copy(alpha = 0.1f)
        )
        val goldShimmerColors = defaultShimmerColors
        val silverShimmerColors = defaultShimmerColors
        val rainbowShimmerColors = defaultShimmerColors
        val neonShimmerColors = defaultShimmerColors
        val fireShimmerColors = defaultShimmerColors
        val iceShimmerColors = defaultShimmerColors
        val metallicShimmerColors = defaultShimmerColors
    }
}

enum class ShimmerDirection { LeftToRight, RightToLeft, TopToBottom, BottomToTop, DiagonalTopLeftToBottomRight, DiagonalTopRightToBottomLeft, DiagonalBottomLeftToTopRight, DiagonalBottomRightToTopLeft }
enum class ShimmerType { Linear, Radial, Sweep, Wave, Pulse, Rainbow, Breathing, Ripple, Sparkle, Aurora, Holographic, Metallic }

@Composable
fun shimmerBrush(showShimmer: Boolean = true, targetValue: Float = 1000f, config: ShimmerConfig = ShimmerConfig()): Brush {
    return if (showShimmer) {
        Brush.linearGradient(colors = config.colors.take(2).ifEmpty { ShimmerConfig.defaultShimmerColors })
    } else {
        Brush.linearGradient(colors = listOf(Color.Transparent, Color.Transparent))
    }
}

@Composable
fun enhancedShimmerBrush(showShimmer: Boolean = true, type: ShimmerType = ShimmerType.Linear, config: ShimmerConfig = ShimmerConfig()): Brush {
    return shimmerBrush(showShimmer, 1000f, config)
}

fun Modifier.shimmerBackground(showShimmer: Boolean = true, type: ShimmerType = ShimmerType.Linear, config: ShimmerConfig = ShimmerConfig()): Modifier = composed {
    this.background(shimmerBrush(showShimmer, config = config))
}

fun Modifier.animatedGradientBackground(colors: List<Color>, duration: Int = 3000, easing: Easing = LinearEasing): Modifier = composed {
    this.background(colors.firstOrNull() ?: Color.Transparent)
}

fun Modifier.pulsingGlow(color: Color, duration: Int = 2000, minAlpha: Float = 0.3f, maxAlpha: Float = 0.8f): Modifier = composed {
    this.background(color.copy(alpha = minAlpha))
}

fun Modifier.rotatingGradient(colors: List<Color>, duration: Int = 3000): Modifier = composed { this }
fun Modifier.waveBackground(colors: List<Color>, duration: Int = 2000, amplitude: Float = 100f): Modifier = composed { this }
fun Modifier.pixelatedShimmer(colors: List<Color>, pixelSize: Dp = 10.dp, duration: Int = 1500): Modifier = composed { this }
fun Modifier.shimmerBorder(width: Dp = 2.dp, colors: List<Color>, duration: Int = 2000, shape: Shape = RectangleShape): Modifier = composed { this }

object ShimmerPresets {
    val gold = ShimmerConfig()
    val silver = ShimmerConfig()
    val rainbow = ShimmerConfig()
    val neon = ShimmerConfig()
    val fire = ShimmerConfig()
    val ice = ShimmerConfig()
    val metallic = ShimmerConfig()
    val fast = ShimmerConfig()
    val slow = ShimmerConfig()
    val subtle = ShimmerConfig()
    val intense = ShimmerConfig()
}

@Composable
fun multiLayerShimmerBrush(layers: List<Pair<ShimmerType, ShimmerConfig>>, blendMode: BlendMode = BlendMode.Plus): Brush {
    return shimmerBrush(true)
}

fun Modifier.animatedShimmerBorder(width: Dp = 2.dp, type: ShimmerType = ShimmerType.Linear, config: ShimmerConfig = ShimmerConfig(), shape: Shape = RectangleShape): Modifier = composed { this }

@Composable
fun textShimmerBrush(colors: List<Color> = ShimmerConfig.defaultShimmerColors, duration: Int = 1500): Brush {
    return shimmerBrush(true)
}

fun Modifier.combinedShimmer(vararg effects: Pair<ShimmerType, ShimmerConfig>): Modifier = composed { this }