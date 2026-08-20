package com.example.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
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

        Log.d(TAG, "Download complete: id=$downloadId")

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

            // Get the local file URI
            val uriString = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            val localUri = Uri.parse(uriString)

            // Convert content:// URI to file:// URI if needed
            val fileUri = if (localUri.scheme == "content") {
                // Content URI — resolve to a File via the FileProvider or direct path
                val inputStream = context.contentResolver.openInputStream(localUri) ?: return
                val updateFile = File(context.cacheDir, "update.apk")
                updateFile.outputStream().use { output -> inputStream.copyTo(output) }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", updateFile)
            } else {
                // file:// URI — use directly
                File(localUri.path!!).let { file ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    } else {
                        localUri
                    }
                }
            }

            launchInstaller(context, fileUri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch installer", e)
        } finally {
            cursor.close()
        }
    }

    private fun launchInstaller(context: Context, apkUri: Uri) {
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "No installer available", e)
        }
    }

    companion object {
        private const val TAG = "DownloadCompleteRecv"
    }
}
