package com.example.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * Listens for [DownloadManager] completion broadcasts and launches the
 * system package installer with the downloaded APK.
 *
 * Registered in AndroidManifest.xml for [DownloadManager.ACTION_DOWNLOAD_COMPLETE].
 */
class DownloadCompleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (downloadId == -1L) return

        Log.d(TAG, "Download complete broadcast received: id=$downloadId")

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = dm.query(query) ?: return

        try {
            if (!cursor.moveToFirst()) return

            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                Log.w(TAG, "Download did not succeed: status=$status")
                return
            }

            // Prefer reading from app external downloads dir if file exists
            val appDownloadFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "AirMouse.apk")
            val targetFile = if (appDownloadFile.exists()) {
                appDownloadFile
            } else {
                val uriString = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                if (uriString != null) {
                    val localUri = Uri.parse(uriString)
                    if (localUri.scheme == "content") {
                        val inputStream = context.contentResolver.openInputStream(localUri) ?: return
                        val updateFile = File(context.cacheDir, "update.apk")
                        updateFile.outputStream().use { output -> inputStream.copyTo(output) }
                        updateFile
                    } else {
                        File(localUri.path ?: return)
                    }
                } else {
                    return
                }
            }

            InstallerUtils.installApk(context, targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle download completion", e)
        } finally {
            cursor.close()
        }
    }

    companion object {
        private const val TAG = "DownloadCompleteRecv"
    }
}
