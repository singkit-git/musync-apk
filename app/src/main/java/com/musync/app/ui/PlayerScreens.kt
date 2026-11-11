package com.musync.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.musync.app.DlState
import com.musync.app.PlayerViewModel
import com.musync.app.SongLyrics
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
    val lyrics by vm.currentLyrics.collectAsStateWithLifecycle()
    val lyricsLoading by vm.lyricsLoading.collectAsStateWithLifecycle()

    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showPlaylist by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val s = song ?: return
    val safeDur = dur.coerceAtLeast(1L)
    val sliderValue = dragging ?: (pos.toFloat() / safeDur).coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
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
                    when {
                        showLyrics -> "LYRICS"
                        showQueue -> "UP NEXT"
                        else -> "NOW PLAYING"
                    },
                    color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.width(48.dp))
            }

            // Artwork, lyrics, or queue
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    showLyrics -> {
                        LyricsCard(
                            lyrics = lyrics,
                            loading = lyricsLoading,
                            currentPos = pos,
                            artUri = s.artUri,
                            albumSeed = s.album,
                            onSeek = { vm.seekTo(it) }
                        )
                    }
                    showQueue -> {
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
                    }
                    else -> {
                        Artwork(
                            s.artUri,
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .shadow(32.dp, RoundedCornerShape(28.dp), spotColor = Violet)
                                .clickable { showLyrics = true },
                            RoundedCornerShape(28.dp),
                            s.album
                        )
                    }
                }
            }

            // Title + like
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                ) {
                    Text(
                        s.title,
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        s.artist,
                        color = TextSecondary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                    IconButton(onClick = {
                        showLyrics = !showLyrics
                        if (showLyrics) showQueue = false
                    }) {
                        Icon(
                            Icons.Rounded.Lyrics, "Lyrics",
                            tint = if (showLyrics) Accent else TextSecondary
                        )
                    }
                    IconButton(onClick = { showPlaylist = true }) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist", tint = TextSecondary)
                    }
                    IconButton(onClick = {
                        showQueue = !showQueue
                        if (showQueue) showLyrics = false
                    }) {
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

@Composable
fun LyricsCard(
    lyrics: SongLyrics?,
    loading: Boolean,
    currentPos: Long,
    artUri: String?,
    albumSeed: String,
    onSeek: (Long) -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .background(Surface2)
    ) {
        // 1. Dynamic Album Art Glow Layer (Apple Music style)
        if (artUri != null) {
            AsyncImage(
                model = artUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 55.dp)
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(placeholderBrush(albumSeed))
                    .blur(radius = 55.dp)
            )
        }

        // 2. Dark contrast tint so lyrics are always super readable & sharp
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.62f),
                            Color.Black.copy(alpha = 0.50f),
                            Color.Black.copy(alpha = 0.70f)
                        )
                    )
                )
        )

        // 3. Lyrics Content
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                loading && lyrics == null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Accent,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.height(14.dp))
                        Text("Finding lyrics…", color = TextSecondary, fontSize = 14.sp)
                    }
                }
                lyrics == null || (lyrics.lines.isEmpty() && lyrics.plainLyrics.isNullOrBlank() && !lyrics.isInstrumental) -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.Lyrics,
                            contentDescription = null,
                            tint = TextSecondary.copy(alpha = 0.45f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No lyrics available",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Lyrics could not be found for this track",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                lyrics.isInstrumental -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("♪", color = Accent, fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Instrumental Track",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("No vocals in this song", color = TextSecondary, fontSize = 13.sp)
                    }
                }
                lyrics.isSynced -> {
                    val listState = rememberLazyListState()
                    val activeIndex = remember(lyrics, currentPos) {
                        lyrics.lines.indexOfLast { it.timeMs <= currentPos + 180L }.coerceAtLeast(0)
                    }

                    // Auto-center the active line smoothly
                    LaunchedEffect(activeIndex) {
                        if (activeIndex in lyrics.lines.indices) {
                            listState.animateScrollToItem(
                                index = activeIndex,
                                scrollOffset = -220
                            )
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 80.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        itemsIndexed(lyrics.lines) { index, line ->
                            val isCurrent = index == activeIndex
                            val alpha by animateFloatAsState(
                                targetValue = if (isCurrent) 1.0f else 0.32f,
                                animationSpec = tween(280),
                                label = "alpha"
                            )
                            val scale by animateFloatAsState(
                                targetValue = if (isCurrent) 1.05f else 0.96f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "scale"
                            )

                            Text(
                                text = line.text,
                                color = if (isCurrent) Color.White else TextSecondary.copy(alpha = 0.85f),
                                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                                fontSize = if (isCurrent) 23.sp else 16.sp,
                                lineHeight = if (isCurrent) 31.sp else 23.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        this.scaleX = scale
                                        this.scaleY = scale
                                        this.alpha = alpha
                                        this.transformOrigin = TransformOrigin(0f, 0.5f)
                                    }
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSeek(line.timeMs) }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            )
                        }
                    }
                }
                else -> {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(vertical = 24.dp, horizontal = 8.dp)
                    ) {
                        Text(
                            text = lyrics.plainLyrics ?: "",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            lineHeight = 25.sp
                        )
                    }
                }
            }
        }

        // 4. Cinematic Top & Bottom Edge Fade (Vignette)
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.70f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.75f)
                        )
                    )
                )
        )
    }
}
