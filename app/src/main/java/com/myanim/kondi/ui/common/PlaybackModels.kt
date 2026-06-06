package com.myanim.kondi.ui.common

import androidx.media3.common.util.UnstableApi

@UnstableApi
data class PlayerState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isEnded: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L
)

@UnstableApi
data class UiState(
    val isControlsVisible: Boolean = true,
    val isFullscreen: Boolean = false,
    val isInPip: Boolean = false,
    val isSettingsVisible: Boolean = false,
    val showSpeedMenu: Boolean = false,
    val showQualityMenu: Boolean = false,
    val showSubtitleMenu: Boolean = false,
    val showSleepTimerMenu: Boolean = false,
    val controlsAutoHideTime: Long = 4000L,
    val isSpeedRamping: Boolean = false,
    val showEpisodeList: Boolean = false,
    val showSubtitleSearch: Boolean = false,
    val networkSpeed: String = "0 KB/s",
    val showTrimMenu: Boolean = false,
    val showBookmarkDialog: Boolean = false
)

@UnstableApi
data class GestureState(
    val brightness: Float = -1f,
    val volume: Float = 0.5f,
    val showBrightnessIndicator: Boolean = false,
    val showVolumeIndicator: Boolean = false,
    val isSeeking: Boolean = false,
    val seekPosition: Long = 0L,
    val showRewindAnimation: Boolean = false,
    val showForwardAnimation: Boolean = false,
    val rewindCount: Int = 0,
    val forwardCount: Int = 0,
    val seekDirection: String = "", // "forward" or "rewind"
    val lastGestureTime: Long = 0L
)

@UnstableApi
data class SettingsState(
    val playbackSpeed: Float = 1f,
    val autoPlayNext: Boolean = false,
    val loopPlayback: Boolean = false,
    val currentQuality: VideoQuality = VideoQuality.Auto,
    val availableQualities: List<VideoQuality> = listOf(VideoQuality.Auto),
    val currentSubtitle: SubtitleTrack? = null,
    val availableSubtitles: List<SubtitleTrack> = emptyList(),
    val sleepTimerEndTime: Long? = null,
    val sleepTimerRemaining: Int = 0,
    val screenshotPath: String? = null,
    val gestureSensitivity: Float = 1.0f,
    val hapticFeedbackEnabled: Boolean = true,
    val pointA: Long? = null,
    val pointB: Long? = null,
    val isAbRepeatEnabled: Boolean = false,
    
    // Subtitle Customization
    val subtitleFontSize: Int = 18,
    val subtitleColor: Int = android.graphics.Color.WHITE,
    val subtitleBgOpacity: Int = 120,
    val subtitleVerticalOffset: Int = 0,
    
    // Video Filters & Effects
    val videoBrightness: Float = 0f, // -1.0 to 1.0
    val videoContrast: Float = 1f,   // 0.0 to 2.0
    val videoSaturation: Float = 1f, // 0.0 to 2.0
    val videoAspectRatio: AspectRatioMode = AspectRatioMode.Fit,
    
    // Trimming fields
    val trimStart: Long = 0L,
    val trimEnd: Long = 0L,
    val isTrimEnabled: Boolean = false
)

enum class AspectRatioMode(val label: String) {
    Fit("Sığdır"),
    Fill("Doldur"),
    Zoom("Zoom"),
    Original("Orijinal"),
    Wide16_9("16:9"),
    Standard4_3("4:3")
}

data class VideoQuality(
    val label: String,
    val height: Int,
    val trackIndex: Int = -1
) {
    companion object {
        val Auto = VideoQuality("Otomatik", 0, -1)
        val Q2160p = VideoQuality("4K", 2160)
        val Q1440p = VideoQuality("2K", 1440)
        val Q1080p = VideoQuality("Full HD", 1080)
        val Q720p = VideoQuality("HD", 720)
        val Q480p = VideoQuality("SD", 480)
        val Q360p = VideoQuality("Düşük", 360)
    }
}

data class SubtitleTrack(
    val language: String,
    val trackIndex: Int
)
