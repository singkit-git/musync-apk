package com.musync.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musync.app.PlayerViewModel
import com.musync.app.Song
import com.musync.app.toAlbums
import com.musync.app.toArtists

private sealed interface Detail {
    data class AlbumD(val key: String) : Detail
    data class ArtistD(val name: String) : Detail
    data class PlaylistD(val name: String) : Detail
    data object LikedD : Detail
}

@Composable
fun LibraryScreen(vm: PlayerViewModel) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val remoteSongs by vm.remoteSongs.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val likedIds by vm.liked.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var showNew by remember { mutableStateOf(false) }

    val albums = remember(songs) { songs.toAlbums() }
    val artists = remember(songs) { songs.toArtists() }
    val byId = remember(songs, remoteSongs) { remoteSongs + songs.associateBy { it.id } }
    val likedSongs = remember(likedIds, byId) { likedIds.mapNotNull { byId[it] } }

    BackHandler(enabled = detail != null) { detail = null }

    when (val d = detail) {
        is Detail.AlbumD -> albums.firstOrNull { it.key == d.key }?.let { a ->
            DetailScreen(a.name, "${a.artist} · ${a.songs.size} songs", a.artUri, a.songs, vm, { detail = null })
        } ?: run { detail = null }

        is Detail.ArtistD -> artists.firstOrNull { it.name == d.name }?.let { a ->
            DetailScreen(
                a.name, "${a.songs.size} songs · ${a.albumCount} albums",
                a.songs.firstOrNull()?.artUri, a.songs, vm, { detail = null }, circleArt = true
            )
        } ?: run { detail = null }

        is Detail.PlaylistD -> playlists.firstOrNull { it.name == d.name }?.let { p ->
            val list = p.songIds.mapNotNull { byId[it] }
            DetailScreen(
                p.name, "${list.size} songs", list.firstOrNull()?.artUri, list, vm,
                { detail = null },
                playlistName = p.name,
                onDeletePlaylist = { vm.deletePlaylist(p.name); detail = null }
            )
        } ?: run { detail = null }

        Detail.LikedD -> DetailScreen(
            "Liked Songs", "${likedSongs.size} songs", null, likedSongs, vm, { detail = null },
            likedHero = true
        )

        null -> Column(Modifier.fillMaxSize()) {
            ScreenHeader("Your Library") {
                if (tab == 3) {
                    IconButton(onClick = { showNew = true }) {
                        Icon(Icons.Rounded.Add, "New playlist", tint = Accent, modifier = Modifier.size(28.dp))
                    }
                }
            }
            PillTabs(listOf("Songs", "Albums", "Artists", "Playlists"), tab) { tab = it }
            Spacer(Modifier.height(8.dp))

            when (tab) {
                0 -> SongsTab(songs, vm)
                1 -> AlbumsTab(albums) { detail = Detail.AlbumD(it.key) }
                2 -> ArtistsTab(artists) { detail = Detail.ArtistD(it.name) }
                else -> PlaylistsTab(
                    playlists = playlists.map { it to it.songIds.mapNotNull { id -> byId[id] } },
                    likedCount = likedSongs.size,
                    onLiked = { detail = Detail.LikedD },
                    onOpen = { detail = Detail.PlaylistD(it) }
                )
            }
        }
    }

    if (showNew) NewPlaylistDialog({ vm.createPlaylist(it) }) { showNew = false }
}

@Composable
private fun SongsTab(songs: List<Song>, vm: PlayerViewModel) {
    if (songs.isEmpty()) {
        EmptyState("No songs yet", "Music on your phone will show up here. Download free tracks from Explore.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${songs.size} songs", color = TextSecondary, fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                GradientCircleButton(Icons.Rounded.Shuffle, 42.dp, { vm.shuffleAll(songs) }, 22.dp)
            }
        }
        items(songs, key = { it.id }) { s ->
            SongRowVm(s, vm, onClick = { vm.play(songs, songs.indexOf(s)) })
        }
    }
}

@Composable
private fun AlbumsTab(albums: List<com.musync.app.Album>, onOpen: (com.musync.app.Album) -> Unit) {
    if (albums.isEmpty()) {
        EmptyState("No albums", "Albums appear once you have music on this device.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        items(albums, key = { it.key }) { a ->
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOpen(a) }
            ) {
                Artwork(a.artUri, Modifier.fillMaxWidth().height(160.dp), RoundedCornerShape(16.dp), a.name)
                Spacer(Modifier.height(8.dp))
                Text(
                    a.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    a.artist, color = TextSecondary, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ArtistsTab(artists: List<com.musync.app.Artist>, onOpen: (com.musync.app.Artist) -> Unit) {
    if (artists.isEmpty()) {
        EmptyState("No artists", "Artists appear once you have music on this device.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        items(artists, key = { it.name }) { a ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(a) }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(a.songs.firstOrNull()?.artUri, Modifier.size(56.dp), CircleShape, a.name)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        a.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${a.songs.size} songs", color = TextSecondary, fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<Pair<com.musync.app.Playlist, List<Song>>>,
    likedCount: Int,
    onLiked: () -> Unit,
    onOpen: (String) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onLiked)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentBrush),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Favorite, null, tint = Color.White) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Liked Songs", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("$likedCount songs", color = TextSecondary, fontSize = 13.sp)
                }
            }
        }
        if (playlists.isEmpty()) {
            item {
                EmptyState(
                    "No playlists yet",
                    "Tap + to create one, or use the ⋮ menu on any song to add it to a playlist."
                )
            }
        }
        items(playlists, key = { it.first.name }) { (p, list) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(p.name) }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(list.firstOrNull()?.artUri, Modifier.size(56.dp), RoundedCornerShape(12.dp), p.name)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        p.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text("${list.size} songs", color = TextSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun DetailScreen(
    title: String,
    subtitle: String,
    artUri: String?,
    songs: List<Song>,
    vm: PlayerViewModel,
    onBack: () -> Unit,
    circleArt: Boolean = false,
    likedHero: Boolean = false,
    playlistName: String? = null,
    onDeletePlaylist: (() -> Unit)? = null
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary)
                    }
                    Spacer(Modifier.weight(1f))
                    if (onDeletePlaylist != null) {
                        IconButton(onClick = onDeletePlaylist) {
                            Icon(Icons.Rounded.Delete, "Delete playlist", tint = TextSecondary)
                        }
                    }
                }
                if (likedHero) {
                    Box(
                        Modifier
                            .size(190.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(AccentBrush),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(72.dp)) }
                } else {
                    Artwork(
                        artUri, Modifier.size(190.dp),
                        if (circleArt) CircleShape else RoundedCornerShape(24.dp), title
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    title, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = TextSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(AccentBrush)
                            .clickable { vm.play(songs, 0) }
                            .padding(horizontal = 26.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Play", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Surface2)
                            .clickable { vm.shuffleAll(songs) }
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Shuffle, null, tint = Accent)
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        if (songs.isEmpty()) {
            item { EmptyState("Nothing here yet", "Add songs from the ⋮ menu on any track.") }
        }
        items(songs, key = { it.id }) { s ->
            SongRowVm(
                s, vm,
                onClick = { vm.play(songs, songs.indexOf(s)) },
                onRemove = playlistName?.let { n -> { vm.removeFromPlaylist(n, s.id) } }
            )
        }
    }
}
