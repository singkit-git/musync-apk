package com.musync.app

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: String,
    val artUri: String?,
    val remote: Boolean = false
)

data class Album(
    val key: String,
    val name: String,
    val artist: String,
    val artUri: String?,
    val songs: List<Song>
)

data class Artist(val name: String, val songs: List<Song>) {
    val albumCount: Int get() = songs.map { it.album }.distinct().size
}

data class Playlist(val name: String, val songIds: List<String>)

data class RemoteTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val imageUrl: String?,
    val durationSec: Int
) {
    /**
     * Streamed songs use a `musync://yt/<videoId>` URI. PlaybackService resolves it to a fresh
     * audio URL right before playback, so queued songs never hit expired stream links.
     */
    fun toSong() = Song(
        id = "yt_$id",
        title = title,
        artist = artist,
        album = album,
        albumId = 0L,
        durationMs = durationSec * 1000L,
        uri = remoteUri(id),
        artUri = imageUrl,
        remote = true
    )
}

const val REMOTE_SCHEME = "musync"

fun remoteUri(videoId: String) = "$REMOTE_SCHEME://yt/$videoId"

/** Extracts the YouTube video id from a `musync://yt/<id>` URI, or null. */
fun videoIdFromUri(uri: Uri): String? =
    if (uri.scheme == REMOTE_SCHEME) uri.lastPathSegment else null

/** Converts a streamed Song back to a RemoteTrack (for downloading from Now Playing etc.). */
fun Song.toRemoteTrack(): RemoteTrack? {
    if (!remote) return null
    val vid = videoIdFromUri(Uri.parse(uri)) ?: id.removePrefix("yt_")
    return RemoteTrack(vid, title, artist, album, artUri, (durationMs / 1000).toInt())
}

fun Song.toMediaItem(): MediaItem {
    val imageUri = artUri?.takeIf { it.startsWith("http") || it.startsWith("file:") }?.let(Uri::parse)
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(imageUri)
                .build()
        )
        .build()
}

fun List<Song>.toAlbums(): List<Album> =
    groupBy { it.album + "|" + it.artist }
        .map { (key, list) ->
            Album(
                key = key,
                name = list.first().album,
                artist = list.first().artist,
                artUri = list.firstOrNull { it.artUri != null }?.artUri,
                songs = list.sortedBy { it.title.lowercase() }
            )
        }
        .sortedBy { it.name.lowercase() }

fun List<Song>.toArtists(): List<Artist> =
    groupBy { it.artist }
        .map { (name, list) -> Artist(name, list.sortedBy { it.title.lowercase() }) }
        .sortedBy { it.name.lowercase() }

fun Long.asClock(): String {
    val total = (this / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
