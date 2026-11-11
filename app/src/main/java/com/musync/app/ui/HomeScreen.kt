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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musync.app.PlayerViewModel
import com.musync.app.Song
import com.musync.app.toAlbums
import java.util.Calendar

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 5 -> "Late night vibes"
        h < 12 -> "Good morning"
        h < 18 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
fun HomeScreen(vm: PlayerViewModel, onGoExplore: () -> Unit) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val remoteSongs by vm.remoteSongs.collectAsStateWithLifecycle()
    val recentIds by vm.recents.collectAsStateWithLifecycle()
    val likedIds by vm.liked.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()

    val byId = remember(songs, remoteSongs) { remoteSongs + songs.associateBy { it.id } }
    val recents = remember(recentIds, byId) { recentIds.mapNotNull { byId[it] } }
    val likedSongs = remember(likedIds, byId) { likedIds.mapNotNull { byId[it] } }
    val albums = remember(songs) { songs.toAlbums().shuffled() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp)
            ) {
                Text(
                    "Musync",
                    style = MaterialTheme.typography.headlineMedium.copy(brush = AccentBrush)
                )
                Spacer(Modifier.height(2.dp))
                Text(greeting(), color = TextSecondary, fontSize = 15.sp)
            }
        }

        if (songs.isEmpty() && recents.isEmpty() && likedSongs.isEmpty()) {
            item {
                Spacer(Modifier.height(40.dp))
                EmptyState(
                    "Your library is empty",
                    "Allow music access, or head to Explore to stream and download tracks."
                )
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(AccentBrush)
                            .clickable(onClick = onGoExplore)
                            .padding(horizontal = 28.dp, vertical = 12.dp)
                    ) { Text("Explore music", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
            return@LazyColumn
        }

        // Hero: shuffle everything
        val pool = songs.ifEmpty { (recents + likedSongs).distinctBy { it.id } }
        item {
            Box(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(AccentBrush)
                    .clickable { vm.shuffleAll(pool) }
                    .padding(22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Shuffle all",
                            color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${pool.size} songs ready to play",
                            color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp
                        )
                    }
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Shuffle, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }

        // Quick access
        val quick = buildList<QuickItem> {
            if (likedSongs.isNotEmpty()) add(QuickItem("Liked Songs", null, true) { vm.play(likedSongs, 0) })
            recents.take(6).forEach { s ->
                add(QuickItem(s.title, s.artUri, false) { vm.play(recents, recents.indexOf(s)) })
            }
        }.take(6)
        if (quick.isNotEmpty()) {
            items(quick.chunked(2)) { pair ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEach { q -> QuickTile(q, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (recents.isNotEmpty()) {
            item { SectionTitle("Jump back in") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(recents, key = { it.id }) { s ->
                        SquareCard(s.title, s.artist, s.artUri, s.album) {
                            vm.play(recents, recents.indexOf(s))
                        }
                    }
                }
            }
        }

        if (albums.isNotEmpty()) {
            item { SectionTitle("Albums for you") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(albums.take(20), key = { it.key }) { a ->
                        SquareCard(a.name, a.artist, a.artUri, a.name) { vm.play(a.songs, 0) }
                    }
                }
            }
        }

        if (playlists.isNotEmpty()) {
            item { SectionTitle("Your playlists") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(playlists, key = { it.name }) { p ->
                        val list = p.songIds.mapNotNull { byId[it] }
                        SquareCard(p.name, "${list.size} songs", list.firstOrNull()?.artUri, p.name) {
                            if (list.isNotEmpty()) vm.play(list, 0)
                        }
                    }
                }
            }
        }
    }
}

private data class QuickItem(
    val title: String,
    val art: String?,
    val isLiked: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun QuickTile(item: QuickItem, modifier: Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .clickable(onClick = item.onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.isLiked) {
            Box(
                Modifier
                    .size(54.dp)
                    .background(AccentBrush),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Favorite, null, tint = Color.White) }
        } else {
            Artwork(item.art, Modifier.size(54.dp), RoundedCornerShape(0.dp), item.title)
        }
        Text(
            item.title,
            color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
    }
}

@Composable
fun SquareCard(title: String, subtitle: String, art: String?, seed: String, onClick: () -> Unit) {
    Column(
        Modifier
            .width(148.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Artwork(art, Modifier.size(148.dp), RoundedCornerShape(16.dp), seed)
        Spacer(Modifier.height(8.dp))
        Text(
            title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(
            subtitle, color = TextSecondary, fontSize = 12.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}
