package com.musync.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musync.app.PlayerViewModel

@Composable
fun SettingsScreen(vm: PlayerViewModel) {
    val store = vm.store
    val savedKey by store.serverUrl.collectAsStateWithLifecycle()
    val hideShort by store.hideShort.collectAsStateWithLifecycle()
    val sleep by vm.sleepMinutes.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val checkingUpdate by vm.checkingUpdate.collectAsStateWithLifecycle()

    var key by remember(savedKey) { mutableStateOf(savedKey) }
    var showSleep by remember { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("Settings")

        Group("Search & Downloads") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Key, null, tint = Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Custom Backend Server (Optional)", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Leave blank to search and download directly via YouTube. Or enter your custom yt-dlp backend (e.g. http://192.168.1.10:8000).",
                color = TextSecondary, fontSize = 13.sp
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                singleLine = true,
                placeholder = { Text("http://host:8000", color = TextSecondary) },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (key.trim() != savedKey) Color.Transparent else Surface3)
                    .then(
                        if (key.trim() != savedKey) Modifier.background(AccentBrush) else Modifier
                    )
                    .clickable(enabled = key.trim() != savedKey) { store.setServerUrl(key.trim()) }
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Text(
                    if (key.trim() != savedKey) "Save" else "Saved",
                    color = if (key.trim() != savedKey) Color.White else TextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Group("Library") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tune, null, tint = Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Hide short clips", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Skip audio under 30 seconds, like ringtones",
                        color = TextSecondary, fontSize = 13.sp
                    )
                }
                Switch(
                    checked = hideShort,
                    onCheckedChange = { store.setHideShort(it); vm.refreshLibrary() },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Violet,
                        checkedThumbColor = Color.White,
                        uncheckedTrackColor = Surface3,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { vm.refreshLibrary() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Refresh, null, tint = Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Rescan library", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("${songs.size} songs found", color = TextSecondary, fontSize = 13.sp)
                }
            }
        }

        Group("Playback") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showSleep = true }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Bedtime, null, tint = Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Sleep timer", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (sleep > 0) "Stops in $sleep minutes" else "Off",
                        color = TextSecondary, fontSize = 13.sp
                    )
                }
            }
        }

        Group("Support") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showSupport = true }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Favorite, null, tint = Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Buy the Dev a Coffee ☕",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Support Musync via GCash or Maya QR",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Group("About") {
            Text("Musync 1.2.0", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                "Your music, in sync. Powered by on-device YouTube extraction.",
                color = TextSecondary, fontSize = 13.sp
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !checkingUpdate) { vm.checkForUpdates(silent = false) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Refresh, null, tint = Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        if (checkingUpdate) "Checking for updates…" else "Check for updates",
                        color = TextPrimary, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "github.com/singkit-git/musync-apk",
                        color = TextSecondary, fontSize = 13.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showSleep) SleepTimerDialog(sleep, { vm.setSleepTimer(it) }) { showSleep = false }
    if (showSupport) SupportDevDialog { showSupport = false }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title.uppercase(),
        color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
    )
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface2)
            .padding(18.dp),
        content = content
    )
}
