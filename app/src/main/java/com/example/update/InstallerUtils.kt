package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object InstallerUtils {
    private const val TAG = "InstallerUtils"

    /**
     * Launch the system package installer for the specified APK [file].
     * Prompts for 'Install unknown apps' permission on Android 8.0+ if needed.
     */
    fun installApk(context: Context, file: File) {
        if (!file.exists()) {
            Log.e(TAG, "APK file does not exist: ${file.absolutePath}")
            Toast.makeText(context, "Downloaded update file missing", Toast.LENGTH_SHORT).show()
            return
        }

        // Android 8.0 (API 26+) requires explicit permission to install unknown apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(
                    context,
                    "Please allow AirMouse to install updates",
                    Toast.LENGTH_LONG
                ).show()
                try {
                    val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(manageIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open unknown app sources settings", e)
                }
                return
            }
        }

        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            Log.d(TAG, "Launched package installer for ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
            Toast.makeText(context, "Failed to launch update installer", Toast.LENGTH_SHORT).show()
        }
    }
}
