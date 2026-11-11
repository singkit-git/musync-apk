package com.musync.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.musync.app.PlayerViewModel
import com.musync.app.Song
import com.musync.app.UpdateInfo

fun placeholderBrush(seed: String): Brush {
    val h = ((seed.hashCode() and 0x7fffffff) % 360).toFloat()
    return Brush.linearGradient(
        listOf(Color.hsv(h, 0.55f, 0.5f), Color.hsv((h + 50f) % 360f, 0.65f, 0.22f))
    )
}

@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    seed: String = ""
) {
    Box(
        modifier
            .clip(shape)
            .background(placeholderBrush(seed)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.MusicNote, null,
            tint = Color.White.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxSize(0.38f)
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = TextPrimary,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)
    )
}

@Composable
fun GradientCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    iconSize: androidx.compose.ui.unit.Dp = size / 2
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(AccentBrush)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = Accent, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(placeholder, color = TextSecondary) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = TextSecondary) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Rounded.Close, null, tint = TextSecondary)
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Surface2,
            unfocusedContainerColor = Surface2,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Accent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = modifier.fillMaxWidth()
    )
}

/** Pill row used for the Library sub-tabs. */
@Composable
fun PillTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(labels.size) { i ->
            val on = i == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .then(
                        if (on) Modifier.background(AccentBrush)
                        else Modifier.background(Surface2)
                    )
                    .clickable { onSelect(i) }
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Text(
                    labels[i],
                    color = if (on) Color.White else TextSecondary,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun SongRowVm(
    song: Song,
    vm: PlayerViewModel,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    val current by vm.current.collectAsStateWithLifecycle()
    val liked by vm.liked.collectAsStateWithLifecycle()
    val isCurrent = current?.id == song.id
    val isLiked = song.id in liked
    var menu by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(song.artUri, Modifier.size(52.dp), seed = song.album)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isCurrent) Accent else TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
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
                    song.artist,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, "More", tint = TextSecondary)
            }
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                modifier = Modifier.background(Surface2)
            ) {
                MenuItem("Play next", Icons.AutoMirrored.Rounded.PlaylistPlay) {
                    menu = false; vm.playNext(song)
                }
                MenuItem("Add to queue", Icons.AutoMirrored.Rounded.QueueMusic) {
                    menu = false; vm.addToQueue(song)
                }
                MenuItem(
                    if (isLiked) "Remove from liked" else "Add to liked",
                    if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder
                ) { menu = false; vm.toggleLike(song) }
                MenuItem("Add to playlist", Icons.AutoMirrored.Rounded.PlaylistAdd) {
                    menu = false; picker = true
                }
                if (onRemove != null) {
                    MenuItem("Remove from this playlist", Icons.Rounded.Close) {
                        menu = false; onRemove()
                    }
                }
            }
        }
    }

    if (picker) AddToPlaylistDialog(vm, song) { picker = false }
}

@Composable
private fun MenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label, color = TextPrimary) },
        leadingIcon = { Icon(icon, null, tint = Accent) },
        onClick = onClick
    )
}

@Composable
fun AddToPlaylistDialog(vm: PlayerViewModel, song: Song, onDismiss: () -> Unit) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text("Add to playlist", color = TextPrimary) },
        text = {
            Column {
                LazyColumn(Modifier.height(if (playlists.isEmpty()) 0.dp else 180.dp)) {
                    items(playlists, key = { it.name }) { p ->
                        Text(
                            p.name,
                            color = TextPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.addToPlaylist(p.name, song); onDismiss() }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    placeholder = { Text("New playlist name", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = newName.isNotBlank(),
                onClick = { vm.createPlaylist(newName.trim(), song); onDismiss() }
            ) { Text("Create", color = if (newName.isNotBlank()) Accent else TextSecondary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@Composable
fun NewPlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text("New playlist", color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Playlist name", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name.trim()); onDismiss() }) {
                Text("Create", color = if (name.isNotBlank()) Accent else TextSecondary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@Composable
fun SleepTimerDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(0, 10, 15, 30, 45, 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text("Sleep timer", color = TextPrimary) },
        text = {
            Column {
                options.forEach { m ->
                    Text(
                        if (m == 0) "Off" else "$m minutes",
                        color = if (m == current) Accent else TextPrimary,
                        fontWeight = if (m == current) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(m); onDismiss() }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Accent) } }
    )
}

@Composable
fun UpdateDialog(
    info: UpdateInfo,
    progress: Float?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text("Update available (v${info.version})", color = TextPrimary) },
        text = {
            Column {
                Text(
                    info.notes.ifBlank { "A new version of Musync (${info.title}) is ready to install." },
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                if (progress != null) {
                    Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress },
                        color = Accent,
                        trackColor = Surface3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Downloading update… ${(progress * 100).toInt()}%",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = progress == null, onClick = onConfirm) {
                Text(
                    if (progress != null) "Downloading…" else "Update now",
                    color = if (progress == null) Accent else TextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            if (progress == null) {
                TextButton(onClick = onDismiss) { Text("Later", color = TextSecondary) }
            }
        }
    )
}
