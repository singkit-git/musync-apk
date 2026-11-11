package com.musync.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musync.app.ui.Accent
import com.musync.app.ui.Bg
import com.musync.app.ui.ExploreScreen
import com.musync.app.ui.GlowBrush
import com.musync.app.ui.HomeScreen
import com.musync.app.ui.LibraryScreen
import com.musync.app.ui.MiniPlayer
import com.musync.app.ui.MusyncTheme
import com.musync.app.ui.NowPlayingScreen
import com.musync.app.ui.SettingsScreen
import com.musync.app.ui.Surface1
import com.musync.app.ui.Surface2
import com.musync.app.ui.TextPrimary
import com.musync.app.ui.TextSecondary
import com.musync.app.ui.UpdateDialog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent { MusyncTheme { MusyncRoot() } }
    }
}

private data class TabDef(val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabDef("Home", Icons.Rounded.Home),
    TabDef("Library", Icons.Rounded.LibraryMusic),
    TabDef("Explore", Icons.Rounded.Explore),
    TabDef("Settings", Icons.Rounded.Settings)
)

@Composable
private fun MusyncRoot() {
    val vm: PlayerViewModel = viewModel()
    val exploreVm: ExploreViewModel = viewModel()
    val ctx = LocalContext.current

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    val current by vm.current.collectAsStateWithLifecycle()
    val update by vm.availableUpdate.collectAsStateWithLifecycle()
    val updateProgress by vm.updatingProgress.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            snackbars.currentSnackbarData?.dismiss()
            snackbars.showSnackbar(msg)
        }
    }

    val needed = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refreshLibrary() }

    LaunchedEffect(Unit) {
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) launcher.launch(missing.toTypedArray()) else vm.refreshLibrary()
    }

    BackHandler(enabled = showPlayer) { showPlayer = false }

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(GlowBrush)
        )
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> HomeScreen(vm, onGoExplore = { tab = 2 })
                    1 -> LibraryScreen(vm)
                    2 -> ExploreScreen(vm, exploreVm)
                    else -> SettingsScreen(vm)
                }
                SnackbarHost(
                    hostState = snackbars,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = Surface2,
                        contentColor = TextPrimary,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }
            if (current != null) MiniPlayer(vm) { showPlayer = true }
            NavigationBar(containerColor = Surface1.copy(alpha = 0.96f), tonalElevation = 0.dp) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Accent,
                            selectedTextColor = Accent,
                            indicatorColor = Accent.copy(alpha = 0.16f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showPlayer && current != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            NowPlayingScreen(vm, onClose = { showPlayer = false })
        }

        update?.let { info ->
            UpdateDialog(
                info = info,
                progress = updateProgress,
                onConfirm = { vm.installUpdate(info) },
                onDismiss = { vm.dismissUpdate() }
            )
        }
    }
}
