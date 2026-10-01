package com.nesimi.baglamaarsivi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesimi.baglamaarsivi.data.FilterOption
import com.nesimi.baglamaarsivi.data.SortOption
import com.nesimi.baglamaarsivi.ui.MainViewModel
import com.nesimi.baglamaarsivi.ui.Screen
import com.nesimi.baglamaarsivi.ui.components.EmptyState
import com.nesimi.baglamaarsivi.ui.components.SearchField
import com.nesimi.baglamaarsivi.ui.components.SectionTitle
import com.nesimi.baglamaarsivi.ui.components.TurkuCard
import com.nesimi.baglamaarsivi.ui.components.VideoCard
import com.nesimi.baglamaarsivi.util.DocumentOpener
import com.nesimi.baglamaarsivi.util.FileManager
import com.nesimi.baglamaarsivi.util.Tr

@Composable
fun TurkuListScreen(vm: MainViewModel) {
    val list by vm.filteredTurkus.collectAsStateWithLifecycle()
    val all by vm.turkus.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val compact by vm.compact.collectAsStateWithLifecycle()
    var showNew by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val manualMode = sort == SortOption.MANUAL && query.isBlank() && filter == FilterOption.ALL

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Türkülerim", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.toggleCompact() }) { Icon(if (compact) Icons.Default.ViewAgenda else Icons.Default.ViewList, "Görünüm") }
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sırala") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortOption.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.title) },
                                leadingIcon = { if (s == sort) Icon(Icons.Default.Check, null) },
                                onClick = { vm.setSort(s); sortMenu = false }
                            )
                        }
                    }
                }
            }
            SearchField(query, vm::setQuery, "Türkü, yöre, makam, sanatçı…", Modifier.padding(horizontal = 16.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(FilterOption.entries) { f -> FilterChip(selected = f == filter, onClick = { vm.setFilter(f) }, label = { Text(f.title) }) }
            }
            Text(
                "${list.size} türkü" + if (manualMode && list.size > 1) "  •  Sırayı ok tuşlarıyla değiştirebilirsin" else "",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp)
            )
            if (list.isEmpty()) {
                EmptyState(Icons.Default.Search, if (all.isEmpty()) "Henüz türkü yok" else "Sonuç bulunamadı",
                    if (all.isEmpty()) "Sağ alttaki + ile ilk türkünü ekle." else "Aramayı veya filtreyi değiştir.")
            }
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(list, key = { _, it -> it.turku.id }) { i, item ->
                    TurkuCard(
                        item, compact,
                        onClick = { vm.navigate(Screen.TurkuDetail(item.turku.id)) },
                        onFavorite = { vm.toggleTurkuFavorite(item.turku) },
                        trailing = if (manualMode) {
                            @Composable {
                                Column {
                                    IconButton(onClick = { vm.moveTurku(i, i - 1) }, enabled = i > 0, modifier = Modifier.height(30.dp)) { Icon(Icons.Default.ArrowUpward, "Yukarı") }
                                    IconButton(onClick = { vm.moveTurku(i, i + 1) }, enabled = i < list.size - 1, modifier = Modifier.height(30.dp)) { Icon(Icons.Default.ArrowDownward, "Aşağı") }
                                }
                            }
                        } else null
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showNew = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Yeni Türkü") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
    if (showNew) TurkuEditDialog(null, onDismiss = { showNew = false }) { vm.saveTurku(it) }
}

@Composable
fun FavoritesScreen(vm: MainViewModel) {
    val turkus by vm.turkus.collectAsStateWithLifecycle()
    val videos by vm.videos.collectAsStateWithLifecycle()
    val docs by vm.documents.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val names = remember(turkus) { turkus.associate { it.turku.id to it.turku.name } }
    Column(Modifier.fillMaxSize()) {
        BackTopBar("⭐ Favoriler", onBack = { vm.back() })
        val favT = turkus.filter { it.turku.isFavorite }
        val favV = videos.filter { it.isFavorite }
        val favD = docs.filter { it.isFavorite }
        TabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Türküler (${favT.size})") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Videolar (${favV.size})") })
            Tab(tab == 2, { tab = 2 }, text = { Text("Belgeler (${favD.size})") })
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (tab) {
                0 -> {
                    if (favT.isEmpty()) item { EmptyState(Icons.Default.Star, "Favori türkü yok", "Türkü kartındaki yıldıza dokun.") }
                    items(favT, key = { it.turku.id }) { t ->
                        TurkuCard(t, false, onClick = { vm.navigate(Screen.TurkuDetail(t.turku.id)) }, onFavorite = { vm.toggleTurkuFavorite(t.turku) })
                    }
                }
                1 -> {
                    if (favV.isEmpty()) item { EmptyState(Icons.Default.Star, "Favori video yok", "Video kartındaki yıldıza dokun.") }
                    items(favV, key = { it.id }) { v ->
                        VideoCard(v, onClick = { vm.navigate(Screen.Player(v.id)) }, subtitle = "🎵 ${names[v.turkuId] ?: ""}  📅 ${v.lessonDate}", onFavorite = { vm.toggleVideoFavorite(v) })
                    }
                }
                else -> {
                    if (favD.isEmpty()) item { EmptyState(Icons.Default.Star, "Favori belge yok", "Belge kartındaki yıldıza dokun.") }
                    items(favD, key = { it.id }) { d ->
                        com.nesimi.baglamaarsivi.ui.components.DocumentCard(
                            d, d.turkuId?.let { names[it] },
                            onOpen = { openDoc(context, vm, d) },
                            onFavorite = { vm.toggleDocFavorite(d) },
                            onShare = { FileManager.resolve(context, d.localPath)?.let { DocumentOpener.share(context, it, d.title) } },
                            onEdit = {}, onDelete = { vm.deleteDoc(d.id) },
                            onChooseApp = { openDoc(context, vm, d, force = true) }
                        )
                    }
                }
            }
        }
    }
}

fun openDoc(context: android.content.Context, vm: MainViewModel, d: com.nesimi.baglamaarsivi.data.DocumentItem, force: Boolean = false) {
    val f = FileManager.resolve(context, d.localPath)
    if (f == null) { vm.toast("Dosya bulunamadı (silinmiş olabilir)"); return }
    if (!DocumentOpener.open(context, f, d.id, force)) vm.toast("Bu dosyayı açacak uygulama bulunamadı")
}

@Composable
fun SearchScreen(vm: MainViewModel) {
    val turkus by vm.turkus.collectAsStateWithLifecycle()
    val videos by vm.videos.collectAsStateWithLifecycle()
    val docs by vm.documents.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var q by remember { mutableStateOf("") }
    val names = remember(turkus) { turkus.associate { it.turku.id to it.turku.name } }
    val rt = if (q.isBlank()) emptyList() else turkus.filter { val t = it.turku; Tr.contains(t.name, q) || Tr.contains(t.region, q) || Tr.contains(t.makam, q) || Tr.contains(t.artist, q) || Tr.contains(t.tags, q) || Tr.contains(t.personalNote, q) }
    val rv = if (q.isBlank()) emptyList() else videos.filter { Tr.contains(it.title, q) || Tr.contains(it.description, q) || Tr.contains(it.tags, q) || Tr.contains(it.instructor, q) || it.lessonDate.contains(q) || Tr.contains(names[it.turkuId], q) }
    val rd = if (q.isBlank()) emptyList() else docs.filter { Tr.contains(it.title, q) || Tr.contains(it.category, q) }
    Column(Modifier.fillMaxSize()) {
        BackTopBar("Ara", onBack = { vm.back() })
        SearchField(q, { q = it }, "Türkü, video, nota, tarih (ör. 25.09)…", Modifier.padding(horizontal = 16.dp))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (q.isNotBlank() && rt.isEmpty() && rv.isEmpty() && rd.isEmpty()) item { EmptyState(Icons.Default.Search, "Sonuç yok", "Farklı bir kelime dene.") }
            if (rt.isNotEmpty()) item { SectionTitle("Türküler (${rt.size})") }
            items(rt, key = { "t" + it.turku.id }) { t -> TurkuCard(t, true, onClick = { vm.navigate(Screen.TurkuDetail(t.turku.id)) }, onFavorite = { vm.toggleTurkuFavorite(t.turku) }) }
            if (rv.isNotEmpty()) item { SectionTitle("Videolar (${rv.size})") }
            items(rv, key = { "v" + it.id }) { v -> VideoCard(v, onClick = { vm.navigate(Screen.Player(v.id)) }, subtitle = "🎵 ${names[v.turkuId] ?: ""}  📅 ${v.lessonDate}") }
            if (rd.isNotEmpty()) item { SectionTitle("Belgeler (${rd.size})") }
            items(rd, key = { "d" + it.id }) { d ->
                com.nesimi.baglamaarsivi.ui.components.DocumentCard(
                    d, d.turkuId?.let { names[it] }, onOpen = { openDoc(context, vm, d) }, onFavorite = { vm.toggleDocFavorite(d) },
                    onShare = { FileManager.resolve(context, d.localPath)?.let { DocumentOpener.share(context, it, d.title) } },
                    onEdit = {}, onDelete = { vm.deleteDoc(d.id) }, onChooseApp = { openDoc(context, vm, d, true) }
                )
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}
