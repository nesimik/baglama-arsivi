package com.nesimi.baglamaarsivi.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesimi.baglamaarsivi.data.StudyStatus
import com.nesimi.baglamaarsivi.ui.MainViewModel
import com.nesimi.baglamaarsivi.ui.Screen
import com.nesimi.baglamaarsivi.ui.components.EmptyState
import com.nesimi.baglamaarsivi.ui.components.SectionTitle
import com.nesimi.baglamaarsivi.ui.components.StatTile
import com.nesimi.baglamaarsivi.ui.components.Thumbnail
import com.nesimi.baglamaarsivi.ui.components.TurkuCard
import com.nesimi.baglamaarsivi.ui.components.VideoCard
import com.nesimi.baglamaarsivi.ui.theme.Amber
import com.nesimi.baglamaarsivi.ui.theme.WoodMedium
import com.nesimi.baglamaarsivi.util.Tr
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(vm: MainViewModel) {
    val turkus by vm.turkus.collectAsStateWithLifecycle()
    val videos by vm.videos.collectAsStateWithLifecycle()
    val docs by vm.documents.collectAsStateWithLifecycle()
    val recent by vm.recentlyWatched.collectAsStateWithLifecycle()
    val stats by vm.practiceStats.collectAsStateWithLifecycle()
    val practiceStart by vm.practiceStart.collectAsStateWithLifecycle()
    var showNew by remember { mutableStateOf(false) }

    val turkuNames = remember(turkus) { turkus.associate { it.turku.id to it.turku.name } }
    val studying = turkus.filter { it.turku.status == StudyStatus.CALISIYORUM.name }
    val recentAdded = remember(videos) { videos.sortedByDescending { it.createdAt }.take(8) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Amber, WoodMedium))).padding(18.dp)
            ) {
                Column {
                    Text("Bağlama Arşivi", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Türkülerin, ders videoların ve notaların", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.95f),
                        modifier = Modifier.fillMaxWidth().clickable { vm.navigate(Screen.Search) }
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, null, tint = Color(0xFF6B5E55))
                            Spacer(Modifier.width(8.dp))
                            Text("Türkü, video, nota ara…", color = Color(0xFF6B5E55))
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Button(onClick = { vm.navigate(Screen.Import(isVideo = true)) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                    Icon(Icons.Default.VideoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Video", fontSize = 13.sp)
                }
                FilledTonalButton(onClick = { vm.navigate(Screen.Import(isVideo = false)) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                    Icon(Icons.Default.Description, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Nota", fontSize = 13.sp)
                }
                FilledTonalButton(onClick = { showNew = true }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Türkü", fontSize = 13.sp)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("${turkus.size}", "Türkü", Modifier.weight(1f)) { vm.switchTab(Screen.TurkuList) }
                StatTile("${videos.size}", "Video", Modifier.weight(1f)) { vm.switchTab(Screen.TurkuList) }
                StatTile("${docs.size}", "Belge", Modifier.weight(1f)) { vm.switchTab(Screen.Documents) }
                StatTile("${turkus.count { it.turku.isFavorite } + videos.count { it.isFavorite }}", "Favori", Modifier.weight(1f), color = Color(0xFFE09000)) { vm.navigate(Screen.Favorites) }
            }
        }
        item {
            // Çalışma özeti
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(practiceStart) { while (practiceStart > 0) { now = System.currentTimeMillis(); delay(1000) } }
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (practiceStart > 0) "⏱️ Çalışıyorsun: ${Tr.duration(now - practiceStart)}" else "🎯 Bugün: ${Tr.practice(stats.todaySec)}",
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Bu hafta ${Tr.practice(stats.weekSec)}  •  🔥 ${stats.streakDays} gün seri", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                    if (practiceStart > 0) {
                        Button(onClick = { vm.stopPractice() }) { Icon(Icons.Default.Stop, null); Text("Bitir") }
                    } else {
                        Button(onClick = { vm.switchTab(Screen.Practice) }) { Icon(Icons.Default.PlayArrow, null); Text("Çalış") }
                    }
                }
            }
        }

        if (turkus.isEmpty()) {
            item {
                EmptyState(
                    Icons.Default.LibraryMusic, "Arşivin henüz boş",
                    "WhatsApp'taki ders videolarını “Paylaş → Bağlama Arşivi” ile ekleyebilir ya da eski uygulamadaki verilerini aktarabilirsin."
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(onClick = { vm.navigate(Screen.Migration) }) {
                            Icon(Icons.Default.CloudDownload, null); Spacer(Modifier.width(6.dp)); Text("Eski uygulamadan aktar / Yedek yükle")
                        }
                        OutlinedButton(onClick = { showNew = true }) { Text("İlk türkünü oluştur") }
                    }
                }
            }
        }

        if (recent.isNotEmpty()) {
            item { SectionTitle("▶ Kaldığın yerden devam et") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recent, key = { it.id }) { v ->
                        Column(Modifier.width(170.dp).clickable { vm.navigate(Screen.Player(v.id)) }) {
                            Thumbnail(
                                v.thumbnailPath, Modifier.width(170.dp).height(96.dp), v.durationMs,
                                if (v.durationMs > 0) v.lastPlaybackPositionMs.toFloat() / v.durationMs else 0f
                            )
                            Text(v.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(turkuNames[v.turkuId] ?: "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }

        if (studying.isNotEmpty()) {
            item { SectionTitle("🎸 Şu an çalıştıklarım") }
            items(studying.take(6), key = { "s" + it.turku.id }) { t ->
                TurkuCard(t, compact = false, onClick = { vm.navigate(Screen.TurkuDetail(t.turku.id)) }, onFavorite = { vm.toggleTurkuFavorite(t.turku) })
            }
        }

        if (recentAdded.isNotEmpty()) {
            item { SectionTitle("🕐 Son eklenen videolar") }
            items(recentAdded, key = { "v" + it.id }) { v ->
                VideoCard(v, onClick = { vm.navigate(Screen.Player(v.id)) }, subtitle = "🎵 ${turkuNames[v.turkuId] ?: ""}  📅 ${v.lessonDate}")
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showNew) TurkuEditDialog(null, onDismiss = { showNew = false }) { vm.saveTurku(it) }
}
