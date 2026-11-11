package com.musync.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import org.schabi.newpipe.extractor.NewPipe

class MusyncApplication : Application(), ImageLoaderFactory {
    lateinit var store: MusyncStore
        private set
    lateinit var repo: MusicRepository
        private set
    lateinit var downloader: Downloader
        private set

    /** Emits when a download finished and the library should be rescanned. */
    val libraryRefresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits user-facing status / error messages (shown in a Snackbar). */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    override fun onCreate() {
        super.onCreate()
        NewPipe.init(NewPipeDownloader.instance)
        store = MusyncStore(this)
        repo = MusicRepository(this, store)
        downloader = Downloader(
            app = this,
            store = store,
            onFinished = { libraryRefresh.tryEmit(Unit) },
            onMessage = { messages.tryEmit(it) }
        )
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(AudioArtFetcher.Factory(this@MusyncApplication)) }
            .crossfade(true)
            .build()
}
