package com.musync.app

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads cover art for local audio files (content://media/.../audio/...). */
class AudioArtFetcher(private val context: Context, private val uri: Uri) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        var bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { context.contentResolver.loadThumbnail(uri, Size(512, 512), null) }.getOrNull()
        } else null

        if (bitmap == null) {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(context, uri)
                val bytes = mmr.embeddedPicture
                if (bytes != null) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= 512) sample *= 2
                    bitmap = BitmapFactory.decodeByteArray(
                        bytes, 0, bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = sample }
                    )
                }
            } catch (_: Exception) {
            } finally {
                runCatching { mmr.release() }
            }
        }

        bitmap?.let {
            DrawableResult(BitmapDrawable(context.resources, it), false, DataSource.DISK)
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val isLocalAudio = data.scheme == "content" &&
                data.authority == "media" &&
                data.pathSegments.contains("audio")
            return if (isLocalAudio) AudioArtFetcher(context, data) else null
        }
    }
}
