package com.musync.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Small SharedPreferences-backed store for playlists, likes, recents and settings. */
class MusyncStore(context: Context) {
    private val sp = context.getSharedPreferences("musync", Context.MODE_PRIVATE)

    private val _playlists = MutableStateFlow(readPlaylists())
    val playlists: StateFlow<List<Playlist>> = _playlists

    private val _liked = MutableStateFlow(readList("liked"))
    val liked: StateFlow<List<String>> = _liked

    private val _recents = MutableStateFlow(readList("recents"))
    val recents: StateFlow<List<String>> = _recents

    private val _serverUrl = MutableStateFlow(sp.getString("server_url", "") ?: "")
    val serverUrl: StateFlow<String> = _serverUrl

    private val _hideShort = MutableStateFlow(sp.getBoolean("hide_short", true))
    val hideShort: StateFlow<Boolean> = _hideShort

    // ---- settings ----
    fun setServerUrl(value: String) {
        _serverUrl.value = value
        sp.edit().putString("server_url", value).apply()
    }

    fun setHideShort(value: Boolean) {
        _hideShort.value = value
        sp.edit().putBoolean("hide_short", value).apply()
    }

    // ---- likes & recents ----
    fun toggleLike(id: String) {
        val next = if (id in _liked.value) _liked.value - id else listOf(id) + _liked.value
        _liked.value = next
        writeList("liked", next)
    }

    fun addRecent(id: String) {
        val next = (listOf(id) + _recents.value.filter { it != id }).take(30)
        _recents.value = next
        writeList("recents", next)
    }

    // ---- playlists ----
    fun createPlaylist(name: String, firstSongId: String? = null) {
        if (_playlists.value.any { it.name.equals(name, ignoreCase = true) }) {
            if (firstSongId != null) {
                val existing = _playlists.value.first { it.name.equals(name, ignoreCase = true) }
                addToPlaylist(existing.name, firstSongId)
            }
            return
        }
        val ids = if (firstSongId != null) listOf(firstSongId) else emptyList()
        savePlaylists(_playlists.value + Playlist(name, ids))
    }

    fun addToPlaylist(name: String, songId: String) {
        savePlaylists(_playlists.value.map {
            if (it.name == name && songId !in it.songIds) it.copy(songIds = it.songIds + songId) else it
        })
    }

    fun removeFromPlaylist(name: String, songId: String) {
        savePlaylists(_playlists.value.map {
            if (it.name == name) it.copy(songIds = it.songIds - songId) else it
        })
    }

    fun deletePlaylist(name: String) {
        savePlaylists(_playlists.value.filter { it.name != name })
    }

    // ---- streamed (remote) songs that were liked / added to playlists / played ----
    private val _remoteSongs = MutableStateFlow(readRemoteSongs())
    val remoteSongs: StateFlow<Map<String, Song>> = _remoteSongs

    fun saveRemoteSong(song: Song) {
        if (!song.remote || _remoteSongs.value[song.id] == song) return
        val next = _remoteSongs.value + (song.id to song)
        _remoteSongs.value = next
        val arr = JSONArray()
        next.values.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id).put("title", s.title).put("artist", s.artist)
                    .put("album", s.album).put("dur", s.durationMs).put("uri", s.uri)
                    .put("art", s.artUri ?: "")
            )
        }
        sp.edit().putString("remote_songs", arr.toString()).apply()
    }

    private fun readRemoteSongs(): Map<String, Song> = runCatching {
        val arr = JSONArray(sp.getString("remote_songs", "[]"))
        (0 until arr.length()).associate { i ->
            val o = arr.getJSONObject(i)
            val s = Song(
                id = o.getString("id"),
                title = o.optString("title"),
                artist = o.optString("artist"),
                album = o.optString("album"),
                albumId = 0L,
                durationMs = o.optLong("dur"),
                uri = o.optString("uri"),
                artUri = o.optString("art").ifBlank { null },
                remote = true
            )
            s.id to s
        }
    }.getOrDefault(emptyMap())

    // ---- recent searches ----
    private val _recentSearches = MutableStateFlow(readList("recent_searches"))
    val recentSearches: StateFlow<List<String>> = _recentSearches

    fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val next = (listOf(q) + _recentSearches.value.filterNot { it.equals(q, true) }).take(10)
        _recentSearches.value = next
        writeList("recent_searches", next)
    }

    fun removeRecentSearch(query: String) {
        val next = _recentSearches.value - query
        _recentSearches.value = next
        writeList("recent_searches", next)
    }

    // ---- downloaded videos: videoId -> file name + metadata ----
    data class DownloadMeta(
        val videoId: String,
        val fileName: String,
        val title: String,
        val artist: String,
        val album: String,
        val artPath: String?
    )

    fun downloads(): Map<String, DownloadMeta> = runCatching {
        val o = JSONObject(sp.getString("downloads", "{}") ?: "{}")
        o.keys().asSequence().associateWith { id ->
            val m = o.getJSONObject(id)
            DownloadMeta(
                id, m.getString("file"), m.optString("title"), m.optString("artist"),
                m.optString("album"), m.optString("art").ifBlank { null }
            )
        }
    }.getOrDefault(emptyMap())

    fun setDownload(meta: DownloadMeta) = writeDownloads(downloads() + (meta.videoId to meta))

    fun removeDownload(videoId: String) = writeDownloads(downloads() - videoId)

    private fun writeDownloads(map: Map<String, DownloadMeta>) {
        val o = JSONObject()
        map.values.forEach { m ->
            o.put(
                m.videoId,
                JSONObject().put("file", m.fileName).put("title", m.title).put("artist", m.artist)
                    .put("album", m.album).put("art", m.artPath ?: "")
            )
        }
        sp.edit().putString("downloads", o.toString()).commit()
    }

    // ---- persistence helpers ----
    private fun savePlaylists(list: List<Playlist>) {
        _playlists.value = list
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().put("name", p.name).put("ids", JSONArray(p.songIds)))
        }
        sp.edit().putString("playlists", arr.toString()).apply()
    }

    private fun readPlaylists(): List<Playlist> = runCatching {
        val arr = JSONArray(sp.getString("playlists", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ids = o.getJSONArray("ids")
            Playlist(o.getString("name"), (0 until ids.length()).map { ids.getString(it) })
        }
    }.getOrDefault(emptyList())

    private fun readList(key: String): List<String> = runCatching {
        val arr = JSONArray(sp.getString(key, "[]"))
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrDefault(emptyList())

    private fun writeList(key: String, list: List<String>) {
        sp.edit().putString(key, JSONArray(list).toString()).apply()
    }
}
