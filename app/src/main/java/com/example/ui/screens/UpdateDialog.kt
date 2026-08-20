package com.example.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.update.InstallerUtils
import com.example.update.UpdateInfo
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isDownloading by remember { mutableStateOf(false) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var progressPercent by remember { mutableStateOf(0) }
    var isIndeterminate by remember { mutableStateOf(true) }

    // Poll download progress when active
    LaunchedEffect(downloadId) {
        val currentId = downloadId ?: return@LaunchedEffect
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(currentId)

        while (true) {
            val cursor = manager.query(query)
            if (cursor != null && cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                if (totalBytes > 0) {
                    isIndeterminate = false
                    progressPercent = ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                } else {
                    isIndeterminate = true
                }

                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    cursor.close()
                    val targetFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "AirMouse.apk")
                    InstallerUtils.installApk(context, targetFile)
                    onDismiss()
                    break
                } else if (status == DownloadManager.STATUS_FAILED) {
                    cursor.close()
                    Toast.makeText(context, "Download failed. Please try again.", Toast.LENGTH_SHORT).show()
                    isDownloading = false
                    downloadId = null
                    break
                }
                cursor.close()
            }
            delay(300)
        }
    }

    Dialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !isDownloading, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Update Icon
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = "Update Available",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Title
                Text(
                    text = "Update Available",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Version info
                Text(
                    text = "v${updateInfo.latestVersion}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Changelog
                if (updateInfo.changelog.isNotBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "What's New:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Parse and display changelog
                            val parsedLines = parseChangelog(updateInfo.changelog)
                            parsedLines.forEach { line ->
                                when {
                                    line.startsWith("VERSION:") -> {
                                        Text(
                                            text = line.removePrefix("VERSION:"),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                                        )
                                    }
                                    line.startsWith("SECTION:") -> {
                                        Text(
                                            text = line.removePrefix("SECTION:"),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                        )
                                    }
                                    line.startsWith("ITEM:") -> {
                                        Text(
                                            text = "• ${line.removePrefix("ITEM:")}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                        )
                                    }
                                    line.startsWith("SUBITEM:") -> {
                                        Text(
                                            text = "  ○ ${line.removePrefix("SUBITEM:")}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 20.dp, top = 2.dp)
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = line,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons - Stack vertically on small screens
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isDownloading) {
                        // Downloading state
                        if (isIndeterminate) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isIndeterminate) "Downloading update..." else "Downloading update... $progressPercent%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Download button
                        Button(
                            onClick = {
                                val apkUrl = updateInfo.apkDownloadUrl
                                if (apkUrl != null) {
                                    isDownloading = true
                                    downloadId = downloadApk(context, apkUrl)
                                } else {
                                    // No APK asset attached to the release - open the page
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.downloadUrl))
                                    context.startActivity(intent)
                                    onDismiss()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Download & Install", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        // Later button
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                        ) {
                            Text("Later", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Queue an APK download via the system DownloadManager.
 * Returns the queued download ID, or null if queuing failed.
 */
private fun downloadApk(context: Context, url: String): Long? {
    return try {
        val targetFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "AirMouse.apk")
        if (targetFile.exists()) {
            targetFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("AirMouse Update")
            setDescription("Downloading the latest AirMouse APK")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setMimeType("application/vnd.android.package-archive")
            setDestinationUri(Uri.fromFile(targetFile))
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = manager.enqueue(request)
        Toast.makeText(context, "Downloading AirMouse update...", Toast.LENGTH_SHORT).show()
        id
    } catch (e: Exception) {
        // Fall back to opening the release URL in a browser if DownloadManager fails
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
        null
    }
}

/**
 * Strip markdown bold markers from text.
 * e.g. "**Settings cleanup** — description" → "Settings cleanup — description"
 */
private fun stripBold(text: String): String {
    return text.replace("**", "")
}

/**
 * Parse markdown changelog into clean format lines.
 * Returns list of strings with prefixes:
 *   - VERSION:  version header (## [x.y.z])
 *   - SECTION:  section sub-header (### Added, ### Changed, etc.)
 *   - ITEM:     bullet point
 *   - SUBITEM:  indented bullet
 *   - plain text
 */
fun parseChangelog(changelog: String): List<String> {
    val result = mutableListOf<String>()
    val lines = changelog.split("\n")

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue

        when {
            // Indented sub-bullets (check raw line before trimming)
            line.startsWith("  - ") || line.startsWith("  * ") -> {
                val text = line.trim().removePrefix("- ").removePrefix("* ").trim()
                result.add("SUBITEM:${stripBold(text)}")
            }
            // Section sub-header (### Added, ### Changed, ### Fixed)
            trimmed.startsWith("### ") -> {
                result.add("SECTION:${stripBold(trimmed.removePrefix("### ").trim())}")
            }
            // Version header (## [x.y.z])
            trimmed.startsWith("## ") -> {
                val text = trimmed.removePrefix("## ").trim()
                result.add("VERSION:${stripBold(text)}")
            }
            // Bullet point
            trimmed.startsWith("- ") -> {
                result.add("ITEM:${stripBold(trimmed.removePrefix("- ").trim())}")
            }
            trimmed.startsWith("* ") -> {
                result.add("ITEM:${stripBold(trimmed.removePrefix("* ").trim())}")
            }
            // Plain text
            else -> {
                result.add(stripBold(trimmed))
            }
        }
    }

    return result
}
