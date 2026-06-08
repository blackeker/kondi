package com.myanim.kondi.data.download

import android.util.Log
import kotlinx.coroutines.delay
import kotlin.math.pow

object DownloadUtils {
    private const val TAG = "DownloadUtils"

    suspend fun <T> retryWithBackoff(
        maxRetries: Int = 5,
        initialDelay: Long = 1000L,
        maxDelay: Long = 30000L,
        factor: Double = 2.0,
        onRetry: (Int, Exception) -> Unit = { _, _ -> },
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // Never retry on cancellation
            } catch (e: PausedException) {
                throw e // Never retry on pause
            } catch (e: Exception) {
                if (e.message == "PAUSED" || attempt == maxRetries - 1) {
                    throw e
                }
                
                onRetry(attempt + 1, e)
                Log.w(TAG, "Attempt ${attempt + 1} failed, retrying in ${currentDelay}ms: ${e.message}")
                
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
        }
        return block() // Should not reach here
    }

    fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    fun createAnimecixFileName(animeName: String, season: Int, episode: Int): String {
        val s = season.toString().padStart(2, '0')
        val e = episode.toString().padStart(2, '0')
        return sanitizeFileName("${animeName}_s${s}_e${e}.mp4")
    }
}
