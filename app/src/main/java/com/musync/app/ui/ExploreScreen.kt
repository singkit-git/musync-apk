package com.musync.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musync.app.DlState
import com.musync.app.ExploreState
import com.musync.app.ExploreViewModel
import com.musync.app.PlayerViewModel
import com.musync.app.RemoteTrack
import com.musync.app.asClock

@Composable
fun ExploreScreen(player: PlayerViewModel, vm: ExploreViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val progress by vm.downloadProgress.collectAsStateWithLifecycle()
    val recentSearches by vm.recentSearches.collectAsStateWithLifecycle()
    val current by player.current.collectAsStateWithLifecycle()
    val liked by player.liked.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current

    LaunchedEffect(Unit) {
        if (state is ExploreState.Loading && (state as? ExploreState.Results) == null) {
            vm.search(query, saveHistory = false)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Explore")
        Text(
            "Search music to stream and download to your library",
            color = TextSecondary, fontSize = 14.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
        )
        SearchField(
            value = query,
            onValueChange = {
                query = it
                vm.onQueryChange(it)
            },
            onSearch = {
                focus.clearFocus()
                vm.search(query, saveHistory = true)
            },
            placeholder = "Search songs or artists",
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        if (recentSearches.isNotEmpty() && query.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recentSearches, key = { it }) { item ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Surface2)
                            .clickable {
                                query = item
                                focus.clearFocus()
                                vm.search(item, saveHistory = true)
                            }
                            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.History, null,
                            tint = TextSecondary, modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(item, color = TextPrimary, fontSize = 13.sp, maxLines = 1)
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Rounded.Close, "Remove",
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { vm.removeRecentSearch(item) }
                                .padding(2.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        when (val s = state) {
            ExploreState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }

            is ExploreState.Error -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                EmptyState("Couldn't load music", s.message)
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AccentBrush)
                        .clickable { vm.search(query, saveHistory = false) }
                        .padding(horizontal = 26.dp, vertical = 11.dp)
                ) {
                    Text("Try again", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            is ExploreState.Results -> {
                if (s.tracks.isEmpty()) {
                    EmptyState("No results", "Try a different song or artist name.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        item {
                            SectionTitle(if (s.trending) "Trending music" else "Results")
                        }
                        itemsIndexed(s.tracks, key = { _, t -> t.id }) { index, t ->
                            if (index >= s.tracks.size - 3 && s.canLoadMore && !s.loadingMore) {
                                LaunchedEffect(s.tracks.size) { vm.loadMore() }
                            }
                            val songId = "yt_${t.id}"
                            TrackRow(
                                track = t,
                                isCurrent = current?.id == songId,
                                isLiked = songId in liked,
                                state = downloads[t.id],
                                progress = progress[t.id] ?: 0f,
                                player = player,
                                onPlay = { vm.playTrack(player, t, s.tracks) },
                                onDownload = { vm.download(t) }
                            )
                        }
                        if (s.loadingMore) {
                            item {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 18.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Accent,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        } else if (s.canLoadMore) {
                            item {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(Surface2)
                                            .clickable { vm.loadMore() }
                                            .padding(horizontal = 22.dp, vertical = 10.dp)
                                    ) {
                                        Text("Load more", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: RemoteTrack,
    isCurrent: Boolean,
    isLiked: Boolean,
    state: DlState?,
    progress: Float,
    player: PlayerViewModel,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    val song = remember(track) { track.toSong() }
    var menu by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(track.imageUrl, Modifier.size(54.dp), seed = track.album)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (isCurrent) Accent else TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val dur = if (track.durationSec > 0) " · ${(track.durationSec * 1000L).asClock()}" else ""
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLiked) {
                    Icon(
                        Icons.Rounded.Favorite, null,
                        tint = PinkSoft,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(13.dp)
                    )
                }
                Text(
                    "${track.artist}$dur",
                    color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }

        when (state) {
            DlState.Downloading -> Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                if (progress > 0f) {
                    CircularProgressIndicator(
                        progress = { progress },
                        color = Accent,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    CircularProgressIndicator(
                        color = Accent,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            DlState.Done -> Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.CheckCircle, "Downloaded", tint = Accent)
            }

            else -> IconButton(onClick = onDownload, modifier = Modifier.size(44.dp)) {
                Icon(
                    if (state == DlState.Failed) Icons.Rounded.ErrorOutline else Icons.Rounded.Download,
                    "Download",
                    tint = if (state == DlState.Failed) PinkSoft else TextPrimary
                )
            }
        }

        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.MoreVert, "More", tint = TextSecondary)
            }
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                modifier = Modifier.background(Surface2)
            ) {
                DropdownMenuItem(
                    text = { Text("Play next", color = TextPrimary) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = Accent) },
                    onClick = { menu = false; player.playNext(song) }
                )
                DropdownMenuItem(
                    text = { Text("Add to queue", color = TextPrimary) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = Accent) },
                    onClick = { menu = false; player.addToQueue(song) }
                )
                DropdownMenuItem(
                    text = { Text(if (isLiked) "Remove from liked" else "Add to liked", color = TextPrimary) },
                    leadingIcon = {
                        Icon(
                            if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            null, tint = Accent
                        )
                    },
                    onClick = { menu = false; player.toggleLike(song) }
                )
                DropdownMenuItem(
                    text = { Text("Add to playlist", color = TextPrimary) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, tint = Accent) },
                    onClick = { menu = false; picker = true }
                )
            }
        }
    }

    if (picker) AddToPlaylistDialog(player, song) { picker = false }
}
