package com.myanim.kondi.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myanim.kondi.ui.theme.*

/**
 * Hata durumu ekranı - sade ve klasik
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = "Hata",
            tint = Error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            color = TextPrimary,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        SimpleButton(
            text = "Tekrar Dene",
            onClick = onRetry,
            modifier = Modifier.width(120.dp)
        )
    }
}

/**
 * Yükleme durumu - sade progress indicator
 */
@Composable
fun LoadingState(
    message: String = "Yükleniyor...",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = Primary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

/**
 * Boş durum ekranı
 */
@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Inbox,
    title: String = "Henüz içerik yok",
    subtitle: String = "",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextDisabled,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        if (subtitle.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

/**
 * Sade kart bileşeni
 */
@Composable
fun SimpleCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Card(
        modifier = modifier.then(clickModifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        content()
    }
}

/**
 * Sade buton
 */
@Composable
fun SimpleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) Primary else OutlineVariant,
            contentColor = if (enabled) OnPrimary else TextDisabled
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(text, color = if (enabled) OnPrimary else TextDisabled)
    }
}

/**
 * Sade outlined buton
 */
@Composable
fun SimpleOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (enabled) Primary else TextDisabled
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(text, color = if (enabled) Primary else TextDisabled)
    }
}

/**
 * Sade metin alanı
 */
@Composable
fun SimpleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "",
    placeholder: String = "",
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailingIcon: (() -> Unit)? = null,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions(),
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions(),
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { if (label.isNotEmpty()) Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, color = TextSecondary) },
        leadingIcon = { leadingIcon?.let { Icon(imageVector = it, contentDescription = null) } },
        trailingIcon = { trailingIcon?.let { IconButton(onClick = it) { Icon(Icons.Default.Clear, contentDescription = "Temizle") } } },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SurfaceCard,
            unfocusedContainerColor = SurfaceCard,
            focusedIndicatorColor = Primary,
            unfocusedIndicatorColor = OutlineVariant,
            disabledContainerColor = SurfaceElevated
        ),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier,
        enabled = enabled,
        singleLine = true
    )
}

/**
 * Sade liste öğesi
 */
@Composable
fun SimpleListItem(
    leading: @Composable () -> Unit = {},
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
        ) {
            title()
            if (subtitle != {}) {
                Spacer(modifier = Modifier.height(4.dp))
                subtitle()
            }
        }
        trailing()
    }
}

/**
 * Sade divider
 */
@Composable
fun SimpleDivider(
    modifier: Modifier = Modifier
) {
    Divider(
        modifier = modifier,
        color = Divider,
        thickness = 1.dp
    )
}

/**
 * Sade section başlığı
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = TextPrimary,
        modifier = modifier.padding(bottom = 8.dp)
    )
}

/**
 * Anime poster kutusu
 */
@Composable
fun AnimePoster(
    url: String?,
    size: Dp = 100.dp,
    contentDescription: String? = null
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(SurfaceElevated, shape = RoundedCornerShape(12.dp))
    ) {
        coil.compose.AsyncImage(
            model = url ?: "file:///android_asset/placeholder.png",
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    }
}
