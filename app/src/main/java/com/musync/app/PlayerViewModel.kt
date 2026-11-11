package com.musync.app

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val musync = app as MusyncApplication
    val store: MusyncStore = musync.store
    val messages: SharedFlow<String> = musync.messages

    // Library
    val songs = MutableStateFlow<List<Song>>(emptyList())
    val loading = MutableStateFlow(false)
    val remoteSongs: StateFlow<Map<String, Song>> = store.remoteSongs

    // Downloads
    val downloads: StateFlow<Map<String, DlState>> = musync.downloader.states
    val downloadProgress: StateFlow<Map<String, Float>> = musync.downloader.progress

    // App updates (GitHub Releases)
    val availableUpdate = MutableStateFlow<UpdateInfo?>(null)
    val checkingUpdate = MutableStateFlow(false)
    val updatingProgress = MutableStateFlow<Float?>(null)

    // Player state
    val current = MutableStateFlow<Song?>(null)
    val isPlaying = MutableStateFlow(false)
    val position = MutableStateFlow(0L)
    val duration = MutableStateFlow(0L)
    val shuffle = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val queue = MutableStateFlow<List<Song>>(emptyList())
    val sleepMinutes = MutableStateFlow(0)

    // Lyrics
    val currentLyrics = MutableStateFlow<SongLyrics?>(null)
    val lyricsLoading = MutableStateFlow(false)

    // Persisted state
    val playlists: StateFlow<List<Playlist>> = store.playlists
    val liked: StateFlow<List<String>> = store.liked
    val recents: StateFlow<List<String>> = store.recents

    private var controller: MediaController? = null
    private val songMap = HashMap<String, Song>()
    private var sleepJob: Job? = null
    private var lyricsJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    init {
        songMap.putAll(store.remoteSongs.value)
        connect()
        refreshLibrary()
        checkForUpdates(silent = true)
        viewModelScope.launch { musync.libraryRefresh.collect { refreshLibrary() } }
        viewModelScope.launch {
            while (true) {
                delay(400)
                controller?.let { if (it.isPlaying) position.value = it.currentPosition }
            }
        }
    }

    private fun connect() {
        val app = getApplication<Application>()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.getOrNull()?.let { c ->
                controller = c
                c.addListener(listener)
                sync(c)
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun sync(p: Player) {
        isPlaying.value = p.isPlaying
        shuffle.value = p.shuffleModeEnabled
        repeatMode.value = p.repeatMode
        duration.value = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        position.value = p.currentPosition

        val song = p.currentMediaItem?.mediaId?.let { songMap[it] ?: store.remoteSongs.value[it] }
        if (song?.id != current.value?.id) {
            current.value = song
            if (song != null) {
                if (song.remote) store.saveRemoteSong(song)
                store.addRecent(song.id)
                fetchLyrics(song)
            } else {
                currentLyrics.value = null
                lyricsLoading.value = false
            }
        }
        queue.value = (0 until p.mediaItemCount).mapNotNull {
            val id = p.getMediaItemAt(it).mediaId
            songMap[id] ?: store.remoteSongs.value[id]
        }
    }

    fun fetchLyrics(song: Song) {
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            lyricsLoading.value = true
            currentLyrics.value = LyricsApi.getLyrics(getApplication(), song)
            lyricsLoading.value = false
        }
    }

    // ---- library ----
    fun refreshLibrary() {
        viewModelScope.launch {
            loading.value = true
            val loaded = musync.repo.loadSongs(store.hideShort.value)
            loaded.forEach { songMap[it.id] = it }
            songs.value = loaded
            loading.value = false
        }
    }

    // ---- playback ----
    fun play(list: List<Song>, index: Int, shuffleOn: Boolean = false) {
        val c = controller ?: return
        if (list.isEmpty()) return
        list.forEach { songMap[it.id] = it }
        val startIdx = index.coerceIn(0, list.lastIndex)
        list[startIdx].let { if (it.remote) store.saveRemoteSong(it) }
        c.shuffleModeEnabled = shuffleOn
        c.setMediaItems(list.map { it.toMediaItem() }, startIdx, 0L)
        c.prepare()
        c.play()
    }

    fun shuffleAll(list: List<Song>) {
        if (list.isEmpty()) return
        play(list, Random.nextInt(list.size), true)
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNext() }

    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPrevious()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        position.value = ms
    }

    fun skipTo(index: Int) {
        controller?.seekToDefaultPosition(index)
        controller?.play()
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        songMap[song.id] = song
        if (song.remote) store.saveRemoteSong(song)
        if (c.mediaItemCount == 0) play(listOf(song), 0)
        else {
            c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
            musync.messages.tryEmit("Playing \"${song.title}\" next")
        }
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        songMap[song.id] = song
        if (song.remote) store.saveRemoteSong(song)
        if (c.mediaItemCount == 0) play(listOf(song), 0)
        else {
            c.addMediaItem(song.toMediaItem())
            musync.messages.tryEmit("Added \"${song.title}\" to queue")
        }
    }

    fun download(song: Song) {
        song.toRemoteTrack()?.let { musync.downloader.download(it) }
    }

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepMinutes.value = minutes
        if (minutes > 0) {
            sleepJob = viewModelScope.launch {
                delay(minutes * 60_000L)
                controller?.pause()
                sleepMinutes.value = 0
            }
        }
    }

    // ---- likes & playlists ----
    fun toggleLike(song: Song) {
        if (song.remote) store.saveRemoteSong(song)
        store.toggleLike(song.id)
    }

    fun createPlaylist(name: String, firstSong: Song? = null) {
        if (firstSong != null && firstSong.remote) store.saveRemoteSong(firstSong)
        store.createPlaylist(name, firstSong?.id)
    }

    fun addToPlaylist(name: String, song: Song) {
        if (song.remote) store.saveRemoteSong(song)
        store.addToPlaylist(name, song.id)
        musync.messages.tryEmit("Added to $name")
    }

    fun removeFromPlaylist(name: String, songId: String) = store.removeFromPlaylist(name, songId)
    fun deletePlaylist(name: String) = store.deletePlaylist(name)

    // ---- app updates ----
    fun checkForUpdates(silent: Boolean = false) {
        if (checkingUpdate.value) return
        viewModelScope.launch {
            checkingUpdate.value = true
            val info = runCatching { UpdateChecker.checkForUpdate(getApplication()) }.getOrNull()
            checkingUpdate.value = false
            if (info != null) {
                availableUpdate.value = info
            } else if (!silent) {
                val ver = UpdateChecker.currentVersion(getApplication())
                musync.messages.tryEmit("You're on the latest version ($ver)")
            }
        }
    }

    fun dismissUpdate() {
        if (updatingProgress.value == null) availableUpdate.value = null
    }

    fun installUpdate(info: UpdateInfo) {
        if (updatingProgress.value != null) return
        viewModelScope.launch {
            updatingProgress.value = 0f
            val ok = runCatching {
                UpdateChecker.downloadAndInstall(getApplication(), info) { p ->
                    updatingProgress.value = p
                }
            }.getOrDefault(false)
            updatingProgress.value = null
            if (ok) {
                availableUpdate.value = null
            } else {
                musync.messages.tryEmit("Couldn't download update")
            }
        }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        super.onCleared()
    }
}
