package com.musync.app

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Reads the device's audio library through MediaStore and enriches Musync downloads with saved cover art & tags. */
class MusicRepository(
    private val context: Context,
    private val store: MusyncStore
) {

    suspend fun loadSongs(hideShort: Boolean): List<Song> = withContext(Dispatchers.IO) {
        val out = ArrayList<Song>()
        val metaByFile = store.downloads().values.associateBy { it.fileName }
        val seenFiles = HashSet<String>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )
        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            if (hideShort) append(" AND ${MediaStore.Audio.Media.DURATION} >= 30000")
        }

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val displayName = c.getString(nameCol).orEmpty()
                    val meta = metaByFile[displayName]
                    if (meta != null) seenFiles += displayName

                    val albumId = c.getLong(albumIdCol)
                    val rawTitle = c.getString(titleCol)?.takeIf { it.isNotBlank() }
                    val rawArtist = c.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" }
                    val rawAlbum = c.getString(albumCol)?.takeIf { it.isNotBlank() && it != "<unknown>" }

                    val title = meta?.title?.takeIf { it.isNotBlank() } ?: rawTitle ?: "Untitled"
                    val artist = meta?.artist?.takeIf { it.isNotBlank() } ?: rawArtist ?: "Unknown artist"
                    val album = meta?.album?.takeIf { it.isNotBlank() } ?: rawAlbum ?: "Unknown album"

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    ).toString()

                    val savedArtFile = meta?.artPath?.let(::File)?.takeIf { it.exists() }
                    val artUri = savedArtFile?.let { Uri.fromFile(it).toString() } ?: contentUri

                    out += Song(
                        id = id.toString(),
                        title = title,
                        artist = artist,
                        album = album,
                        albumId = albumId,
                        durationMs = c.getLong(durCol),
                        uri = contentUri,
                        artUri = artUri
                    )
                }
            }
            (context.applicationContext as? MusyncApplication)?.downloader?.reconcile(seenFiles)
        } catch (_: SecurityException) {
            // Permission not granted yet: show an empty library.
        }
        out.sortedBy { it.title.lowercase() }
    }
}
