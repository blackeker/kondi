package com.myanim.kondi.ui.player

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.myanim.kondi.ui.theme.Background
import com.myanim.kondi.ui.theme.OnSurface
import com.myanim.kondi.ui.theme.TextSecondary

/**
 * İndirilmiş yerel videoları uygulama içinde oynatır.
 *
 * [mediaUri] mutlak dosya yolu, `file://` URI'si veya `content://` URI'si olabilir.
 * ExoPlayer dosya yolunda `file://` şeması beklediği için ham yollar dönüştürülür.
 */
@UnstableApi
@Composable
fun LocalVideoPlayerScreen(
    mediaUri: String,
    title: String,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(true) }
    var hasStarted by remember { mutableStateOf(false) }

    val player = remember(mediaUri) {
        val uri = toPlayableUri(mediaUri)
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                hasStarted = playbackState == Player.STATE_READY
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = "Video oynatılamadı: ${error.errorCodeName}"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowFastForwardButton(true)
                    setShowRewindButton(true)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { it.player = player }
        )

        // Üst bar: geri + başlık
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Geri",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isBuffering && !hasStarted) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        errorMessage?.let { message ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = message,
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onBack() }
                        )
                        Text(
                            text = "Geri dön",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { onBack() }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ham dosya yollarını ExoPlayer'ın anlayacağı URI biçimine çevirir.
 * `content://` ve `file://` zaten geçerli şemalar olduğu için olduğu gibi bırakılır.
 */
internal fun toPlayableUri(path: String): Uri {
    val trimmed = path.trim()
    return when {
        trimmed.startsWith("content://") || trimmed.startsWith("file://") ||
            trimmed.startsWith("http://") || trimmed.startsWith("https://") ->
            Uri.parse(trimmed)

        else -> Uri.fromFile(java.io.File(trimmed))
    }
}
