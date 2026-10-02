package com.nesimi.baglamaarsivi

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesimi.baglamaarsivi.data.AppThemeMode
import com.nesimi.baglamaarsivi.ui.MainViewModel
import com.nesimi.baglamaarsivi.ui.Screen
import com.nesimi.baglamaarsivi.ui.components.BusyOverlay
import com.nesimi.baglamaarsivi.ui.screens.DocumentsScreen
import com.nesimi.baglamaarsivi.ui.screens.FavoritesScreen
import com.nesimi.baglamaarsivi.ui.screens.HomeScreen
import com.nesimi.baglamaarsivi.ui.screens.ImportScreen
import com.nesimi.baglamaarsivi.ui.screens.PlayerScreen
import com.nesimi.baglamaarsivi.ui.screens.PracticeScreen
import com.nesimi.baglamaarsivi.ui.screens.SearchScreen
import com.nesimi.baglamaarsivi.ui.screens.SettingsScreen
import com.nesimi.baglamaarsivi.ui.screens.StorageScreen
import com.nesimi.baglamaarsivi.ui.screens.TrashScreen
import com.nesimi.baglamaarsivi.ui.screens.TurkuDetailScreen
import com.nesimi.baglamaarsivi.ui.screens.TurkuListScreen
import com.nesimi.baglamaarsivi.ui.theme.BaglamaTheme

private enum class Tab(val title: String, val on: ImageVector, val off: ImageVector, val screen: Screen) {
    HOME("Ana Sayfa", Icons.Filled.Home, Icons.Outlined.Home, Screen.Home),
    TURKU("Türküler", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic, Screen.TurkuList),
    PRACTICE("Çalış", Icons.Filled.Timer, Icons.Outlined.Timer, Screen.Practice),
    DOCS("Belgeler", Icons.Filled.Description, Icons.Outlined.Description, Screen.Documents),
    SETTINGS("Ayarlar", Icons.Filled.Settings, Icons.Outlined.Settings, Screen.Settings)
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            val theme by vm.theme.collectAsStateWithLifecycle()
            val dark = when (theme) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
            }
            BaglamaTheme(dark) { AppRoot(vm) }
        }
    }

    override fun onStart() {
        super.onStart()
        // Movies/Bağlama Arşivi klasörüne elle konan videoları ve silinenleri yakala
        vm.syncFolder()
        com.nesimi.baglamaarsivi.util.VideoStore.checkTick.value++
    }

    override fun onPause() {
        super.onPause()
        com.nesimi.baglamaarsivi.ui.screens.PlaybackGate.pause.tryEmit(Unit)
    }

    override fun onStop() {
        super.onStop()
        com.nesimi.baglamaarsivi.ui.screens.PlaybackGate.pause.tryEmit(Unit)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        com.nesimi.baglamaarsivi.ui.screens.PlaybackGate.pause.tryEmit(Unit)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    @Suppress("DEPRECATION")
    private fun handleShare(intent: Intent?) {
        intent ?: return
        val type = intent.type ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> {
                val u: Uri? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_STREAM)
                listOfNotNull(u ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val l: ArrayList<Uri>? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                l?.toList() ?: emptyList()
            }
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        val isVideo = type.startsWith("video/") || uris.all { (contentResolver.getType(it) ?: "").startsWith("video/") }
        vm.navigate(Screen.Import(uris = uris.take(60), isVideo = isVideo))
        intent.action = null
    }
}

@Composable
private fun AppRoot(vm: MainViewModel) {
    val stack by vm.stack.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val restoreDone by vm.restoreDone.collectAsStateWithLifecycle()
    val screen = stack.last()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    // Klasöre elle konan videoları görebilmek için bir kez video okuma izni iste
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.syncFolder(force = false) }
    LaunchedEffect(Unit) {
        val prefs = ctx.getSharedPreferences("baglama_arsivi_ayarlar", android.content.Context.MODE_PRIVATE)
        if (com.nesimi.baglamaarsivi.util.VideoStore.galleryEnabled &&
            !com.nesimi.baglamaarsivi.util.VideoStore.hasReadPermission(ctx) &&
            !prefs.getBoolean("video_izni_soruldu", false)
        ) {
            prefs.edit().putBoolean("video_izni_soruldu", true).apply()
            permLauncher.launch(if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_VIDEO else android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    BackHandler(enabled = stack.size > 1) { vm.back() }

    val tab = when (screen) {
        Screen.Home -> Tab.HOME
        Screen.TurkuList -> Tab.TURKU
        Screen.Practice -> Tab.PRACTICE
        Screen.Documents -> Tab.DOCS
        Screen.Settings -> Tab.SETTINGS
        else -> null
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (tab != null) {
                    NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = t == tab,
                                onClick = { vm.switchTab(t.screen) },
                                icon = { Icon(if (t == tab) t.on else t.off, t.title) },
                                label = { Text(t.title, fontSize = 11.sp, fontWeight = if (t == tab) FontWeight.Bold else FontWeight.Normal) }
                            )
                        }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(if (screen is Screen.Player) androidx.compose.foundation.layout.PaddingValues() else pad)) {
                when (screen) {
                    Screen.Home -> HomeScreen(vm)
                    Screen.TurkuList -> TurkuListScreen(vm)
                    Screen.Practice -> PracticeScreen(vm)
                    Screen.Documents -> DocumentsScreen(vm, turkuFilter = null)
                    Screen.Settings -> SettingsScreen(vm)
                    Screen.Favorites -> FavoritesScreen(vm)
                    Screen.Trash -> TrashScreen(vm)
                    Screen.Storage -> StorageScreen(vm)
                    Screen.Search -> SearchScreen(vm)
                    is Screen.TurkuDetail -> TurkuDetailScreen(vm, screen.turkuId)
                    is Screen.Player -> PlayerScreen(vm, screen.videoId)
                    is Screen.Import -> ImportScreen(vm, screen)
                }
            }
        }
        com.nesimi.baglamaarsivi.ui.OriginalsHandler(vm)
        com.nesimi.baglamaarsivi.ui.screens.PracticeFinishDialog(vm)
        busy?.let { BusyOverlay(it) }
        restoreDone?.let { msg ->
            val ctx = androidx.compose.ui.platform.LocalContext.current
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Geri yükleme tamamlandı ✅") },
                text = { Text(msg) },
                confirmButton = { TextButton(onClick = { RestartActivity.restart(ctx) }) { Text("Yeniden Başlat", fontWeight = FontWeight.Bold) } }
            )
        }
    }
}
