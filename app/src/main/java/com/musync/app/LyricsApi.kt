package com.musync.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class SongLyrics(
    val songId: String,
    val lines: List<LyricLine>,
    val plainLyrics: String?,
    val isInstrumental: Boolean = false,
    val isSynced: Boolean = lines.isNotEmpty()
)

object LyricsApi {

    private const val BASE_URL = "https://lrclib.net/api"
    private val memoryCache = HashMap<String, SongLyrics?>()

    suspend fun getLyrics(context: Context, song: Song): SongLyrics? = withContext(Dispatchers.IO) {
        val cacheKey = song.id
        if (memoryCache.containsKey(cacheKey)) {
            return@withContext memoryCache[cacheKey]
        }

        // Check local disk cache
        val diskLyrics = readDiskCache(context, cacheKey)
        if (diskLyrics != null) {
            memoryCache[cacheKey] = diskLyrics
            return@withContext diskLyrics
        }

        // Fetch from LRCLIB
        val cleanTitle = cleanTitle(song.title)
        val cleanArtist = cleanArtist(song.artist)
        val durationSec = (song.durationMs / 1000L).coerceAtLeast(0L)

        var result = fetchExact(cleanTitle, cleanArtist, durationSec, song.id)
        if (result == null) {
            result = fetchBySearch("$cleanArtist $cleanTitle", song.id, durationSec)
        }
        if (result == null && cleanTitle != song.title) {
            result = fetchBySearch(cleanTitle, song.id, durationSec)
        }

        memoryCache[cacheKey] = result
        if (result != null) {
            saveDiskCache(context, result)
        }
        result
    }

    private fun fetchExact(title: String, artist: String, durationSec: Long, songId: String): SongLyrics? {
        val qTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.name())
        val qArtist = URLEncoder.encode(artist, StandardCharsets.UTF_8.name())
        val url = "$BASE_URL/get?track_name=$qTitle&artist_name=$qArtist&duration=$durationSec"

        return executeRequest(url, songId)
    }

    private fun fetchBySearch(query: String, songId: String, durationSec: Long): SongLyrics? {
        val q = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val url = "$BASE_URL/search?q=$q"

        val jsonStr = executeRaw(url) ?: return null
        val array = runCatching { JSONArray(jsonStr) }.getOrNull() ?: return null
        if (array.length() == 0) return null

        var bestObj: JSONObject? = null
        var bestDiff = Long.MAX_VALUE

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val hasSynced = item.optString("syncedLyrics").isNotBlank()
            val itemDur = item.optLong("duration", 0L)
            val diff = if (durationSec > 0 && itemDur > 0) kotlin.math.abs(durationSec - itemDur) else 0L

            if (bestObj == null) {
                bestObj = item
                bestDiff = diff
            } else if (hasSynced && !bestObj.optString("syncedLyrics").isNotBlank()) {
                bestObj = item
                bestDiff = diff
            } else if (hasSynced && diff < bestDiff) {
                bestObj = item
                bestDiff = diff
            }
        }

        val target = bestObj ?: return null
        return parseLyricsJson(target, songId)
    }

    private fun executeRequest(url: String, songId: String): SongLyrics? {
        val jsonStr = executeRaw(url) ?: return null
        val obj = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return null
        return parseLyricsJson(obj, songId)
    }

    private fun executeRaw(url: String): String? = runCatching {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Musync-Android/1.4.1")
            .build()
        NewPipeDownloader.instance.client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
    }.getOrNull()

    private fun parseLyricsJson(json: JSONObject, songId: String): SongLyrics {
        val instrumental = json.optBoolean("instrumental", false)
        val rawSynced = json.optString("syncedLyrics").trim()
        val rawPlain = json.optString("plainLyrics").trim().ifBlank { null }
        val lines = if (rawSynced.isNotBlank()) parseLrc(rawSynced) else emptyList()

        return SongLyrics(
            songId = songId,
            lines = lines,
            plainLyrics = rawPlain,
            isInstrumental = instrumental
        )
    }

    fun parseLrc(lrcText: String): List<LyricLine> {
        val pattern = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]""")
        val result = mutableListOf<LyricLine>()

        for (rawLine in lrcText.lineSequence()) {
            val matches = pattern.findAll(rawLine).toList()
            if (matches.isEmpty()) continue
            val text = rawLine.substring(matches.last().range.last + 1).trim()
            if (text.isEmpty()) continue

            for (match in matches) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val sub = match.groupValues[3]
                val ms = when (sub.length) {
                    1 -> (sub.toLongOrNull() ?: 0L) * 100L
                    2 -> (sub.toLongOrNull() ?: 0L) * 10L
                    3 -> sub.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val totalMs = min * 60_000L + sec * 1_000L + ms
                result.add(LyricLine(totalMs, text))
            }
        }
        return result.sortedBy { it.timeMs }
    }

    private fun cleanTitle(raw: String): String {
        var s = raw
        // If "Artist - Title", extract title
        if (s.contains(" - ")) {
            val parts = s.split(" - ", limit = 2)
            if (parts.size == 2 && parts[1].isNotBlank()) {
                s = parts[1]
            }
        }
        // Remove common YouTube tags & brackets
        s = s.replace(Regex("""(?i)\s*[\(\[](official\s*(music\s*)?video|official\s*audio|lyrics|audio|visualizer|remastered.*?)[\)\]]"""), "")
        s = s.replace(Regex("""(?i)\s*(feat\.|ft\.).*"""), "")
        return s.trim()
    }

    private fun cleanArtist(raw: String): String {
        return raw.replace(Regex("""(?i)\s*-\s*topic$"""), "")
            .replace(Regex("""(?i)\s*vevo$"""), "")
            .trim()
    }

    private fun getDiskFile(context: Context, songId: String): File {
        val dir = File(context.filesDir, "lyrics").apply { mkdirs() }
        val safeName = songId.replace(Regex("""[^a-zA-Z0-9_\-]"""), "_")
        return File(dir, "$safeName.json")
    }

    private fun readDiskCache(context: Context, songId: String): SongLyrics? = runCatching {
        val file = getDiskFile(context, songId)
        if (!file.exists()) return null
        val str = file.readText()
        val json = JSONObject(str)
        val instrumental = json.optBoolean("instrumental", false)
        val plain = json.optString("plainLyrics").ifBlank { null }
        val linesArray = json.optJSONArray("lines")
        val lines = mutableListOf<LyricLine>()
        if (linesArray != null) {
            for (i in 0 until linesArray.length()) {
                val item = linesArray.getJSONObject(i)
                lines.add(LyricLine(item.getLong("t"), item.getString("l")))
            }
        }
        SongLyrics(songId, lines, plain, instrumental)
    }.getOrNull()

    private fun saveDiskCache(context: Context, lyrics: SongLyrics) = runCatching {
        val file = getDiskFile(context, lyrics.songId)
        val json = JSONObject().apply {
            put("instrumental", lyrics.isInstrumental)
            put("plainLyrics", lyrics.plainLyrics ?: "")
            val linesArr = JSONArray()
            lyrics.lines.forEach { line ->
                linesArr.put(JSONObject().apply {
                    put("t", line.timeMs)
                    put("l", line.text)
                })
            }
            put("lines", linesArr)
        }
        file.writeText(json.toString())
    }
}
