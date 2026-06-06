package com.myanim.kondi.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myanim.kondi.ui.theme.PureBlack

@Composable
fun KondiBackground(
    modifier: Modifier = Modifier,
    showParticles: Boolean = false, // Kept for API compatibility but unused
    showFloatingShapes: Boolean = false,
    showConstellation: Boolean = false,
    showEnhancedBackground: Boolean = false,
    showPulsingGlow: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1E1E2C), // Subtle dark purple/blue tone
                        Color(0xFF0F0F16)  // Near black
                    ),
                    radius = 2500f,
                    center = androidx.compose.ui.geometry.Offset(0f, 0f)
                )
            )
    ) {
        // Top right glow
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(400.dp)
                .offset(x = 100.dp, y = (-100).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    ),
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        )
        // Bottom left glow
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(500.dp)
                .offset(x = (-150).dp, y = 150.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.20f),
                            Color.Transparent
                        )
                    ),
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        )
    }
}

@Composable
fun EnhancedKondiButton(
    text: String,
    subtitle: String,
    icon: ImageVector,
    gradient: Brush = Brush.linearGradient(listOf(Color(0xFF1A1A1A), Color(0xFF121212))),
    shimmerEnabled: Boolean = false,
    onClick: () -> Unit,
    delayMillis: Int = 0,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(90.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun LoadingProgressBar(
    progress: Float, 
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .width(200.dp)
                .height(4.dp),
            color = color,
            trackColor = color.copy(alpha = 0.1f),
            strokeCap = StrokeCap.Round
        )
        if (progress < 1f) {
            Text(
                text = "Yükleniyor... ${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun PulsingDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .background(Color.White, androidx.compose.foundation.shape.CircleShape)
    )
}

// Stub classes for compatibility if needed elsewhere
data class RippleEffect(val x: Float, val y: Float, var radius: Float, var alpha: Float, val maxRadius: Float, val color: Color)
@Composable
fun RippleEffects(ripples: MutableList<RippleEffect>) { /* No-op */ }
