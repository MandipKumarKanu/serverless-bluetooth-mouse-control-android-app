package com.example.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val changelog: String,
    val isUpdateAvailable: Boolean
)

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val GITHUB_API_URL = "https://api.github.com/repos/MandipKumarKanu/serverless-bluetooth-mouse-control-android-app/releases/latest"

    suspend fun checkForUpdate(currentVersion: String): UpdateInfo {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(GITHUB_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)

                    val tagName = json.getString("tag_name").removePrefix("v")
                    val htmlUrl = json.getString("html_url")
                    val body = json.getString("body") ?: ""

                    val isUpdateAvailable = compareVersions(tagName, currentVersion) > 0

                    Log.d(TAG, "Current: $currentVersion, Latest: $tagName, Update: $isUpdateAvailable")

                    UpdateInfo(
                        latestVersion = tagName,
                        downloadUrl = htmlUrl,
                        changelog = body,
                        isUpdateAvailable = isUpdateAvailable
                    )
                } else {
                    Log.e(TAG, "GitHub API error: ${connection.responseCode}")
                    UpdateInfo("", "", "", false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for update", e)
                UpdateInfo("", "", "", false)
            }
        }
    }

    private fun compareVersions(latest: String, current: String): Int {
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(latestParts.size, currentParts.size)

        for (i in 0 until maxLength) {
            val latestPart = latestParts.getOrElse(i) { 0 }
            val currentPart = currentParts.getOrElse(i) { 0 }

            if (latestPart > currentPart) return 1
            if (latestPart < currentPart) return -1
        }

        return 0
    }
}
