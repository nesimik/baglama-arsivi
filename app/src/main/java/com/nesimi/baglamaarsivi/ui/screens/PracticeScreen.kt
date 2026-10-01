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
    val start by vm.practiceStart.collectAsStateWithLifecycle()
    val selTurku by vm.practiceTurku.collectAsStateWithLifecycle()
    val running by vm.metroRunning.collectAsStateWithLifecycle()
    val bpm by vm.metroBpm.collectAsStateWithLifecycle()
    val beats by vm.metroBeats.collectAsStateWithLifecycle()
    val beat by vm.metroBeat.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var expanded by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    val names = remember(turkus) { turkus.associate { it.turku.id to it.turku.name } }

    LaunchedEffect(start) { while (start > 0) { now = System.currentTimeMillis(); delay(500) } }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Çalışma Köşesi", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(Tr.practice(stats.todaySec), "Bugün", Modifier.weight(1f))
                StatTile(Tr.practice(stats.weekSec), "Bu hafta", Modifier.weight(1f))
                StatTile("🔥 ${stats.streakDays}", "Gün seri", Modifier.weight(1f), color = Color(0xFFE0571B))
            }
        }
        // Süre tutucu
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⏱️ Çalışma süresi", fontWeight = FontWeight.Bold)
                    Text(if (start > 0) Tr.duration(now - start) else "00:00", fontSize = 46.sp, fontWeight = FontWeight.ExtraBold)
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (start == 0L) expanded = it }) {
                        OutlinedTextField(
                            value = selTurku?.let { names[it] } ?: "Genel çalışma (türkü seçilmedi)",
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
                        if (start > 0) {
                            Button(onClick = { vm.stopPractice() }) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(4.dp)); Text("Bitir ve Kaydet") }
                        } else {
                            Button(onClick = { vm.startPractice(selTurku) }) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Başlat") }
                            OutlinedButton(onClick = { manual = true }) { Text("Elle ekle") }
                        }
                    }
                    Text("Uygulamayı kapatsan da süre işlemeye devam eder.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                }
            }
        }
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
                        FilledIconButton(onClick = { vm.setBpm(bpm - 1) }) { Icon(Icons.Default.Remove, "Azalt") }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 18.dp)) {
                            Text("$bpm", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
                            Text("BPM • ${tempoName(bpm)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledIconButton(onClick = { vm.setBpm(bpm + 1) }) { Icon(Icons.Default.Add, "Artır") }
                    }
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
