package com.nesimi.baglamaarsivi.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.nesimi.baglamaarsivi.R
import com.nesimi.baglamaarsivi.data.VideoItem
import com.nesimi.baglamaarsivi.data.VideoMarker
import com.nesimi.baglamaarsivi.ui.MainViewModel
import com.nesimi.baglamaarsivi.ui.Screen
import com.nesimi.baglamaarsivi.ui.components.OrderTagDialog
import com.nesimi.baglamaarsivi.ui.components.VideoCard
import com.nesimi.baglamaarsivi.ui.theme.FavGold
import com.nesimi.baglamaarsivi.util.FileManager
import com.nesimi.baglamaarsivi.util.Tr
import kotlinx.coroutines.delay

private fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private val SPEEDS = listOf(0.25f, 0.5f, 0.6f, 0.7f, 0.75f, 0.8f, 0.9f, 1.0f, 1.25f, 1.5f, 2.0f)

private fun speedLabel(s: Float): String = (if (s == s.toInt().toFloat()) s.toInt().toString() else s.toString().trimEnd('0')) + "x"

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: MainViewModel, startVideoId: Long) {
    val context = LocalContext.current
    val activity = remember { context.findActivity() }
    var videoId by remember { mutableLongStateOf(startVideoId) }
    val video by remember(videoId) { vm.videoFlow(videoId) }.collectAsState(initial = null)
    val v = video
    val turkuId = v?.turkuId ?: -1L
    val playlist by remember(turkuId) { vm.videosFor(turkuId) }.collectAsState(initial = emptyList())
    val markers by remember(videoId) { vm.markersFor(videoId) }.collectAsState(initial = emptyList())
    val turkus by vm.turkus.collectAsState()
    val turkuName = turkus.firstOrNull { it.turku.id == turkuId }?.turku?.name

    val player = remember {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            // Başka uygulama ses çalarsa (ör. arama, müzik) otomatik duraklar; kulaklık çıkınca durur
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var mirror by remember { mutableStateOf(false) }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val fullscreen = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    // Tam ekran düğmesine basınca yön kilitlenir; telefon o yöne çevrilince kilit kalkar (otomatik dönme devam eder)
    var lockedTo by remember { mutableIntStateOf(0) } // 0 yok, 1 dikey, 2 yatay
    var swipeDx by remember { mutableFloatStateOf(0f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var loopA by remember { mutableLongStateOf(-1L) }
    var loopB by remember { mutableLongStateOf(-1L) }
    var loopOn by remember { mutableStateOf(false) }
    var loopCount by remember { mutableIntStateOf(0) }
    var autoNext by remember { mutableStateOf(true) }
    var seeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }
    var loadedId by remember { mutableLongStateOf(-1L) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var relinkFor by remember { mutableStateOf<VideoItem?>(null) }
    val relinkLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> val target = relinkFor; if (uri != null && target != null) { loadedId = -1; vm.relinkVideo(target, uri) }; relinkFor = null }
    var fileMissing by remember { mutableStateOf(false) }
    var markerDialog by remember { mutableStateOf<VideoMarker?>(null) }
    var addMarkerAt by remember { mutableLongStateOf(-1L) }
    var orderDialog by remember { mutableStateOf(false) }
    var editDialog by remember { mutableStateOf(false) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    val currentIndex = playlist.indexOfFirst { it.id == videoId }
    val next = playlist.getOrNull(currentIndex + 1)
    val prev = if (currentIndex > 0) playlist.getOrNull(currentIndex - 1) else null

    fun saveProgress() {
        val pos = player.currentPosition
        if (loadedId > 0) {
            val d = player.duration
            val save = if (d > 0 && pos > d - 3000) 0L else pos
            vm.savePosition(loadedId, save)
        }
    }

    fun switchTo(target: VideoItem) {
        saveProgress()
        loopA = -1; loopB = -1; loopOn = false; loopCount = 0
        videoId = target.id
    }

    // Video yükle
    var loadedPath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(v?.id, v?.localPath, reloadKey) {
        val vv = v ?: return@LaunchedEffect
        if (loadedId == vv.id && loadedPath == vv.localPath) {
            // Aynı video: sadece dosya hâlâ duruyor mu kontrol et
            fileMissing = !com.nesimi.baglamaarsivi.util.VideoStore.exists(context, vv.localPath)
            if (fileMissing) player.pause()
            return@LaunchedEffect
        }
        val playUri = com.nesimi.baglamaarsivi.util.VideoStore.uri(context, vv.localPath)
        if (playUri == null) { fileMissing = true; player.stop(); player.clearMediaItems(); loadedId = vv.id; loadedPath = vv.localPath; return@LaunchedEffect }
        fileMissing = false
        player.setMediaItem(MediaItem.fromUri(playUri))
        player.prepare()
        val resume = vv.lastPlaybackPositionMs
        if (resume > 3000 && (vv.durationMs == 0L || resume < vv.durationMs - 3000)) player.seekTo(resume)
        player.playbackParameters = PlaybackParameters(speed)
        player.playWhenReady = true
        loadedId = vv.id
        loadedPath = vv.localPath
    }

    // Oynatıcı olayları
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    duration = player.duration.coerceAtLeast(0)
                    val cur = video
                    if (cur != null && cur.durationMs <= 0 && duration > 0) vm.saveDuration(cur.id, duration)
                }
                if (state == Player.STATE_ENDED) {
                    if (loadedId > 0) vm.savePosition(loadedId, 0)
                    val n = playlist.getOrNull(playlist.indexOfFirst { it.id == videoId } + 1)
                    if (autoNext && n != null && !loopOn) switchTo(n)
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Konum takibi + A-B döngüsü
    LaunchedEffect(player) {
        var tick = 0
        while (true) {
            position = player.currentPosition
            if (player.duration > 0) duration = player.duration
            if (loopOn && loopA >= 0 && loopB > loopA && position >= loopB) {
                player.seekTo(loopA)
                loopCount++
            }
            tick++
            if (tick % 100 == 0 && player.isPlaying) saveProgress() // ~5 sn
            delay(50)
        }
    }

    // Kontrolleri otomatik gizle
    LaunchedEffect(controlsVisible, isPlaying, interaction, seeking) {
        if (controlsVisible && isPlaying && !seeking) {
            delay(3500)
            controlsVisible = false
        }
    }

    // Yan çevirince otomatik tam ekran: sistem çubuklarını gizle
    LaunchedEffect(fullscreen) {
        val act = activity ?: return@LaunchedEffect
        val ctrl = WindowCompat.getInsetsController(act.window, act.window.decorView)
        if (fullscreen) {
            ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            ctrl.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(lockedTo) {
        activity?.requestedOrientation = when (lockedTo) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    DisposableEffect(Unit) {
        val listener = object : android.view.OrientationEventListener(context) {
            override fun onOrientationChanged(angle: Int) {
                if (angle < 0 || lockedTo == 0) return
                val physPortrait = angle < 25 || angle > 335 || angle in 155..205
                val physLandscape = angle in 65..115 || angle in 245..295
                if ((lockedTo == 1 && physPortrait) || (lockedTo == 2 && physLandscape)) lockedTo = 0
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }

    LaunchedEffect(mirror, playerView) { playerView?.videoSurfaceView?.scaleX = if (mirror) -1f else 1f }

    // Başka ekrana / uygulamaya geçince duraklat; geri gelince kullanıcı dokunana kadar bekle
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    if (player.isPlaying || player.playWhenReady) {
                        player.pause()
                        saveProgress()
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    controlsVisible = true
                    if (fileMissing || loadedId > 0) reloadKey++ // dosya bu arada silinmiş/yeniden bağlanmış olabilir
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    DisposableEffect(Unit) {
        onDispose {
            saveProgress()
            player.release()
            activity?.let {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(it.window, it.window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    fun setSpeed(s: Float) { speed = s; player.playbackParameters = PlaybackParameters(s) }
    fun poke() { controlsVisible = true; interaction++ }

    // ---------------------------------------------------------------- Video alanı
    val videoBox: @Composable (Modifier) -> Unit = { mod ->
        BoxWithConstraints(mod.background(Color.Black)) {
            val w = constraints.maxWidth
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView).also {
                        it.player = player
                        playerView = it
                    }
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(w, prev?.id, next?.id) {
                        detectHorizontalDragGestures(
                            onDragStart = { swipeDx = 0f },
                            onDragCancel = { swipeDx = 0f },
                            onDragEnd = {
                                val threshold = w * 0.18f
                                if (swipeDx < -threshold && next != null) switchTo(next)
                                else if (swipeDx > threshold && prev != null) switchTo(prev)
                                swipeDx = 0f
                            },
                            onHorizontalDrag = { change, dx -> change.consume(); swipeDx += dx }
                        )
                    }
                    .pointerInput(w) {
                    detectTapGestures(
                        onTap = { if (controlsVisible) controlsVisible = false else poke() },
                        onDoubleTap = { off ->
                            if (off.x < w / 2f) player.seekBack() else player.seekForward()
                            poke()
                        }
                    )
                }
            )
            if (kotlin.math.abs(swipeDx) > w * 0.06f) {
                val goingNext = swipeDx < 0
                val target = if (goingNext) next else prev
                Text(
                    if (target == null) (if (goingNext) "Son video" else "İlk video") else if (goingNext) "Sonraki ▶" else "◀ Önceki",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    modifier = Modifier.align(if (goingNext) Alignment.CenterEnd else Alignment.CenterStart).padding(24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            if (fileMissing) {
                Column(
                    Modifier.align(Alignment.Center).fillMaxWidth().background(Color.Black.copy(alpha = 0.85f)).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("⚠️ Bu videonun dosyası telefondan silinmiş.", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Arşivden kaldırabilir ya da başka bir video dosyasına bağlayabilirsin.", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        androidx.compose.material3.OutlinedButton(onClick = {
                            v?.let { gone ->
                                val n = next ?: prev
                                vm.removeMissingVideo(gone)
                                if (n != null) switchTo(n) else vm.back()
                            }
                        }) { Text("Arşivden kaldır", color = Color.White) }
                        androidx.compose.material3.Button(onClick = { relinkFor = v; relinkLauncher.launch(arrayOf("video/*")) }) { Text("Dosya seç ve bağla") }
                    }
                }
            }
            if (controlsVisible) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
                    Row(Modifier.fillMaxWidth().align(Alignment.TopStart).then(if (fullscreen) Modifier else Modifier.statusBarsPadding()), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (fullscreen) lockedTo = 1 else vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Color.White) }
                        Text(v?.title ?: "", color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { mirror = !mirror; poke() }) { Icon(Icons.Default.Flip, "Ayna", tint = if (mirror) FavGold else Color.White) }
                        IconButton(onClick = { lockedTo = if (fullscreen) 1 else 2; poke() }) {
                            Icon(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Tam ekran", tint = Color.White)
                        }
                    }
                    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        IconButton(onClick = { prev?.let { switchTo(it) }; poke() }, enabled = prev != null) { Icon(Icons.Default.SkipPrevious, "Önceki", tint = if (prev != null) Color.White else Color.Gray) }
                        IconButton(onClick = { player.seekBack(); poke() }) { Icon(Icons.Default.Replay5, "5 sn geri", tint = Color.White, modifier = Modifier.size(34.dp)) }
                        Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(62.dp)) {
                            IconButton(onClick = {
                                if (player.isPlaying) player.pause() else {
                                    if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                                    player.play()
                                }
                                poke()
                            }) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Oynat", tint = Color.Black, modifier = Modifier.size(38.dp)) }
                        }
                        IconButton(onClick = { player.seekForward(); poke() }) { Icon(Icons.Default.Forward5, "5 sn ileri", tint = Color.White, modifier = Modifier.size(34.dp)) }
                        IconButton(onClick = { next?.let { switchTo(it) }; poke() }, enabled = next != null) { Icon(Icons.Default.SkipNext, "Sonraki", tint = if (next != null) Color.White else Color.Gray) }
                    }
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Box(Modifier.fillMaxWidth()) {
                            if (duration > 0 && loopA >= 0) LoopMark(loopA.toFloat() / duration, "A")
                            if (duration > 0 && loopB >= 0) LoopMark(loopB.toFloat() / duration, "B")
                        }
                        Slider(
                            value = if (seeking) seekValue else if (duration > 0) position.toFloat() / duration else 0f,
                            onValueChange = { seeking = true; seekValue = it; poke() },
                            onValueChangeFinished = { player.seekTo((seekValue * duration).toLong()); seeking = false },
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = Color.White.copy(alpha = 0.35f)),
                            modifier = Modifier.height(28.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${Tr.duration(if (seeking) (seekValue * duration).toLong() else position)} / ${Tr.duration(duration)}", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            if (loopOn) Text("🔁 ${loopCount}", color = FavGold, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                            Text(speedLabel(speed), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                modifier = Modifier.background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
                                    .pointerInput(speed) { detectTapGestures { val i = SPEEDS.indexOf(speed); setSpeed(SPEEDS[(i + 1).mod(SPEEDS.size)]); poke() } })
                        }
                    }
                }
            }
        }
    }

    if (fullscreen) {
        videoBox(Modifier.fillMaxSize())
    } else {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            videoBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Başlık
                item {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(v?.title ?: "", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            val info = listOfNotNull(
                                v?.displayOrderTag?.takeIf { it.isNotBlank() }?.let { "#$it" },
                                v?.lessonDate?.takeIf { it.isNotBlank() }?.let { "📅 $it" },
                                v?.instructor?.takeIf { it.isNotBlank() }?.let { "👤 $it" },
                                v?.fileSize?.takeIf { it > 0 }?.let { Tr.size(it) }
                            ).joinToString("  ")
                            Text(info, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (turkuName != null) {
                                Text("🎵 $turkuName →", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 2.dp).pointerInput(turkuId) { detectTapGestures { vm.navigate(Screen.TurkuDetail(turkuId)) } })
                            }
                        }
                        if (v != null) {
                            IconButton(onClick = { vm.toggleVideoFavorite(v) }) {
                                Icon(if (v.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder, "Favori", tint = if (v.isFavorite) FavGold else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { editDialog = true }) { Icon(Icons.Default.Edit, "Düzenle") }
                        }
                    }
                }
                // Hız
                item {
                    Text("Çalışma hızı (ses perdesi korunur)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(SPEEDS) { s -> FilterChip(selected = s == speed, onClick = { setSpeed(s) }, label = { Text(speedLabel(s)) }) }
                    }
                }
                // A-B döngü
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Repeat, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text("Bölüm tekrarı (A-B)", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Switch(checked = loopOn, enabled = loopA >= 0 && loopB > loopA, onCheckedChange = {
                                    loopOn = it; loopCount = 0
                                    if (it) player.seekTo(loopA)
                                })
                            }
                            Text("Zor bir pasajı sürekli tekrarlat: başlangıçta A'ya, bitişte B'ye bas.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                                AssistChip(onClick = { loopA = player.currentPosition; if (loopB in 0..loopA) loopB = -1 }, label = { Text(if (loopA >= 0) "A: ${Tr.duration(loopA)}" else "A noktası") })
                                AssistChip(onClick = {
                                    val p = player.currentPosition
                                    if (loopA in 0 until p) { loopB = p; loopOn = true; loopCount = 0; player.seekTo(loopA) } else vm.toast("Önce A noktasını seç")
                                }, label = { Text(if (loopB >= 0) "B: ${Tr.duration(loopB)}" else "B noktası") })
                                if (loopA >= 0) AssistChip(onClick = { loopA = -1; loopB = -1; loopOn = false }, label = { Text("Temizle") })
                            }
                            if (loopA >= 0 && loopB > loopA) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AssistChip(onClick = { loopA = (loopA - 1000).coerceAtLeast(0) }, label = { Text("A −1sn") })
                                    AssistChip(onClick = { loopA = (loopA + 1000).coerceAtMost(loopB - 500) }, label = { Text("A +1sn") })
                                    AssistChip(onClick = { loopB = (loopB - 1000).coerceAtLeast(loopA + 500) }, label = { Text("B −1sn") })
                                    AssistChip(onClick = { loopB = (loopB + 1000).coerceAtMost(duration) }, label = { Text("B +1sn") })
                                }
                            }
                        }
                    }
                }
                // İşaretler
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📌 İşaretler (${markers.size})", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { addMarkerAt = player.currentPosition }) {
                            Icon(Icons.Default.BookmarkAdd, null); Spacer(Modifier.width(4.dp)); Text("Buraya işaret koy")
                        }
                    }
                }
                items(markers, key = { "m" + it.id }) { m ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(Tr.duration(m.positionMs), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.pointerInput(m.id) { detectTapGestures { player.seekTo(m.positionMs); player.play() } })
                            Spacer(Modifier.width(10.dp))
                            Text(m.label, modifier = Modifier.weight(1f).pointerInput(m.id) { detectTapGestures { player.seekTo(m.positionMs); player.play() } }, maxLines = 2)
                            TextButton(onClick = { loopA = m.positionMs; if (loopB in 0..loopA) loopB = -1 }) { Text("A") }
                            IconButton(onClick = { markerDialog = m }) { Icon(Icons.Default.Edit, "Düzenle") }
                            IconButton(onClick = { vm.deleteMarker(m.id) }) { Icon(Icons.Default.Delete, "Sil") }
                        }
                    }
                }
                if (v != null && (v.description.isNotBlank() || v.tags.isNotBlank())) {
                    item {
                        Text("Açıklama", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                        if (v.description.isNotBlank()) Text(v.description, fontSize = 14.sp)
                        if (v.tags.isNotBlank()) Text(Tr.splitTags(v.tags).joinToString("  ") { "#$it" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                // Sıradaki videolar
                if (playlist.size > 1) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Text("🎬 Bu türkünün videoları (${currentIndex + 1}/${playlist.size})", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("Otomatik geç", fontSize = 12.sp)
                            Switch(checked = autoNext, onCheckedChange = { autoNext = it }, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    items(playlist, key = { "p" + it.id }) { pv ->
                        VideoCard(pv, onClick = { if (pv.id != videoId) switchTo(pv) }, isPlaying = pv.id == videoId,
                            modifier = Modifier.animateItem(),
                            onOrderChange = { vm.setVideoOrderTag(pv.id, it) }, orderSuggestion = nextOrderSuggestion(playlist))
                    }
                }
                item { Spacer(Modifier.height(30.dp)) }
            }
        }
    }

    if (addMarkerAt >= 0) {
        var label by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addMarkerAt = -1 },
            title = { Text("İşaret: ${Tr.duration(addMarkerAt)}") },
            text = { OutlinedTextField(label, { label = it }, label = { Text("Not (ör. sol el geçişi)") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.addMarker(videoId, addMarkerAt, label.trim()); addMarkerAt = -1 }) { Text("Ekle") } },
            dismissButton = { TextButton(onClick = { addMarkerAt = -1 }) { Text("Vazgeç") } }
        )
    }
    markerDialog?.let { m ->
        var label by remember(m.id) { mutableStateOf(m.label) }
        AlertDialog(
            onDismissRequest = { markerDialog = null },
            title = { Text("İşareti düzenle") },
            text = { OutlinedTextField(label, { label = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.updateMarker(m.copy(label = label.trim().ifBlank { m.label })); markerDialog = null }) { Text("Kaydet") } },
            dismissButton = { TextButton(onClick = { markerDialog = null }) { Text("Vazgeç") } }
        )
    }
    if (orderDialog && v != null) OrderTagDialog(v.orderTag.ifBlank { v.displayOrderTag }, v.title, onSave = { vm.setVideoOrderTag(v.id, it) }, onDismiss = { orderDialog = false })
    if (editDialog && v != null) VideoEditDialog(vm, v, onDismiss = { editDialog = false })
}

@Composable
private fun LoopMark(fraction: Float, label: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val x = maxWidth * fraction.coerceIn(0f, 1f)
        Text(
            label, color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(x = x - 6.dp).background(FavGold, RoundedCornerShape(3.dp)).padding(horizontal = 3.dp)
        )
    }
}
