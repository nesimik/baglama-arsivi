package com.nesimi.baglamaarsivi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesimi.baglamaarsivi.ui.MainViewModel
import com.nesimi.baglamaarsivi.ui.components.SectionTitle
import com.nesimi.baglamaarsivi.ui.components.StatTile
import com.nesimi.baglamaarsivi.util.Tr
import kotlinx.coroutines.delay

private fun tempoName(bpm: Int) = when {
    bpm < 60 -> "Largo (çok ağır)"
    bpm < 76 -> "Adagio (ağır)"
    bpm < 108 -> "Andante / Moderato"
    bpm < 140 -> "Allegro (hızlı)"
    else -> "Presto (çok hızlı)"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(vm: MainViewModel) {
    val turkus by vm.turkus.collectAsStateWithLifecycle()
    val stats by vm.practiceStats.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val selTurku by vm.practiceTurku.collectAsStateWithLifecycle()
    val running by vm.metroRunning.collectAsStateWithLifecycle()
    val bpm by vm.metroBpm.collectAsStateWithLifecycle()
    val beats by vm.metroBeats.collectAsStateWithLifecycle()
    val beat by vm.metroBeat.collectAsStateWithLifecycle()
    var manual by remember { mutableStateOf(false) }
    val names = remember(turkus) { turkus.associate { it.turku.id to it.turku.name } }


    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Çalışma Köşesi", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(Tr.practice(stats.todaySec), "Bugün", Modifier.weight(1f))
                StatTile(Tr.practice(stats.weekSec), "Bu hafta", Modifier.weight(1f))
                StatTile("🔥 ${stats.streakDays}", "Gün seri", Modifier.weight(1f), color = Color(0xFFE0571B))
            }
        }
        // Süre tutucu (ileri / geri sayım)
        item { TimerCard(vm, names) }
        // Metronom
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🥁 Metronom", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        for (i in 0 until beats) {
                            val active = running && beat == i
                            Box(
                                Modifier.size(if (i == 0) 22.dp else 18.dp).clip(CircleShape)
                                    .background(if (active) (if (i == 0) Color(0xFFE0571B) else MaterialTheme.colorScheme.primary) else MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { vm.setBpm(bpm - 5) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("−5", fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(4.dp))
                        FilledIconButton(onClick = { vm.setBpm(bpm - 1) }) { Icon(Icons.Default.Remove, "1 azalt") }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 10.dp)) {
                            Text("$bpm", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                            Text("BPM", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledIconButton(onClick = { vm.setBpm(bpm + 1) }) { Icon(Icons.Default.Add, "1 artır") }
                        Spacer(Modifier.width(4.dp))
                        OutlinedButton(onClick = { vm.setBpm(bpm + 5) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("+5", fontWeight = FontWeight.Bold) }
                    }
                    Text(tempoName(bpm), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = bpm.toFloat(), onValueChange = { vm.setBpm(it.toInt()) }, valueRange = 30f..260f)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf(60, 70, 80, 90, 100, 120, 140)) { b -> FilterChip(selected = bpm == b, onClick = { vm.setBpm(b) }, label = { Text("$b") }) }
                    }
                    Text("Ölçü (vuruş sayısı)", fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf(2, 3, 4, 5, 6, 7, 9)) { n -> FilterChip(selected = beats == n, onClick = { vm.setBeats(n) }, label = { Text("$n/") }) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.toggleMetronome() }, modifier = Modifier.height(48.dp)) {
                            Icon(if (running) Icons.Default.Stop else Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp))
                            Text(if (running) "Durdur" else "Başlat", fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(onClick = { vm.tapTempo() }, modifier = Modifier.height(48.dp)) {
                            Icon(Icons.Default.TouchApp, null); Spacer(Modifier.width(4.dp)); Text("Tempoya dokun")
                        }
                    }
                    Text("Aksak ritimler için 5, 7 veya 9 vuruşu seç.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        // Geçmiş
        item { SectionTitle("📈 Son çalışmalar") }
        if (sessions.isEmpty()) item { Text("Henüz kayıt yok. Başlat'a basıp çalışmaya başla!", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(sessions.take(40), key = { it.id }) { s ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${Tr.practice(s.durationSec)} • ${s.turkuId?.let { names[it] } ?: "Genel çalışma"}", fontWeight = FontWeight.SemiBold)
                        Text(Tr.dateTime(s.startedAt) + if (s.note.isNotBlank()) " • ${s.note}" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.deletePractice(s.id) }) { Icon(Icons.Default.Delete, "Sil") }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (manual) {
        var minutes by remember { mutableStateOf("30") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { manual = false },
            title = { Text("Elle çalışma ekle") },
            text = {
                Column {
                    Text(selTurku?.let { names[it] } ?: "Genel çalışma", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(minutes, { minutes = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("Süre (dakika)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(note, { note = it }, label = { Text("Not (isteğe bağlı)") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { vm.addManualPractice(selTurku, minutes.toIntOrNull() ?: 0, note.trim()); manual = false }) { Text("Ekle") } },
            dismissButton = { TextButton(onClick = { manual = false }) { Text("Vazgeç") } }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimerCard(vm: MainViewModel, names: Map<Long, String>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val turkus by vm.turkus.collectAsStateWithLifecycle()
    val t by vm.timer.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var countdownMode by remember { mutableStateOf(true) }
    var minutes by remember { mutableIntStateOf(vm.lastCountdownMinutes) }
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(t.isRunning, t.runningSince) {
        while (t.isRunning) {
            now = System.currentTimeMillis()
            if (t.countdown && t.remaining(now) <= 0) { vm.timerExpired(); break }
            delay(250)
        }
        now = System.currentTimeMillis()
    }

    val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { vm.startPractice(t.turkuId, minutes) }

    fun startCountdown() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        else vm.startPractice(t.turkuId, minutes)
    }

    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val active = t.isActive && !t.finished
            if (!active) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = countdownMode, onClick = { countdownMode = true }, label = { Text("⏳ Geri sayım") })
                    FilterChip(selected = !countdownMode, onClick = { countdownMode = false }, label = { Text("⏱️ Serbest") })
                }
            } else {
                val head = if (t.countdown) "⏳ Geri sayım" else "⏱️ Çalışma süresi"
                val suffix = if (t.isRunning) "" else " (duraklatıldı)"
                Text(head + suffix, fontWeight = FontWeight.Bold)
            }
            val big = when {
                active && t.countdown -> Tr.duration(t.remaining(now))
                active -> Tr.duration(t.elapsed(now))
                countdownMode -> Tr.duration(minutes * 60_000L)
                else -> "00:00"
            }
            Text(big, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold)
            if (active && t.countdown) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { if (t.targetMs > 0) t.elapsed(now).toFloat() / t.targetMs else 0f },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                )
            }
            if (!active && countdownMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { minutes = (minutes - 5).coerceAtLeast(1) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("−5") }
                    IconButton(onClick = { minutes = (minutes - 1).coerceAtLeast(1) }) { Icon(Icons.Default.Remove, "1 dk azalt") }
                    Text("$minutes dk", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { minutes = (minutes + 1).coerceAtMost(240) }) { Icon(Icons.Default.Add, "1 dk artır") }
                    OutlinedButton(onClick = { minutes = (minutes + 5).coerceAtMost(240) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("+5") }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(5, 10, 15, 20, 30, 45, 60)) { m -> FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m dk") }) }
                }
            }
            Spacer(Modifier.height(6.dp))
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = t.turkuId?.let { names[it] } ?: "Genel çalışma (türkü seçilmedi)",
                    onValueChange = {}, readOnly = true, label = { Text("Ne çalışıyorsun?") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Genel çalışma") }, onClick = { vm.selectPracticeTurku(null); expanded = false })
                    turkus.forEach { tw -> DropdownMenuItem(text = { Text(tw.turku.name) }, onClick = { vm.selectPracticeTurku(tw.turku.id); expanded = false }) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    !active -> Button(onClick = { if (countdownMode) startCountdown() else vm.startPractice(t.turkuId, null) }, modifier = Modifier.height(48.dp)) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Başlat", fontWeight = FontWeight.Bold)
                    }
                    t.isRunning -> {
                        FilledTonalButton(onClick = { vm.pausePractice() }, modifier = Modifier.height(48.dp)) {
                            Icon(Icons.Default.Pause, null); Spacer(Modifier.width(4.dp)); Text("Duraklat")
                        }
                        Button(onClick = { vm.stopPractice() }, modifier = Modifier.height(48.dp)) {
                            Icon(Icons.Default.Stop, null); Spacer(Modifier.width(4.dp)); Text("Bitir")
                        }
                    }
                    else -> {
                        Button(onClick = { vm.resumePractice() }, modifier = Modifier.height(48.dp)) {
                            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Devam")
                        }
                        OutlinedButton(onClick = { vm.stopPractice() }, modifier = Modifier.height(48.dp)) {
                            Icon(Icons.Default.Stop, null); Spacer(Modifier.width(4.dp)); Text("Bitir")
                        }
                    }
                }
            }
            Text(
                if (countdownMode || t.countdown) "Süre dolunca alarm çalar (uygulama kapalı olsa da). Bitir'e basınca kaydedip kaydetmeyeceğin sorulur."
                else "Bitir'e basınca kaydedip kaydetmeyeceğin sorulur.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/** Sayaç bittiğinde (elle veya süre dolunca) her ekranda çıkan kaydet / kaydetme sorusu. */
@Composable
fun PracticeFinishDialog(vm: MainViewModel) {
    val t by vm.timer.collectAsStateWithLifecycle()
    val turkus by vm.turkus.collectAsStateWithLifecycle()
    if (!t.finished) return
    var note by remember { mutableStateOf("") }
    val name = t.turkuId?.let { id -> turkus.firstOrNull { it.turku.id == id }?.turku?.name } ?: "Genel çalışma"
    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (t.countdown) "⏰ Süre doldu!" else "Çalışma bitti") },
        text = {
            Column {
                Text("${Tr.practice(t.accumulatedMs / 1000)} • $name", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("Bu çalışmayı kayıtlarına eklemek ister misin?")
                OutlinedTextField(note, { note = it }, label = { Text("Not (isteğe bağlı)") }, singleLine = true, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { Button(onClick = { vm.savePractice(note.trim()) }) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = { vm.discardPractice() }) { Text("Kaydetme") } }
    )
}
