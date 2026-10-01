package com.nesimi.baglamaarsivi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesimi.baglamaarsivi.data.DocumentItem
import com.nesimi.baglamaarsivi.data.StudyStatus
import com.nesimi.baglamaarsivi.data.Turku
import com.nesimi.baglamaarsivi.data.VideoItem
import com.nesimi.baglamaarsivi.ui.MainViewModel
import com.nesimi.baglamaarsivi.ui.Screen
import com.nesimi.baglamaarsivi.ui.components.ConfirmDialog
import com.nesimi.baglamaarsivi.ui.components.DocumentCard
import com.nesimi.baglamaarsivi.ui.components.EmptyState
import com.nesimi.baglamaarsivi.ui.components.OrderTagDialog
import com.nesimi.baglamaarsivi.ui.components.VideoCard
import com.nesimi.baglamaarsivi.ui.components.statusColor
import com.nesimi.baglamaarsivi.ui.theme.FavGold
import com.nesimi.baglamaarsivi.util.DocumentOpener
import com.nesimi.baglamaarsivi.util.FileManager
import com.nesimi.baglamaarsivi.util.Tr
import com.nesimi.baglamaarsivi.util.VideoOrder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TurkuDetailScreen(vm: MainViewModel, turkuId: Long) {
    val turku by remember(turkuId) { vm.turkuFlow(turkuId) }.collectAsState(initial = null)
    val videos by remember(turkuId) { vm.videosFor(turkuId) }.collectAsState(initial = emptyList())
    val docs by remember(turkuId) { vm.docsFor(turkuId) }.collectAsState(initial = emptyList())
    val sessions by remember(turkuId) { vm.practiceFor(turkuId) }.collectAsState(initial = emptyList())
    val practiceStart by vm.practiceStart.collectAsStateWithLifecycle()
    val practiceTurku by vm.practiceTurku.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showEdit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var orderFor by remember { mutableStateOf<VideoItem?>(null) }
    var editVideo by remember { mutableStateOf<VideoItem?>(null) }
    var editDoc by remember { mutableStateOf<DocumentItem?>(null) }
    var confirmRenumber by remember { mutableStateOf(false) }
    val selectedIds = remember { androidx.compose.runtime.mutableStateListOf<Long>() }
    var selectionMode by remember { mutableStateOf(false) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = selectionMode) { selectionMode = false; selectedIds.clear() }

    val t = turku
    if (t == null) {
        Column(Modifier.fillMaxSize()) { BackTopBar("", onBack = { vm.back() }) }
        return
    }
    val status = StudyStatus.fromString(t.status)

    Column(Modifier.fillMaxSize()) {
        if (selectionMode) {
            SelectionBar(
                count = selectedIds.size,
                onClose = { selectionMode = false; selectedIds.clear() },
                onSelectAll = {
                    if (selectedIds.size == videos.size) selectedIds.clear()
                    else { selectedIds.clear(); selectedIds.addAll(videos.map { it.id }) }
                },
                onDelete = { if (selectedIds.isNotEmpty()) confirmBulkDelete = true }
            )
        } else BackTopBar(t.name, onBack = { vm.back() }) {
            IconButton(onClick = { vm.toggleTurkuFavorite(t) }) {
                Icon(if (t.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder, "Favori", tint = if (t.isFavorite) FavGold else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { showEdit = true }) { Icon(Icons.Default.Edit, "Düzenle") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Sil") }
        }

        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp)) {
                        val info = listOf("📍" to t.region, "🎼" to t.makam, "🎤" to t.artist, "📶" to t.level).filter { it.second.isNotBlank() }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            info.forEach { (ic, v) -> Text("$ic $v", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        if (t.description.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(t.description, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StudyStatus.entries.forEach { s ->
                                FilterChip(
                                    selected = s == status, onClick = { vm.setTurkuStatus(t.id, s) },
                                    label = { Text("${s.badgeIcon} ${s.displayName}", color = if (s == status) statusColor(s) else MaterialTheme.colorScheme.onSurfaceVariant) }
                                )
                            }
                        }
                        if (t.tags.isNotBlank()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Tr.splitTags(t.tags).forEach { Text("#$it", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        val total = sessions.sumOf { it.durationSec }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⏱ Toplam çalışma: ${Tr.practice(total)}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                            if (practiceStart > 0 && practiceTurku == t.id) {
                                Button(onClick = { vm.stopPractice() }) { Text("Çalışmayı Bitir") }
                            } else {
                                FilledTonalButton(onClick = { vm.startPractice(t.id) }, enabled = practiceStart == 0L) {
                                    Icon(Icons.Default.Timer, null); Spacer(Modifier.width(4.dp)); Text("Çalışmaya Başla")
                                }
                            }
                        }
                    }
                }
            }
            item {
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("Videolar (${videos.size})") })
                    Tab(tab == 1, { tab = 1 }, text = { Text("Notalar (${docs.size})") })
                    Tab(tab == 2, { tab = 2 }, text = { Text("Notlarım") })
                    Tab(tab == 3, { tab = 3 }, text = { Text("Çalışma (${sessions.size})") })
                }
            }
            when (tab) {
                0 -> {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Button(onClick = { vm.navigate(Screen.Import(isVideo = true, turkuId = t.id)) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Add, null); Text("Video Ekle")
                            }
                            if (videos.isNotEmpty()) {
                                val next = videos.firstOrNull { it.durationMs == 0L || it.lastPlaybackPositionMs < it.durationMs * 0.9 } ?: videos.first()
                                FilledTonalButton(onClick = { vm.navigate(Screen.Player(next.id)) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.PlayArrow, null); Text("Sırayla İzle")
                                }
                            }
                        }
                    }
                    if (videos.isEmpty()) {
                        item { EmptyState(Icons.Default.VideoLibrary, "Henüz video yok", "WhatsApp'ta videoyu seç → Paylaş → Bağlama Arşivi.") }
                    } else {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("💡 Kutuya numara yaz (1.1, 1.2, 2.1…), video yerine kayar. Toplu silmek için karta basılı tut.", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                IconButton(onClick = { confirmRenumber = true }) { Icon(Icons.Default.FormatListNumbered, "Yeniden numaralandır") }
                            }
                        }
                    }
                    val suggestion = nextOrderSuggestion(videos)
                    val groups = videos.groupBy { VideoOrder.section(it.displayOrderTag) }
                    groups.forEach { (section, list) ->
                        item(key = "h$section") { GroupHeader(section, list.size, Modifier.animateItem()) }
                        items(list, key = { it.id }) { v ->
                            VideoCard(
                                v,
                                onClick = {
                                    if (selectionMode) { if (v.id in selectedIds) selectedIds.remove(v.id) else selectedIds.add(v.id) }
                                    else vm.navigate(Screen.Player(v.id))
                                },
                                selected = if (selectionMode) v.id in selectedIds else null,
                                onLongClick = {
                                    if (!selectionMode) { selectionMode = true; selectedIds.clear() }
                                    if (v.id !in selectedIds) selectedIds.add(v.id)
                                },
                                modifier = Modifier.animateItem(),
                                onOrderClick = { orderFor = v }, onFavorite = { vm.toggleVideoFavorite(v) },
                                onEdit = { editVideo = v }, onDelete = { vm.deleteVideo(v.id) },
                                onOrderChange = { vm.setVideoOrderTag(v.id, it) }, orderSuggestion = suggestion
                            )
                        }
                    }
                }
                1 -> {
                    item {
                        OutlinedButton(onClick = { vm.navigate(Screen.Import(isVideo = false, turkuId = t.id)) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Icon(Icons.Default.Add, null); Text("Nota / Belge / Ses Ekle")
                        }
                    }
                    if (docs.isEmpty()) item { EmptyState(Icons.Default.Description, "Henüz belge yok", "Nota, söz, ses kaydı veya fotoğraf ekleyebilirsin.") }
                    items(docs, key = { it.id }) { d ->
                        DocumentCard(
                            d, null, onOpen = { openDoc(context, vm, d) }, onFavorite = { vm.toggleDocFavorite(d) },
                            onShare = { FileManager.resolve(context, d.localPath)?.let { DocumentOpener.share(context, it, d.title) } },
                            onEdit = { editDoc = d }, onDelete = { vm.deleteDoc(d.id) }, onChooseApp = { openDoc(context, vm, d, true) }
                        )
                    }
                }
                2 -> item { NoteEditor(t, vm) }
                else -> {
                    if (sessions.isEmpty()) item { EmptyState(Icons.Default.Timer, "Çalışma kaydı yok", "“Çalışmaya Başla” ile süre tut, ilerlemeni gör.") }
                    items(sessions, key = { it.id }) { s ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(Tr.practice(s.durationSec), fontWeight = FontWeight.Bold)
                                    Text(Tr.dateTime(s.startedAt) + if (s.note.isNotBlank()) " • ${s.note}" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { vm.deletePractice(s.id) }) { Icon(Icons.Default.Delete, "Sil") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEdit) TurkuEditDialog(t, onDismiss = { showEdit = false }) { vm.saveTurku(it) }
    if (confirmDelete) ConfirmDialog("Türkü silinsin mi?", "“${t.name}” ve videoları çöp kutusuna taşınır. 30 gün içinde geri alabilirsin.", "Sil",
        onConfirm = { vm.deleteTurku(t.id) }, onDismiss = { confirmDelete = false })
    orderFor?.let { v -> OrderTagDialog(v.orderTag.ifBlank { v.displayOrderTag }, v.title, onSave = { vm.setVideoOrderTag(v.id, it) }, onDismiss = { orderFor = null }) }
    editVideo?.let { v -> VideoEditDialog(vm, v, onDismiss = { editVideo = null }) }
    editDoc?.let { d -> DocEditDialog(vm, d, onDismiss = { editDoc = null }) }
    if (confirmBulkDelete) ConfirmDialog(
        "${selectedIds.size} video silinsin mi?",
        "Seçilen videolar arşivden ve telefondan silinecek. Geri alınamaz.",
        "Sil",
        onConfirm = { vm.deleteVideos(selectedIds.toList()); selectedIds.clear(); selectionMode = false },
        onDismiss = { confirmBulkDelete = false }
    )
    if (confirmRenumber) ConfirmDialog(
        "Yeniden numaralandır", "Videolar şu anki sırasıyla 1, 2, 3… diye numaralanacak. Noktalı numaralar (2.1 gibi) silinir.", "Numaralandır",
        onConfirm = { vm.renumberVideos(videos) }, onDismiss = { confirmRenumber = false }, destructive = false
    )
}

@Composable
private fun NoteEditor(t: Turku, vm: MainViewModel) {
    var note by remember(t.id) { mutableStateOf(t.personalNote) }
    LaunchedEffect(t.personalNote) { if (note.isEmpty()) note = t.personalNote }
    Column(Modifier.padding(top = 6.dp)) {
        Text("📝 Kişisel çalışma notların", fontWeight = FontWeight.Bold)
        Text("ör. “2. bölümde ritmi yavaş çalış”, “3. dersteki geçişi tekrar et”", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth(), minLines = 6, placeholder = { Text("Notlarını yaz…") })
        Spacer(Modifier.height(6.dp))
        Button(onClick = { vm.saveNote(t.id, note) }, enabled = note != t.personalNote, modifier = Modifier.fillMaxWidth()) { Text("Notu Kaydet") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditDialog(vm: MainViewModel, v: VideoItem, onDismiss: () -> Unit) {
    val turkus by vm.turkus.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf(v.title) }
    var date by remember { mutableStateOf(v.lessonDate) }
    var instructor by remember { mutableStateOf(v.instructor) }
    var desc by remember { mutableStateOf(v.description) }
    var tags by remember { mutableStateOf(v.tags) }
    var order by remember { mutableStateOf(v.orderTag.ifBlank { v.displayOrderTag }) }
    var turkuId by remember { mutableStateOf(v.turkuId) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video Bilgileri") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it }, label = { Text("Başlık") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(order, { order = it }, label = { Text("Sıra no (ör. 2.1)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(date, { date = it }, label = { Text("Ders tarihi (gg.aa.yyyy)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(instructor, { instructor = it }, label = { Text("Hoca") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = turkus.firstOrNull { it.turku.id == turkuId }?.turku?.name ?: "",
                        onValueChange = {}, readOnly = true, label = { Text("Türkü") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        turkus.forEach { tw ->
                            DropdownMenuItem(text = { Text(tw.turku.name) }, onClick = { turkuId = tw.turku.id; expanded = false })
                        }
                    }
                }
                OutlinedTextField(desc, { desc = it }, label = { Text("Açıklama") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tags, { tags = it }, label = { Text("Etiketler") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clean = order.trim().removePrefix("#").trim()
                vm.updateVideo(
                    v.copy(
                        title = title.trim().ifBlank { v.title }, lessonDate = date.trim(), instructor = instructor.trim(),
                        description = desc.trim(), tags = tags.trim(), turkuId = turkuId, orderTag = clean,
                        manualOrder = clean.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
                    )
                )
                onDismiss()
            }) { Text("Kaydet", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}


/** Boş numaralı videolar için önerilen sıradaki numara: son numara 2.3 ise 2.4 */
fun nextOrderSuggestion(videos: List<VideoItem>): String {
    val last = videos.filter { it.displayOrderTag.isNotBlank() }.maxWithOrNull(VideoOrder) ?: return "1.1"
    val tag = last.displayOrderTag.replace(',', '.')
    val parts = tag.split('.', '/', '-').filter { it.isNotBlank() }
    val major = parts.getOrNull(0)?.toIntOrNull() ?: return "1.1"
    val minor = parts.getOrNull(1)?.toIntOrNull()
    return if (minor != null) "$major.${minor + 1}" else "$major.1"
}

@Composable
fun GroupHeader(section: Int?, count: Int, modifier: Modifier = Modifier) {
    val dark = com.nesimi.baglamaarsivi.ui.theme.LocalIsDark.current
    val style = com.nesimi.baglamaarsivi.util.SectionColors.forSection(section, dark)
    Row(modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 6.dp, height = 22.dp).background(style.border, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(8.dp))
        Text(if (section == null) "Numarasız" else "${section}. Bölüm", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text("  •  $count video", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (section == null) Text("  (numara ver, yerine geçsin)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SelectionBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit, onDelete: () -> Unit) {
    androidx.compose.material3.TopAppBar(
        title = { Text("$count seçildi", fontWeight = FontWeight.Bold) },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Seçimi kapat") } },
        actions = {
            IconButton(onClick = onSelectAll) { Icon(Icons.Default.SelectAll, "Tümünü seç") }
            IconButton(onClick = onDelete, enabled = count > 0) { Icon(Icons.Default.Delete, "Seçilenleri sil", tint = MaterialTheme.colorScheme.error) }
        },
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    )
}
