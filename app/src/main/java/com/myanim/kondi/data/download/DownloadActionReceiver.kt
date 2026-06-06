package com.myanim.kondi.data.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * Bildirim aksiyonlarını dinleyen BroadcastReceiver
 */
class DownloadActionReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val downloadId = intent.getStringExtra(DownloadNotificationManager.EXTRA_DOWNLOAD_ID)
            ?: return
        
        when (intent.action) {
            DownloadNotificationManager.ACTION_CANCEL -> {
                handleCancelAction(context, downloadId)
            }
            DownloadNotificationManager.ACTION_PAUSE -> {
                handlePauseAction(context, downloadId)
            }
            DownloadNotificationManager.ACTION_RESUME -> {
                handleResumeAction(context, downloadId)
            }
        }
    }
    
    private fun handleCancelAction(context: Context, downloadId: String) {
        // DownloadManager'a cancel eventi gönder
        val intent = Intent(ACTION_DOWNLOAD_CANCEL).apply {
            putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        
        // Bildirimi kaldır
        DownloadNotificationManager.getInstance(context).cancelNotification(downloadId)
    }
    
    private fun handlePauseAction(context: Context, downloadId: String) {
        // DownloadManager'a pause eventi gönder
        val intent = Intent(ACTION_DOWNLOAD_PAUSE).apply {
            putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }
    
    private fun handleResumeAction(context: Context, downloadId: String) {
        // DownloadManager'a resume eventi gönder
        val intent = Intent(ACTION_DOWNLOAD_RESUME).apply {
            putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }
    
    companion object {
        const val ACTION_DOWNLOAD_CANCEL = "com.myanim.kondi.DOWNLOAD_CANCEL"
        const val ACTION_DOWNLOAD_PAUSE = "com.myanim.kondi.DOWNLOAD_PAUSE"
        const val ACTION_DOWNLOAD_RESUME = "com.myanim.kondi.DOWNLOAD_RESUME"
        const val EXTRA_DOWNLOAD_ID = DownloadNotificationManager.EXTRA_DOWNLOAD_ID
    }
}
