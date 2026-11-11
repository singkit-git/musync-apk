package com.musync.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/**
 * YouTube search + audio stream resolving.
 *
 * Primary engine is NewPipeExtractor (runs fully on-device, like yt-dlp). If it fails we fall back
 * to the optional user-configured yt-dlp backend, YouTube's InnerTube search, then Invidious.
 */
object YouTubeApi {

    data class SearchResult(
        val tracks: List<RemoteTrack>,
        /** Loads the next page, or null when there are no more results. */
        val loadMore: (suspend () -> SearchResult)?
    )

    data class ResolvedAudio(val url: String, val mimeType: String, val ext: String)

    private const val UA = NewPipeDownloader.USER_AGENT
    private val INVIDIOUS = listOf(
        "https://yt.chocolatemoo53.com",
        "https://inv.nadeko.net",
        "https://invidious.nerdvpn.de"
    )

    private val service get() = ServiceList.YouTube
    private val cache = ConcurrentHashMap<String, Pair<ResolvedAudio, Long>>()

    // ------------------------------------------------------------------ search

    /** Blank [query] loads popular music. */
    suspend fun search(query: String, serverUrl: String = ""): SearchResult = withContext(Dispatchers.IO) {
        val q = query.trim().ifEmpty { "top hits ${Calendar.getInstance().get(Calendar.YEAR)}" }
        var lastError: Throwable? = null

        if (serverUrl.isNotBlank()) {
            runCatching { searchBackend(serverUrl, q) }
                .onSuccess { if (it.isNotEmpty()) return@withContext SearchResult(it, null) }
                .onFailure { lastError = it }
        }

        // YouTube Music "songs" gives clean results (real songs, square art, artist names).
        runCatching { newPipeSearch(q, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS) }
            .onSuccess { if (it.tracks.isNotEmpty()) return@withContext it }
            .onFailure { lastError = it }

        // Regular YouTube video search, filtered down to song-like videos.
        runCatching { newPipeSearch(q, YoutubeSearchQueryHandlerFactory.VIDEOS) }
            .onSuccess { if (it.tracks.isNotEmpty()) return@withContext it }
            .onFailure { lastError = it }

        runCatching { searchInnerTube(q) }
            .onSuccess { if (it.isNotEmpty()) return@withContext SearchResult(it, null) }
            .onFailure { lastError = it }

        for (inst in INVIDIOUS) {
            runCatching { searchInvidious(inst, q) }
                .onSuccess { if (it.isNotEmpty()) return@withContext SearchResult(it, null) }
                .onFailure { lastError = it }
        }

        if (lastError != null) throw IOException("Couldn't reach YouTube. Check your internet connection.", lastError)
        SearchResult(emptyList(), null)
    }

    private fun newPipeSearch(query: String, filter: String): SearchResult {
        val handler = service.searchQHFactory.fromQuery(query, listOf(filter), "")
        val info = SearchInfo.getInfo(service, handler)
        val music = filter == YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
        return SearchResult(mapItems(info.relatedItems, music), nextLoader(handler, info.nextPage, music))
    }

    private fun nextLoader(handler: SearchQueryHandler, page: Page?, music: Boolean): (suspend () -> SearchResult)? {
        if (page == null || (page.url.isNullOrEmpty() && page.id.isNullOrEmpty() && page.body == null)) return null
        return {
            withContext(Dispatchers.IO) {
                val more = SearchInfo.getMoreItems(service, handler, page)
                SearchResult(mapItems(more.items, music), if (more.hasNextPage()) nextLoader(handler, more.nextPage, music) else null)
            }
        }
    }

    private fun mapItems(items: List<InfoItem>, music: Boolean): List<RemoteTrack> =
        items.filterIsInstance<StreamInfoItem>().mapNotNull { item ->
            if (item.streamType != StreamType.VIDEO_STREAM && item.streamType != StreamType.AUDIO_STREAM) return@mapNotNull null
            val id = runCatching { service.streamLHFactory.getId(item.url) }.getOrNull() ?: return@mapNotNull null
            val dur = item.duration.toInt()
            if (item.isShortFormContent) return@mapNotNull null
            if (!music && (dur < 45 || dur > 15 * 60)) return@mapNotNull null

            var title = item.name ?: return@mapNotNull null
            var artist = item.uploaderName.orEmpty().removeSuffix(" - Topic").ifBlank { "Unknown artist" }
            val image: String
            if (music) {
                image = item.thumbnails.maxByOrNull { it.width * it.height }?.url?.let(::upscaleMusicArt)
                    ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
            } else {
                // "Artist - Title (Official Video)" -> artist / clean title
                title = cleanTitle(title)
                val dash = title.indexOf(" - ")
                if (dash > 0) {
                    artist = title.substring(0, dash).trim()
                    title = title.substring(dash + 3).trim()
                }
                image = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
            }
            RemoteTrack(id, title, artist, if (music) "YouTube Music" else "YouTube", image, dur)
        }.distinctBy { it.id }

    private val noise = Regex(
        """\s*[(\[](official\s*(music\s*)?(video|audio|lyric\s*video|visualizer)|lyrics?(\s*video)?|audio|hd|hq|4k|mv|m/v|visualizer)[)\]]""",
        RegexOption.IGNORE_CASE
    )

    private fun cleanTitle(t: String) = t.replace(noise, "").replace("–", "-").trim()

    /** YouTube Music art URLs end in =w60-h60...; ask for a large square instead. */
    private fun upscaleMusicArt(url: String) =
        url.replace(Regex("=w\\d+-h\\d+.*$"), "=w544-h544-l90-rj")

    // ------------------------------------------------------------------ audio streams

    suspend fun resolveAudio(videoId: String, serverUrl: String = ""): ResolvedAudio =
        withContext(Dispatchers.IO) { resolveAudioBlocking(videoId, serverUrl) }

    /** Blocking; safe to call from ExoPlayer's loader thread. Results are cached until they expire. */
    fun resolveAudioBlocking(videoId: String, serverUrl: String = ""): ResolvedAudio {
        val id = videoId.removePrefix("yt_")
        cache[id]?.let { (audio, expiry) ->
            if (expiry > System.currentTimeMillis() + 60_000) return audio
        }

        var lastError: Throwable? = null
        val attempts = buildList<() -> ResolvedAudio?> {
            if (serverUrl.isNotBlank()) add { resolveBackend(serverUrl, id) }
            add { resolveNewPipe(id) }
            INVIDIOUS.forEach { inst -> add { resolveInvidious(inst, id) } }
        }
        for (attempt in attempts) {
            val result = runCatching { attempt() }.onFailure { lastError = it }.getOrNull()
            if (result != null) {
                cache[id] = result to expiryOf(result.url)
                return result
            }
        }
        throw IOException("Couldn't load audio for this song", lastError)
    }

    private fun resolveNewPipe(id: String): ResolvedAudio? {
        val info = StreamInfo.getInfo(service, "https://www.youtube.com/watch?v=$id")
        val all = info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        // Skip dubbed / descriptive tracks when the original language is available.
        val original = all.filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
            .ifEmpty { all }
        val best: AudioStream = original.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
            ?: original.maxByOrNull { it.averageBitrate }
            ?: return null
        val fmt = best.format ?: MediaFormat.M4A
        return ResolvedAudio(best.content, fmt.mimeType, fmt.suffix)
    }

    private fun expiryOf(url: String): Long {
        val sec = Regex("[?&]expire=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()
        return if (sec != null) sec * 1000 else System.currentTimeMillis() + 3 * 3600_000L
    }

    // ------------------------------------------------------------------ fallbacks

    private fun http(url: String, timeout: Int = 10_000): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeout
            readTimeout = timeout + 5_000
            setRequestProperty("User-Agent", UA)
        }
        return try {
            if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            conn.disconnect()
        }
    }

    private fun searchInnerTube(query: String): List<RemoteTrack> {
        val conn = (URL("https://www.youtube.com/youtubei/v1/search").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", UA)
        }
        val payload = JSONObject()
            .put("context", JSONObject().put("client", JSONObject()
                .put("clientName", "WEB").put("clientVersion", "2.20240101.00.00")
                .put("hl", "en").put("gl", "US")))
            .put("query", query)
        conn.outputStream.use { it.write(payload.toString().toByteArray()) }
        if (conn.responseCode !in 200..299) return emptyList()
        val root = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })

        val sections = root.optJSONObject("contents")
            ?.optJSONObject("twoColumnSearchResultsRenderer")
            ?.optJSONObject("primaryContents")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents") ?: return emptyList()

        val out = mutableListOf<RemoteTrack>()
        for (i in 0 until sections.length()) {
            val items = sections.optJSONObject(i)?.optJSONObject("itemSectionRenderer")?.optJSONArray("contents") ?: continue
            for (j in 0 until items.length()) {
                val v = items.optJSONObject(j)?.optJSONObject("videoRenderer") ?: continue
                val id = v.optString("videoId").takeIf { it.isNotBlank() } ?: continue
                val dur = parseDuration(v.optJSONObject("lengthText")?.optString("simpleText").orEmpty())
                if (dur < 45 || dur > 15 * 60) continue
                var title = cleanTitle(runs(v.optJSONObject("title")))
                var artist = runs(v.optJSONObject("ownerText")).ifBlank { "Unknown artist" }
                val dash = title.indexOf(" - ")
                if (dash > 0) {
                    artist = title.substring(0, dash).trim(); title = title.substring(dash + 3).trim()
                }
                out += RemoteTrack(id, title, artist, "YouTube", "https://i.ytimg.com/vi/$id/hqdefault.jpg", dur)
            }
        }
        return out
    }

    private fun searchInvidious(instance: String, query: String): List<RemoteTrack> {
        val body = http("$instance/api/v1/search?q=${enc(query)}&type=video", 8_000) ?: return emptyList()
        val arr = JSONArray(body)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("videoId").ifBlank { return@mapNotNull null }
            val dur = o.optInt("lengthSeconds", 0)
            if (dur < 45 || dur > 15 * 60) return@mapNotNull null
            RemoteTrack(
                id, cleanTitle(o.optString("title", "Untitled")), o.optString("author", "Unknown artist"),
                "YouTube", "https://i.ytimg.com/vi/$id/hqdefault.jpg", dur
            )
        }
    }

    private fun resolveInvidious(instance: String, id: String): ResolvedAudio? {
        val body = http("$instance/api/v1/videos/${enc(id)}", 8_000) ?: return null
        val formats = JSONObject(body).optJSONArray("adaptiveFormats") ?: return null
        var best: JSONObject? = null
        for (i in 0 until formats.length()) {
            val f = formats.optJSONObject(i) ?: continue
            if (!f.optString("type").startsWith("audio/") || f.optString("url").isBlank()) continue
            if (f.optString("itag") == "140") { best = f; break }
            if (best == null || f.optInt("bitrate") > best.optInt("bitrate")) best = f
        }
        best ?: return null
        val m4a = best.optString("type").startsWith("audio/mp4")
        return ResolvedAudio(best.optString("url"), if (m4a) "audio/mp4" else "audio/webm", if (m4a) "m4a" else "webm")
    }

    /** Optional self-hosted backend (backend/yt_dlp_server.py). */
    private fun searchBackend(baseUrl: String, query: String): List<RemoteTrack> {
        val body = http(baseUrl.trimEnd('/') + "/search?q=" + enc(query), 30_000) ?: return emptyList()
        val arr = JSONArray(body.trim())
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").ifBlank { return@mapNotNull null }
            RemoteTrack(
                id = id,
                title = o.optString("title", "Untitled"),
                artist = o.optString("uploader").ifBlank { o.optString("channel", "Unknown artist") },
                album = o.optString("album").ifBlank { "YouTube" },
                imageUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                durationSec = o.optInt("duration", 0)
            )
        }
    }

    private fun resolveBackend(baseUrl: String, id: String): ResolvedAudio? {
        val body = http(baseUrl.trimEnd('/') + "/resolve?id=" + enc(id), 30_000) ?: return null
        val o = JSONObject(body)
        val url = o.optString("url").ifBlank { return null }
        val ext = o.optString("ext", "m4a")
        return ResolvedAudio(url, if (ext == "m4a") "audio/mp4" else "audio/$ext", ext)
    }

    private fun runs(obj: JSONObject?): String {
        obj ?: return ""
        val runs = obj.optJSONArray("runs") ?: return obj.optString("simpleText")
        return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }.trim()
    }

    private fun parseDuration(text: String): Int {
        val p = text.split(":").mapNotNull { it.trim().toIntOrNull() }
        return p.fold(0) { acc, v -> acc * 60 + v }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
