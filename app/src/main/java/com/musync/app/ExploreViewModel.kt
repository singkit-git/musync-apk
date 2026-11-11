package com.musync.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ExploreState {
    data object Loading : ExploreState
    data class Results(
        val tracks: List<RemoteTrack>,
        val trending: Boolean,
        val canLoadMore: Boolean = false,
        val loadingMore: Boolean = false
    ) : ExploreState
    data class Error(val message: String) : ExploreState
}

class ExploreViewModel(app: Application) : AndroidViewModel(app) {

    private val musync = app as MusyncApplication

    val state = MutableStateFlow<ExploreState>(ExploreState.Loading)
    val downloads: StateFlow<Map<String, DlState>> = musync.downloader.states
    val downloadProgress: StateFlow<Map<String, Float>> = musync.downloader.progress
    val serverUrl: StateFlow<String> = musync.store.serverUrl
    val recentSearches: StateFlow<List<String>> = musync.store.recentSearches

    private var currentQuery: String? = null
    private var nextLoader: (suspend () -> YouTubeApi.SearchResult)? = null
    private var job: Job? = null
    private var debounceJob: Job? = null

    /** Debounced search triggered as the user types. */
    fun onQueryChange(query: String) {
        debounceJob?.cancel()
        val trimmed = query.trim()
        if (trimmed == currentQuery && state.value is ExploreState.Results) return
        debounceJob = viewModelScope.launch {
            delay(400)
            search(trimmed, saveHistory = false)
        }
    }

    /** Immediate search (e.g. keyboard search button, recent chip, or initial load). */
    fun search(query: String, saveHistory: Boolean = true) {
        debounceJob?.cancel()
        job?.cancel()
        val trimmed = query.trim()
        currentQuery = trimmed
        if (saveHistory && trimmed.isNotEmpty()) {
            musync.store.addRecentSearch(trimmed)
        }
        job = viewModelScope.launch {
            state.value = ExploreState.Loading
            state.value = try {
                val res = YouTubeApi.search(trimmed, serverUrl.value.trim())
                nextLoader = res.loadMore
                ExploreState.Results(
                    tracks = res.tracks,
                    trending = trimmed.isEmpty(),
                    canLoadMore = res.loadMore != null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                nextLoader = null
                ExploreState.Error(e.message ?: "Could not load music. Check your internet connection.")
            }
        }
    }

    /** Loads the next page of search results when scrolling near the end. */
    fun loadMore() {
        val cur = state.value as? ExploreState.Results ?: return
        val loader = nextLoader ?: return
        if (cur.loadingMore) return

        state.value = cur.copy(loadingMore = true)
        viewModelScope.launch {
            runCatching { loader() }
                .onSuccess { more ->
                    nextLoader = more.loadMore
                    val merged = (cur.tracks + more.tracks).distinctBy { it.id }
                    state.value = ExploreState.Results(
                        tracks = merged,
                        trending = cur.trending,
                        canLoadMore = more.loadMore != null,
                        loadingMore = false
                    )
                }
                .onFailure {
                    nextLoader = null
                    state.value = cur.copy(canLoadMore = false, loadingMore = false)
                    musync.messages.tryEmit("Couldn't load more results")
                }
        }
    }

    fun removeRecentSearch(query: String) = musync.store.removeRecentSearch(query)

    /** Plays a remote track; [PlaybackService] resolves the `musync://yt/<id>` stream URL on the fly. */
    fun playTrack(player: PlayerViewModel, track: RemoteTrack, allTracks: List<RemoteTrack>) {
        currentQuery?.takeIf { it.isNotEmpty() }?.let { musync.store.addRecentSearch(it) }
        val songs = allTracks.map { it.toSong() }
        val idx = allTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        player.play(songs, idx)
    }

    fun download(track: RemoteTrack) {
        currentQuery?.takeIf { it.isNotEmpty() }?.let { musync.store.addRecentSearch(it) }
        musync.downloader.download(track)
    }
}
