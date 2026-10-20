package com.myanim.kondi.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.myanim.kondi.ui.common.*
import com.myanim.kondi.ui.theme.*

/**
 * Ana ekran - sade, klasik ve ergonomik
 * Kullanıcının "arayüzü sıfırdan yaz daha düzgün ergonomik sade klasik bir şey" hedefine uygun olarak tasarlandı.
 */
@Composable
fun HomeScreen(
    onDownloadsClick: () -> Unit,
    onStorageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Üst başlık bölümü
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .background(SurfaceCard)
        ) {
            Text(
                text = "Kondi",
                style = MaterialTheme.typography.displayLarge,
                color = Primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "İndirme Yöneticisi",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hızlı erişim kartları
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            item {
                QuickActionCard(
                    title = "İndirilenler",
                    subtitle = "İndirdiğiniz içerikleri yönetin",
                    icon = Icons.Default.DownloadDone,
                    onClick = onDownloadsClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                QuickActionCard(
                    title = "Depolama Yönetimi",
                    subtitle = "Önbellek ve dosya temizliği",
                    icon = Icons.Default.Storage,
                    onClick = onStorageClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Text(
                    text = "Hızlı İşlemler",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                QuickActionRow(
                    onDownloadsClick = onDownloadsClick,
                    onStorageClick = onStorageClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Hızlı erişim kartı bileşeni
 */
@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SimpleCard(
        modifier = modifier
            .height(120.dp)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        onClick = null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            Icon(
                imageVector = Icons.Default.NavigateNext,
                contentDescription = null,
                tint = OutlineVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Hızlı işlem satırı
 */
@Composable
private fun QuickActionRow(
    onDownloadsClick: () -> Unit,
    onStorageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SimpleOutlinedButton(
            text = "İndirilenler",
            onClick = onDownloadsClick,
            modifier = Modifier.weight(1f)
        )
        SimpleOutlinedButton(
            text = "Depolama",
            onClick = onStorageClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Preview(showBackground = true, name = "HomeScreen")
@Composable
fun PreviewHomeScreen() {
    KondiTheme {
        HomeScreen(
            onDownloadsClick = {},
            onStorageClick = {}
        )
    }
}