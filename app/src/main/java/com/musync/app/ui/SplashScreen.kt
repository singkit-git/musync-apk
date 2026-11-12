package com.musync.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SplashScreen(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(400, easing = FastOutSlowInEasing)) +
                scaleOut(targetScale = 1.06f, animationSpec = tween(400, easing = FastOutSlowInEasing)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Bg)
        ) {
            // Ambient glowing background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Violet.copy(alpha = 0.22f),
                                Pink.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            radius = 650f
                        )
                    )
            )

            // Centered Logo + Equalizer + Title
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Waveform Equalizer Card
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Surface2)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EqualizerWaveform()
                }

                Spacer(Modifier.height(28.dp))

                // App Brand Name with AccentBrush
                Text(
                    text = "Musync",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        brush = AccentBrush,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Your music, in sync",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.3.sp
                )
            }

            // Bottom loading status / version
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PulsingDots()
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "v1.4.1",
                    color = TextSecondary.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun EqualizerWaveform() {
    val transition = rememberInfiniteTransition(label = "waveform")

    val h1 by transition.animateFloat(
        initialValue = 14f,
        targetValue = 36f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h1"
    )
    val h2 by transition.animateFloat(
        initialValue = 22f,
        targetValue = 54f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h2"
    )
    val h3 by transition.animateFloat(
        initialValue = 34f,
        targetValue = 68f,
        animationSpec = infiniteRepeatable(
            animation = tween(620, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h3"
    )
    val h4 by transition.animateFloat(
        initialValue = 20f,
        targetValue = 50f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h4"
    )
    val h5 by transition.animateFloat(
        initialValue = 14f,
        targetValue = 36f,
        animationSpec = infiniteRepeatable(
            animation = tween(580, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h5"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(72.dp)
    ) {
        WaveBar(heightDp = h1, color = Color(0xFF8B5CF6))
        WaveBar(heightDp = h2, color = Color(0xFFA78BFA))
        WaveBar(heightDp = h3, color = Color(0xFFF472B6))
        WaveBar(heightDp = h4, color = Color(0xFFEC4899))
        WaveBar(heightDp = h5, color = Color(0xFFC084FC))
    }
}

@Composable
private fun WaveBar(heightDp: Float, color: Color) {
    Box(
        modifier = Modifier
            .width(7.dp)
            .height(heightDp.dp)
            .clip(RoundedCornerShape(50))
            .background(color)
    )
}

@Composable
private fun PulsingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    val alpha1 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "d1"
    )
    val alpha2 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "d2"
    )
    val alpha3 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "d3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Accent.copy(alpha = alpha1))
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Accent.copy(alpha = alpha2))
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Accent.copy(alpha = alpha3))
        )
    }
}
