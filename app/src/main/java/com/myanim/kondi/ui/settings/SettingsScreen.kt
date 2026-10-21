package com.myanim.kondi.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myanim.kondi.data.prefs.UserPreferencesManager
import com.myanim.kondi.ui.common.SectionHeader
import com.myanim.kondi.ui.common.SimpleCard
import com.myanim.kondi.ui.common.SimpleListItem
import com.myanim.kondi.ui.theme.Error
import com.myanim.kondi.ui.theme.SurfaceCard
import com.myanim.kondi.ui.theme.TextPrimary
import com.myanim.kondi.ui.theme.TextSecondary

/**
 * Uygulama ayarları ekranı.
 *
 * [onStorageClick] depolama yönetimine, [onBack] geri dönüşe götürür.
 */
@Composable
fun SettingsScreen(
    prefs: UserPreferencesManager,
    onStorageClick: () -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Tercihler her yeniden çizimde okunur; değişiklik anında yazılır.
    var multiChunk by remember { mutableStateOf(prefs.enableMultiChunk) }
    var threads by remember { mutableFloatStateOf(prefs.downloadThreads.toFloat()) }
    var serverIp by remember { mutableStateOf(prefs.hdwpServerIp) }
    var targetPhone by remember { mutableStateOf(prefs.hdwpTargetPhone) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Üst bar: geri + başlık
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard)
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Geri",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Uygulama Ayarları",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
        // ---- İndirme ayarları ----
        SectionHeader(title = "İndirme")

        SimpleCard {
            SettingSwitchRow(
                icon = Icons.Default.PlayCircle,
                title = "Çok parçalı indirme",
                subtitle = "Dosyayı paralel parçalar halinde indirir, hızı artırır",
                checked = multiChunk,
                onCheckedChange = {
                    multiChunk = it
                    prefs.enableMultiChunk = it
                }
            )

            SettingSliderRow(
                icon = Icons.Default.Speed,
                title = "Eşzamanlı bağlantı",
                value = threads,
                valueLabel = "${threads.toInt()} bağlantı",
                valueRange = UserPreferencesManager.MIN_DOWNLOAD_THREADS.toFloat()..
                    UserPreferencesManager.MAX_DOWNLOAD_THREADS.toFloat(),
                enabled = multiChunk,
                onValueChange = {
                    threads = it
                    prefs.downloadThreads = it.toInt()
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ---- HDWP sunucu ayarları ----
        SectionHeader(title = "HDWP Sunucusu")

        SimpleCard {
            OutlinedTextField(
                value = serverIp,
                onValueChange = {
                    serverIp = it
                    prefs.hdwpServerIp = it
                },
                label = { Text("Sunucu adresi") },
                placeholder = { Text("http://111.235.150.203:7860") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            OutlinedTextField(
                value = targetPhone,
                onValueChange = {
                    targetPhone = it
                    prefs.hdwpTargetPhone = it
                },
                label = { Text("Hedef telefon numarası") },
                placeholder = { Text("905xxxxxxxxx") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ---- Depolama ----
        SectionHeader(title = "Depolama")

        SimpleCard {
            SimpleListItem(
                leading = { SettingIcon(Icons.Default.Storage) },
                title = { Text("Depolama yönetimi", color = TextPrimary) },
                subtitle = {
                    Text("İndirilen ve önbelleğe alınan dosyaları temizle", color = TextSecondary, fontSize = 13.sp)
                },
                trailing = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                },
                onClick = onStorageClick
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ---- Hakkında ----
        SectionHeader(title = "Hakkında")

        SimpleCard {
            SimpleListItem(
                leading = { SettingIcon(Icons.Default.Info) },
                title = { Text("Kondi", color = TextPrimary) },
                subtitle = {
                    Text("Anime izleme ve indirme uygulaması · sürüm 1.0", color = TextSecondary, fontSize = 13.sp)
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = TextSecondary,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SimpleListItem(
        leading = { SettingIcon(icon) },
        title = { Text(title, color = TextPrimary) },
        subtitle = { Text(subtitle, color = TextSecondary, fontSize = 13.sp) },
        trailing = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}

@Composable
private fun SettingSliderRow(
    icon: ImageVector,
    title: String,
    value: Float,
    valueLabel: String,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingIcon(icon)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.Medium)
                Text(
                    text = valueLabel,
                    color = if (enabled) TextSecondary else Error,
                    fontSize = 13.sp
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            steps = (valueRange.endInclusive - valueRange.start).toInt() - 1
        )
    }
}
