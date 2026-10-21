package com.zevbuild.zevblock.core.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val publishedAt: String = "",
    val assetSize: Long = 0L
)

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class UpdateAvailable(val release: AppReleaseInfo) : UpdateStatus()
    object UpToDate : UpdateStatus()
    data class Downloading(val progressPercent: Int) : UpdateStatus()
    data class ReadyToInstall(val apkFile: File) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

/**
 * AppUpdateManager:
 * Automatically queries GitHub Releases API for newer ZevBlock versions,
 * manages APK downloads via DownloadManager, and triggers native PackageInstaller
 * with FileProvider URI permissions.
 */
object AppUpdateManager {
    private const val GITHUB_REPO = "zevbuild/zevblock"
    private const val GITHUB_LATEST_RELEASE_API = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    private var activeDownloadId: Long = -1L

    /**
     * Checks GitHub Releases API for the latest published release.
     */
    suspend fun checkForUpdate(context: Context): UpdateStatus = withContext(Dispatchers.IO) {
        _updateStatus.value = UpdateStatus.Checking
        try {
            val url = URL(GITHUB_LATEST_RELEASE_API)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "ZevBlock-Android-App")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            if (connection.responseCode != 200) {
                val error = "GitHub API responded with code ${connection.responseCode}"
                _updateStatus.value = UpdateStatus.Error(error)
                return@withContext UpdateStatus.Error(error)
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseBody)

            val tagName = json.optString("tag_name", "")
            val title = json.optString("name", tagName)
            val body = json.optString("body", "Bug fixes and security improvements.")
            val publishedAt = json.optString("published_at", "")

            // Find APK in assets
            val assets = json.optJSONArray("assets")
            var downloadUrl = ""
            var apkSize = 0L

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            // Fallback to direct releases download URL if assets list is somehow empty
            if (downloadUrl.isBlank() && tagName.isNotBlank()) {
                downloadUrl = "https://github.com/$GITHUB_REPO/releases/download/$tagName/ZevBlock-latest.apk"
            }

            val currentVersionName = getInstalledVersionName(context)
            val hasUpdate = isNewerVersion(currentVersionName, tagName)

            val status = if (hasUpdate && downloadUrl.isNotBlank()) {
                val release = AppReleaseInfo(
                    tagName = tagName,
                    versionName = cleanVersion(tagName),
                    releaseTitle = title,
                    releaseNotes = body,
                    downloadUrl = downloadUrl,
                    publishedAt = publishedAt,
                    assetSize = apkSize
                )
                UpdateStatus.UpdateAvailable(release)
            } else {
                UpdateStatus.UpToDate
            }

            _updateStatus.value = status
            status
        } catch (e: Exception) {
            val error = e.localizedMessage ?: "Network error checking update"
            _updateStatus.value = UpdateStatus.Error(error)
            UpdateStatus.Error(error)
        }
    }

    /**
     * Determines whether the remote release version is newer than installed version.
     */
    fun isNewerVersion(installed: String, remote: String): Boolean {
        val cleanInstalled = cleanVersion(installed)
        val cleanRemote = cleanVersion(remote)

        val installedParts = cleanInstalled.split(".").mapNotNull { it.toIntOrNull() }
        val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(installedParts.size, remoteParts.size)
        for (i in 0 until maxLen) {
            val inst = installedParts.getOrElse(i) { 0 }
            val rem = remoteParts.getOrElse(i) { 0 }
            if (rem > inst) return true
            if (rem < inst) return false
        }
        return false
    }

    fun cleanVersion(raw: String): String {
        return raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .split("-")[0] // e.g. "1.2-stable" -> "1.2"
    }

    fun getInstalledVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    /**
     * Validates that download URLs originate exclusively from official GitHub infrastructure over HTTPS.
     */
    fun isValidDownloadUrl(url: String): Boolean {
        return try {
            val parsed = Uri.parse(url)
            val host = parsed.host?.lowercase() ?: return false
            val scheme = parsed.scheme?.lowercase() ?: return false
            scheme == "https" && (
                host == "github.com" ||
                host == "api.github.com" ||
                host == "objects.githubusercontent.com" ||
                host.endsWith(".github.com") ||
                host.endsWith(".githubusercontent.com")
            )
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Downloads the APK file using Android's DownloadManager.
     */
    fun downloadApk(context: Context, release: AppReleaseInfo) {
        if (!isValidDownloadUrl(release.downloadUrl)) {
            _updateStatus.value = UpdateStatus.Error("Untrusted update download URL rejected for security.")
            return
        }

        val appContext = context.applicationContext
        val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return

        val safeTag = release.tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fileName = "ZevBlock-$safeTag.apk"
        val destination = File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (destination.exists()) {
            destination.delete()
        }

        val request = DownloadManager.Request(Uri.parse(release.downloadUrl)).apply {
            setTitle("Downloading ZevBlock ${release.tagName}")
            setDescription("Fetching update from GitHub Releases...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(destination))
            setMimeType("application/vnd.android.package-archive")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        _updateStatus.value = UpdateStatus.Downloading(0)
        activeDownloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(recvContext: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == activeDownloadId) {
                    _updateStatus.value = UpdateStatus.ReadyToInstall(destination)
                    installApk(appContext, destination)
                    try {
                        appContext.unregisterReceiver(this)
                    } catch (_: Exception) {}
                }
            }
        }

        androidx.core.content.ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
    }

    /**
     * Prompts the Android Package Installer to install the downloaded APK.
     */
    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            _updateStatus.value = UpdateStatus.Error("APK file not found")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val grantIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(grantIntent)
                return
            }
        }

        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            _updateStatus.value = UpdateStatus.Error("Failed to launch installer: ${e.message}")
        }
    }

    fun dismissUpdate() {
        _updateStatus.value = UpdateStatus.Idle
    }
}
