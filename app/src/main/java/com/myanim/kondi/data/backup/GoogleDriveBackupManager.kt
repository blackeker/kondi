package com.myanim.kondi.data.backup

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.gson.Gson
import com.myanim.kondi.data.local.KondiDatabase
import com.myanim.kondi.data.local.Favorite
import com.myanim.kondi.data.local.WatchHistory
import com.myanim.kondi.data.local.Watchlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

data class BackupPayload(
    val favorites: List<Favorite>,
    val watchlist: List<Watchlist>,
    val watchHistory: List<WatchHistory>,
    val settings: Map<String, String>
)

class GoogleDriveBackupManager(private val context: Context) {
    companion object {
        private const val TAG = "GDriveBackup"
        private const val BACKUP_FILENAME = "kondi_backup.json"
        
        // We use drive.appdata scope which targets the hidden appDataFolder in Google Drive
        private val DRIVE_SCOPE = Scope("https://www.googleapis.com/auth/drive.appdata")
    }

    private val gson = Gson()
    private val client = OkHttpClient()

    val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(DRIVE_SCOPE)
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    suspend fun getAccessToken(account: GoogleSignInAccount): String? = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.getToken(
                context,
                account.account ?: return@withContext null,
                "oauth2:https://www.googleapis.com/auth/drive.appdata"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting access token", e)
            null
        }
    }

    suspend fun createBackupJson(): String = withContext(Dispatchers.IO) {
        val db = KondiDatabase.getDatabase(context)
        val favorites = db.favoriteDao().getAllFavorites().first()
        val watchlist = db.watchlistDao().getAllWatchlist().first()
        val history = db.watchHistoryDao().getAllHistory().first()
        
        val sharedPrefs = context.getSharedPreferences("kondi_user_prefs", Context.MODE_PRIVATE)
        val settings = mutableMapOf<String, String>()
        sharedPrefs.all.forEach { (key, value) ->
            settings[key] = value.toString()
        }

        val payload = BackupPayload(favorites, watchlist, history, settings)
        gson.toJson(payload)
    }

    suspend fun performBackup(account: GoogleSignInAccount): Boolean = withContext(Dispatchers.IO) {
        var token = getAccessToken(account) ?: return@withContext false
        val backupContent = createBackupJson()

        try {
            executeBackupFlow(token, backupContent)
            true
        } catch (e: Exception) {
            val is403 = e is IOException && e.message?.contains("403") == true
            if (is403) {
                Log.d(TAG, "403 error detected, clearing cached token and retrying...")
                try {
                    GoogleAuthUtil.clearToken(context, token)
                } catch (ex: Exception) {
                    Log.e(TAG, "Failed to clear token", ex)
                }
                token = getAccessToken(account) ?: return@withContext false
                try {
                    executeBackupFlow(token, backupContent)
                    true
                } catch (e2: Exception) {
                    Log.e(TAG, "Backup retry failed", e2)
                    false
                }
            } else {
                Log.e(TAG, "Backup failed", e)
                false
            }
        }
    }

    private fun executeBackupFlow(token: String, backupContent: String) {
        val fileId = findBackupFile(token)
        if (fileId != null) {
            updateBackupFile(token, fileId, backupContent)
        } else {
            createBackupFile(token, backupContent)
        }
    }

    suspend fun performRestore(account: GoogleSignInAccount): Boolean = withContext(Dispatchers.IO) {
        var token = getAccessToken(account) ?: return@withContext false
        try {
            val fileId = executeRestoreFlow(account, token) ?: return@withContext false
            val json = downloadBackupFile(token, fileId) ?: return@withContext false
            
            val payload = gson.fromJson(json, BackupPayload::class.java) ?: return@withContext false
            
            val db = KondiDatabase.getDatabase(context)
            db.runInTransaction {
                runBlocking {
                    payload.favorites.forEach { db.favoriteDao().insertFavorite(it) }
                    payload.watchlist.forEach { db.watchlistDao().insertWatchlist(it) }
                    payload.watchHistory.forEach { db.watchHistoryDao().insertHistory(it) }
                }
            }

            val sharedPrefs = context.getSharedPreferences("kondi_user_prefs", Context.MODE_PRIVATE)
            val editor = sharedPrefs.edit()
            payload.settings.forEach { (key, value) ->
                when {
                    value == "true" || value == "false" -> editor.putBoolean(key, value.toBoolean())
                    value.toIntOrNull() != null -> editor.putInt(key, value.toInt())
                    else -> editor.putString(key, value)
                }
            }
            editor.apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            false
        }
    }

    private suspend fun executeRestoreFlow(account: GoogleSignInAccount, initialToken: String): String? {
        var token = initialToken
        return try {
            findBackupFile(token)
        } catch (e: Exception) {
            val is403 = e is IOException && e.message?.contains("403") == true
            if (is403) {
                Log.d(TAG, "403 error on restore, clearing token and retrying...")
                try {
                    GoogleAuthUtil.clearToken(context, token)
                } catch (ex: Exception) {
                    Log.e(TAG, "Failed to clear token", ex)
                }
                token = getAccessToken(account) ?: return null
                try {
                    findBackupFile(token)
                } catch (e2: Exception) {
                    Log.e(TAG, "Restore retry failed", e2)
                    null
                }
            } else {
                null
            }
        }
    }

    private fun findBackupFile(token: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files?q=name='$BACKUP_FILENAME'+and+'appDataFolder'+in+parents&spaces=appDataFolder"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 403) throw IOException("403 Forbidden")
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val files = json.optJSONArray("files")
            if (files != null && files.length() > 0) {
                return files.getJSONObject(0).optString("id")
            }
        }
        return null
    }

    private fun createBackupFile(token: String, content: String) {
        val metadata = JSONObject().apply {
            put("name", BACKUP_FILENAME)
            put("parents", listOf("appDataFolder"))
        }

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addPart(
                metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
            )
            .addPart(
                content.toRequestBody("application/json; charset=UTF-8".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            .header("Authorization", "Bearer $token")
            .post(multipartBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 403) throw IOException("403 Forbidden")
            if (!response.isSuccessful) throw IOException("Failed to create file: ${response.code} ${response.message}")
        }
    }

    private fun updateBackupFile(token: String, fileId: String, content: String) {
        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
            .header("Authorization", "Bearer $token")
            .patch(content.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 403) throw IOException("403 Forbidden")
            if (!response.isSuccessful) throw IOException("Failed to update file: ${response.code} ${response.message}")
        }
    }

    private fun downloadBackupFile(token: String, fileId: String): String? {
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .header("Authorization", "Bearer $token")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 403) throw IOException("403 Forbidden")
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }
}
