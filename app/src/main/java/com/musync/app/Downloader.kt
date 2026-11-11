package com.musync.app

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

enum class DlState { Downloading, Done, Failed }

/**
 * Downloads audio tracks + cover art to Music/Musync, tracks progress for [DownloadService],
 * and persists metadata in [MusyncStore] so downloaded tracks show rich tags & artwork in Library.
 */
class Downloader(
    private val app: Context,
    private val store: MusyncStore,
    private val onFinished: () -> Unit,
    private val onMessage: (String) -> Unit
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeNames = ConcurrentHashMap<String, String>()

    private val _states = MutableStateFlow(
        store.downloads().keys.associateWith { DlState.Done }
    )
    val states: StateFlow<Map<String, DlState>> = _states

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress

    fun activeTitles(): List<String> = activeNames.values.toList()

    /** Keeps download state in sync if the user deletes a file from device storage. */
    fun reconcile(existingFileNames: Set<String>) {
        val saved = store.downloads()
        saved.values.forEach { meta ->
            if (meta.fileName !in existingFileNames && _states.value[meta.videoId] != DlState.Downloading) {
                store.removeDownload(meta.videoId)
                _states.update { it - meta.videoId }
            }
        }
    }

    fun download(track: RemoteTrack) {
        val trackId = track.id.removePrefix("yt_")
        when (_states.value[trackId]) {
            DlState.Downloading -> return
            DlState.Done -> {
                onMessage("\"${track.title}\" is already in your library")
                return
            }
            else -> {}
        }

        activeNames[trackId] = "${track.title} · ${track.artist}"
        set(trackId, DlState.Downloading)
        setProgress(trackId, 0f)
        DownloadService.start(app)

        scope.launch {
            try {
                val resolved = YouTubeApi.resolveAudio(trackId, store.serverUrl.value.trim())
                val baseName = sanitize("${track.artist} - ${track.title}")
                val displayFileName = "$baseName.${resolved.ext}"

                val artPath = downloadCoverArt(trackId, track.imageUrl)
                val ok = downloadAudio(trackId, resolved, displayFileName, track)

                if (ok) {
                    store.setDownload(
                        MusyncStore.DownloadMeta(
                            videoId = trackId,
                            fileName = displayFileName,
                            title = track.title,
                            artist = track.artist,
                            album = track.album.ifBlank { "Musync" },
                            artPath = artPath
                        )
                    )
                    activeNames.remove(trackId)
                    clearProgress(trackId)
                    set(trackId, DlState.Done)
                    onMessage("Downloaded \"${track.title}\"")
                    withContext(Dispatchers.Main) { onFinished() }
                } else {
                    fail(trackId, track.title)
                }
            } catch (_: Exception) {
                fail(trackId, track.title)
            }
        }
    }

    private fun fail(trackId: String, title: String) {
        activeNames.remove(trackId)
        clearProgress(trackId)
        set(trackId, DlState.Failed)
        onMessage("Couldn't download \"$title\"")
    }

    private fun downloadCoverArt(videoId: String, imageUrl: String?): String? {
        if (imageUrl.isNullOrBlank()) return null
        return runCatching {
            val dir = File(app.filesDir, "art").apply { mkdirs() }
            val file = File(dir, "$videoId.jpg")
            val req = Request.Builder()
                .url(imageUrl)
                .header("User-Agent", NewPipeDownloader.USER_AGENT)
                .build()
            NewPipeDownloader.instance.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body ?: return null
                FileOutputStream(file).use { out -> body.byteStream().copyTo(out) }
            }
            file.takeIf { it.length() > 0 }?.absolutePath
        }.getOrNull()
    }

    private suspend fun downloadAudio(
        trackId: String,
        resolved: YouTubeApi.ResolvedAudio,
        displayFileName: String,
        track: RemoteTrack
    ): Boolean = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(resolved.url)
            .header("User-Agent", NewPipeDownloader.USER_AGENT)
            .header("Accept", "*/*")
            .build()

        NewPipeDownloader.instance.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext false
            val body = resp.body ?: return@withContext false
            val total = body.contentLength()
            val input = body.byteStream()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, displayFileName)
                    put(MediaStore.Audio.Media.TITLE, track.title)
                    put(MediaStore.Audio.Media.ARTIST, track.artist)
                    put(MediaStore.Audio.Media.ALBUM, track.album.ifBlank { "Musync" })
                    put(MediaStore.Audio.Media.MIME_TYPE, resolved.mimeType)
                    put(MediaStore.Audio.Media.DURATION, track.durationSec * 1000L)
                    put(MediaStore.Audio.Media.IS_MUSIC, 1)
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Musync")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }

                val uri = app.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext false

                var writeOk = false
                try {
                    app.contentResolver.openOutputStream(uri)?.use { out ->
                        copyWithProgress(trackId, input, out, total)
                        writeOk = true
                    }
                } finally {
                    if (writeOk) {
                        values.clear()
                        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                        app.contentResolver.update(uri, values, null, null)
                    } else {
                        runCatching { app.contentResolver.delete(uri, null, null) }
                    }
                }
                writeOk
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "Musync"
                ).apply { mkdirs() }
                val targetFile = File(dir, displayFileName)
                FileOutputStream(targetFile).use { out ->
                    copyWithProgress(trackId, input, out, total)
                }
                MediaScannerConnection.scanFile(
                    app,
                    arrayOf(targetFile.absolutePath),
                    arrayOf(resolved.mimeType),
                    null
                )
                true
            }
        }
    }

    private fun copyWithProgress(trackId: String, input: InputStream, out: OutputStream, total: Long) {
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
                if (now - lastEmit >= 80) {
                    lastEmit = now
                    setProgress(trackId, (copied.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        out.flush()
    }

    private fun set(trackId: String, state: DlState) {
        _states.update { it + (trackId to state) }
    }

    private fun setProgress(trackId: String, value: Float) {
        _progress.update { it + (trackId to value) }
    }

    private fun clearProgress(trackId: String) {
        _progress.update { it - trackId }
    }

    private fun sanitize(name: String) =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(120)
}
