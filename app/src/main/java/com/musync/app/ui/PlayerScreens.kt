package com.musync.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.musync.app.DlState
import com.musync.app.PlayerViewModel
import com.musync.app.asClock

@Composable
fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit) {
    val song by vm.current.collectAsStateWithLifecycle()
    val playing by vm.isPlaying.collectAsStateWithLifecycle()
    val pos by vm.position.collectAsStateWithLifecycle()
    val dur by vm.duration.collectAsStateWithLifecycle()
    val s = song ?: return
    val progress = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .shadow(12.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Surface2)
            .clickable(onClick = onOpen)
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Artwork(s.artUri, Modifier.size(46.dp), RoundedCornerShape(10.dp), s.album)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    s.title, color = TextPrimary, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    s.artist, color = TextSecondary, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { vm.previous() }) {
                Icon(Icons.Rounded.SkipPrevious, "Previous", tint = TextPrimary)
            }
            GradientCircleButton(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                40.dp, { vm.togglePlay() }, 24.dp
            )
            IconButton(onClick = { vm.next() }) {
                Icon(Icons.Rounded.SkipNext, "Next", tint = TextPrimary)
            }
        }
        // Thin progress line along the bottom edge
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 12.dp)
                .padding(bottom = 3.dp)
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.10f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(2.dp)
                    .background(AccentBrush)
            )
        }
    }
}

@Composable
fun NowPlayingScreen(vm: PlayerViewModel, onClose: () -> Unit) {
    val song by vm.current.collectAsStateWithLifecycle()
    val playing by vm.isPlaying.collectAsStateWithLifecycle()
    val pos by vm.position.collectAsStateWithLifecycle()
    val dur by vm.duration.collectAsStateWithLifecycle()
    val shuffle by vm.shuffle.collectAsStateWithLifecycle()
    val repeat by vm.repeatMode.collectAsStateWithLifecycle()
    val liked by vm.liked.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val sleep by vm.sleepMinutes.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()

    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showPlaylist by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val s = song ?: return
    val safeDur = dur.coerceAtLeast(1L)
    val sliderValue = dragging ?: (pos.toFloat() / safeDur).coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .background(GlowBrush)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            // Top bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Close", tint = TextPrimary, modifier = Modifier.size(32.dp))
                }
                Text(
                    if (showQueue) "UP NEXT" else "NOW PLAYING",
                    color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.width(48.dp))
            }

            // Artwork or queue
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (showQueue) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(queue) { i, q ->
                            val isCur = q.id == s.id
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { vm.skipTo(i) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Artwork(q.artUri, Modifier.size(44.dp), RoundedCornerShape(8.dp), q.album)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        q.title, color = if (isCur) Accent else TextPrimary,
                                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        q.artist, color = TextSecondary, fontSize = 12.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(q.durationMs.asClock(), color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    Artwork(
                        s.artUri,
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .shadow(32.dp, RoundedCornerShape(28.dp), spotColor = Violet),
                        RoundedCornerShape(28.dp),
                        s.album
                    )
                }
            }

            // Title + like
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        s.title, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        s.artist, color = TextSecondary, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                val isLiked = s.id in liked
                IconButton(onClick = { vm.toggleLike(s) }) {
                    Icon(
                        if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        "Like",
                        tint = if (isLiked) PinkSoft else TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Seek bar
            Slider(
                value = sliderValue,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { vm.seekTo((it * safeDur).toLong()) }
                    dragging = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(
                    ((dragging?.let { it * safeDur })?.toLong() ?: pos).asClock(),
                    color = TextSecondary, fontSize = 12.sp
                )
                Spacer(Modifier.weight(1f))
                Text(dur.asClock(), color = TextSecondary, fontSize = 12.sp)
            }

            // Transport
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.toggleShuffle() }) {
                    Icon(
                        Icons.Rounded.Shuffle, "Shuffle",
                        tint = if (shuffle) Accent else TextSecondary
                    )
                }
                IconButton(onClick = { vm.previous() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous", tint = TextPrimary, modifier = Modifier.size(38.dp))
                }
                GradientCircleButton(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    72.dp, { vm.togglePlay() }, 40.dp
                )
                IconButton(onClick = { vm.next() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Next", tint = TextPrimary, modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = { vm.cycleRepeat() }) {
                    Icon(
                        if (repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Repeat",
                        tint = if (repeat == Player.REPEAT_MODE_OFF) TextSecondary else Accent
                    )
                }
            }

            // Bottom actions
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { showSleep = true }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Bedtime, "Sleep timer",
                        tint = if (sleep > 0) Accent else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    if (sleep > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text("${sleep}m", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (s.remote) {
                        val dlState = downloads[s.id.removePrefix("yt_")]
                        when (dlState) {
                            DlState.Downloading -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp)
                                )
                            }
                            DlState.Done -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.CheckCircle, "Downloaded", tint = Accent)
                            }
                            else -> IconButton(onClick = { vm.download(s) }) {
                                Icon(Icons.Rounded.Download, "Download", tint = TextSecondary)
                            }
                        }
                    }
                    IconButton(onClick = { showPlaylist = true }) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist", tint = TextSecondary)
                    }
                    IconButton(onClick = { showQueue = !showQueue }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.QueueMusic, "Queue",
                            tint = if (showQueue) Accent else TextSecondary
                        )
                    }
                }
            }
        }
    }

    if (showSleep) SleepTimerDialog(sleep, { vm.setSleepTimer(it) }) { showSleep = false }
    if (showPlaylist) AddToPlaylistDialog(vm, s) { showPlaylist = false }
}
