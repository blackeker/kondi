package com.myanim.kondi.ui.common

import android.os.Build
// Removed animation imports
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.composed

val CapsuleShape = RoundedCornerShape(50)

fun Modifier.bounceClick() = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
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
            .clip(shape)
    ) {
        // Separate background layer for blur to keep content sharp
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

        // Interactive and Content layer
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

/**
 * A container that applies glassmorphism to the background ONLY,
 * ensuring children are not blurred.
 */
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

/**
 * Common transparent TopAppBar with a glass gradient feel
 */
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
