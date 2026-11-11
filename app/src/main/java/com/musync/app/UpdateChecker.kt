package com.musync.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class UpdateInfo(
    val version: String,
    val title: String,
    val notes: String,
    val apkUrl: String?,
    val releasePageUrl: String
)

/**
 * Checks GitHub Releases (singkit-git/musync-apk) for a newer version of Musync
 * and downloads + launches the APK installer directly on the device.
 */
object UpdateChecker {

    const val GITHUB_REPO = "singkit-git/musync-apk"
    private const val LATEST_API = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    private const val TAGS_API = "https://api.github.com/repos/$GITHUB_REPO/tags"

    fun currentVersion(context: Context): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.1.0"
        }.getOrDefault("1.1.0")

    private fun defaultRawApkUrl(version: String): String =
        "https://raw.githubusercontent.com/$GITHUB_REPO/main/releases/Musync-v$version.apk"

    /** Returns [UpdateInfo] if a newer release or tag is published on GitHub, or null if up to date. */
    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val current = currentVersion(context)
        val cleanCur = cleanVersion(current)

        // 1. Check formal GitHub Releases (/releases/latest)
        val releaseCandidate = runCatching {
            val req = Request.Builder()
                .url(LATEST_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Musync-Android/$current")
                .build()

            NewPipeDownloader.instance.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val json = JSONObject(body)
                if (json.optBoolean("draft", false)) return@use null

                val rawTag = json.optString("tag_name").ifBlank { json.optString("name") }
                val remoteVer = cleanVersion(rawTag)
                if (remoteVer.isBlank() || !isNewer(remoteVer, cleanCur)) return@use null

                var apkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.optJSONObject(i) ?: continue
                        val name = a.optString("name")
                        val url = a.optString("browser_download_url")
                        if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                            apkUrl = url
                            break
                        }
                    }
                }

                UpdateInfo(
                    version = remoteVer,
                    title = json.optString("name").ifBlank { "Musync v$remoteVer" },
                    notes = json.optString("body").trim(),
                    apkUrl = apkUrl ?: defaultRawApkUrl(remoteVer),
                    releasePageUrl = json.optString("html_url")
                        .ifBlank { "https://github.com/$GITHUB_REPO/releases/latest" }
                )
            }
        }.getOrNull()

        if (releaseCandidate != null) return@withContext releaseCandidate

        // 2. Fallback: check repository Git tags (/tags) + releases/Musync-vX.Y.Z.apk
        runCatching {
            val req = Request.Builder()
                .url(TAGS_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Musync-Android/$current")
                .build()

            NewPipeDownloader.instance.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val arr = JSONArray(body)
                var bestVer: String? = null
                for (i in 0 until arr.length()) {
                    val tagObj = arr.optJSONObject(i) ?: continue
                    val ver = cleanVersion(tagObj.optString("name"))
                    if (ver.isNotBlank() && isNewer(ver, bestVer ?: cleanCur)) {
                        bestVer = ver
                    }
                }
                val targetVer = bestVer ?: return@use null
                UpdateInfo(
                    version = targetVer,
                    title = "Musync v$targetVer",
                    notes = "A new update (v$targetVer) is ready to install.",
                    apkUrl = defaultRawApkUrl(targetVer),
                    releasePageUrl = "https://github.com/$GITHUB_REPO/releases/tag/v$targetVer"
                )
            }
        }.getOrNull()
    }

    /** Downloads the APK and opens the system package installer, or opens the release page if no APK asset exists. */
    suspend fun downloadAndInstall(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val apkUrl = info.apkUrl
        if (apkUrl.isNullOrBlank()) {
            withContext(Dispatchers.Main) {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(info.releasePageUrl))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return@withContext true
        }

        val req = Request.Builder()
            .url(apkUrl)
            .header("User-Agent", NewPipeDownloader.USER_AGENT)
            .build()

        val dir = File(context.cacheDir, "updates").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val apkFile = File(dir, "Musync-${info.version}.apk")

        NewPipeDownloader.instance.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext false
            val body = resp.body ?: return@withContext false
            val total = body.contentLength()
            val input = body.byteStream()

            FileOutputStream(apkFile).use { out ->
                val buf = ByteArray(32 * 1024)
                var copied = 0L
                var lastEmit = 0L
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    out.write(buf, 0, read)
                    copied += read
                    if (total > 0) {
                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 60) {
                            lastEmit = now
                            onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                out.flush()
            }
        }

        if (!apkFile.exists() || apkFile.length() == 0L) return@withContext false

        withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        }
        true
    }

    private fun cleanVersion(v: String): String =
        v.trim().removePrefix("v").removePrefix("V").trim()

    private fun isNewer(remote: String, current: String): Boolean {
        val rParts = remote.split(".", "-").mapNotNull { it.toIntOrNull() }
        val cParts = current.split(".", "-").mapNotNull { it.toIntOrNull() }
        val max = maxOf(rParts.size, cParts.size)
        for (i in 0 until max) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }
}
