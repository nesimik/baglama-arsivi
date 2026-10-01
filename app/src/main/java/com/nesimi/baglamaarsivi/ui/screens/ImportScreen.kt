package com.nesimi.baglamaarsivi.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesimi.baglamaarsivi.data.DocumentCategory
import com.nesimi.baglamaarsivi.data.StudyStatus
import com.nesimi.baglamaarsivi.ui.MainViewModel
import com.nesimi.baglamaarsivi.ui.Screen
import com.nesimi.baglamaarsivi.util.FileManager
import com.nesimi.baglamaarsivi.util.Tr
import kotlinx.coroutines.launch

private class Staged(val uri: Uri, title: String, order: String) {
    var title by mutableStateOf(title)
    var order by mutableStateOf(order)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportScreen(vm: MainViewModel, screen: Screen.Import) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val turkus by vm.turkus.collectAsStateWithLifecycle()
    var isVideo by remember { mutableStateOf(screen.isVideo) }
    val staged = remember { mutableStateListOf<Staged>() }

    fun add(uris: List<Uri>) {
        uris.forEach { u ->
            if (staged.none { it.uri == u }) {
                val name = FileManager.originalName(context, u).substringBeforeLast('.')
                staged.add(Staged(u, name, ""))
            }
        }
    }
    remember(screen) { add(screen.uris); 0 }

    val pickGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(60)) { add(it) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { add(it) }

    var turkuId by remember { mutableStateOf(screen.turkuId) }
    var newTurku by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newRegion by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var lessonDate by remember { mutableStateOf(Tr.today()) }
    var instructor by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<StudyStatus?>(null) }
    var fav by remember { mutableStateOf(false) }
    var deleteOriginals by remember { mutableStateOf(vm.deleteOriginalsPref) }
    var category by remember { mutableStateOf(DocumentCategory.NOTA) }
    var prefixDialog by remember { mutableStateOf(false) }

    val turkuName = turkus.firstOrNull { it.turku.id == turkuId }?.turku?.name
    val canSave = staged.isNotEmpty() && (if (newTurku) newName.isNotBlank() else (turkuId != null || !isVideo))

    Column(Modifier.fillMaxSize()) {
        BackTopBar(if (isVideo) "Video Ekle" else "Nota / Belge Ekle", onBack = { vm.back() })
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = isVideo, onClick = { isVideo = true }, label = { Text("🎬 Video") })
                    FilterChip(selected = !isVideo, onClick = { isVideo = false }, label = { Text("📄 Nota / Belge / Ses") })
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (isVideo) {
                        Button(onClick = { pickGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(4.dp)); Text("Galeriden")
                        }
                    }
                    FilledTonalButton(onClick = { pickFiles.launch(if (isVideo) arrayOf("video/*") else arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(4.dp)); Text("Dosyalardan")
                    }
                }
                Text(
                    "İpucu: WhatsApp'ta videoları basılı tutup seç → Paylaş → Bağlama Arşivi. Tek seferde 60 dosyaya kadar.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (staged.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Seçilenler (${staged.size})", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (isVideo) TextButton(onClick = { prefixDialog = true }) { Text("Toplu isim / numara") }
                    }
                }
            }
            itemsIndexed(staged, key = { _, s -> s.uri.toString() }) { i, s ->
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
                        Column(Modifier.weight(1f)) {
                            OutlinedTextField(s.title, { s.title = it }, label = { Text("Başlık") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            if (isVideo) OutlinedTextField(s.order, { s.order = it }, label = { Text("Sıra no (isteğe bağlı)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        Column {
                            IconButton(onClick = { if (i > 0) staged.add(i - 1, staged.removeAt(i)) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.ArrowUpward, "Yukarı") }
                            IconButton(onClick = { staged.removeAt(i) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Close, "Çıkar") }
                            IconButton(onClick = { if (i < staged.size - 1) staged.add(i + 1, staged.removeAt(i)) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.ArrowDownward, "Aşağı") }
                        }
                    }
                }
            }

            item {
                Text(if (isVideo) "Hangi türküye ait?" else "Hangi türküye bağlansın?", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                if (!newTurku) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = turkuName ?: if (isVideo) "Türkü seç…" else "Genel arşiv (türküye bağlı değil)",
                            onValueChange = {}, readOnly = true, label = { Text("Türkü") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            if (!isVideo) DropdownMenuItem(text = { Text("Genel arşiv") }, onClick = { turkuId = null; expanded = false })
                            turkus.sortedWith { a, b -> Tr.compare(a.turku.name, b.turku.name) }.forEach { tw ->
                                DropdownMenuItem(text = { Text(tw.turku.name) }, onClick = { turkuId = tw.turku.id; expanded = false })
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("➕ Yeni türkü oluştur", modifier = Modifier.weight(1f))
                    Switch(checked = newTurku, onCheckedChange = { newTurku = it })
                }
                if (newTurku) {
                    OutlinedTextField(newName, { newName = it }, label = { Text("Yeni türkü adı *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(newRegion, { newRegion = it }, label = { Text("Yöre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }

            item {
                if (isVideo) {
                    Text("Ders bilgileri", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    OutlinedTextField(lessonDate, { lessonDate = it }, label = { Text("Ders tarihi") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(instructor, { instructor = it }, label = { Text("Hoca (isteğe bağlı)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(desc, { desc = it }, label = { Text("Açıklama (isteğe bağlı)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(tags, { tags = it }, label = { Text("Etiketler (virgülle)") }, placeholder = { Text("ör. ritim, sağ el") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Türkünün durumu", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = status == null, onClick = { status = null }, label = { Text("Değiştirme") })
                        StudyStatus.entries.forEach { s -> FilterChip(selected = status == s, onClick = { status = s }, label = { Text("${s.badgeIcon} ${s.displayName}") }) }
                    }
                } else {
                    Text("Kategori", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DocumentCategory.entries.forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.displayName) }) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⭐ Favorilere ekle", modifier = Modifier.weight(1f))
                    Switch(checked = fav, onCheckedChange = { fav = it })
                }
                if (isVideo && com.nesimi.baglamaarsivi.util.VideoStore.galleryEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("🧹 Sonra orijinalleri silmeyi sor")
                            Text("Videolar Filmler/Bağlama Arşivi klasörüne kaydedilir. WhatsApp'taki kopyayı silersen tek kopya kalır; istersen \"Silme\" diyebilirsin.",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = deleteOriginals, onCheckedChange = { deleteOriginals = it; vm.deleteOriginalsPref = it })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    onClick = {
                        scope.launch {
                            val target: Long? = if (newTurku) vm.createTurkuQuick(newName, newRegion) else turkuId
                            val items = staged.toList()
                            if (isVideo) {
                                if (target == null) return@launch
                                vm.importVideos(
                                    items.map { MainViewModel.StagedVideo(it.uri, it.title.trim(), it.order.trim()) },
                                    target, lessonDate.trim(), instructor, desc, tags, status, fav, deleteOriginals
                                ) { vm.back(); vm.navigate(Screen.TurkuDetail(target)) }
                            } else {
                                vm.importDocuments(items.map { it.uri to it.title.trim() }, target, category, fav) { vm.back() }
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp))
                    Text(if (staged.isEmpty()) "Önce dosya seç" else "${staged.size} dosyayı arşive kaydet", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (prefixDialog) {
        var prefix by remember { mutableStateOf(turkuName ?: newName.ifBlank { "Ders" }) }
        var numbered by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { prefixDialog = false },
            title = { Text("Toplu isim ver") },
            text = {
                Column {
                    Text("Videolar listedeki sırasıyla “$prefix – 1”, “$prefix – 2” … diye adlandırılır.", fontSize = 13.sp)
                    OutlinedTextField(prefix, { prefix = it }, label = { Text("Ön ek") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sıra numarası da ver (1, 2, 3…)", modifier = Modifier.weight(1f), fontSize = 13.sp)
                        Switch(checked = numbered, onCheckedChange = { numbered = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    staged.forEachIndexed { i, s ->
                        s.title = "${prefix.trim()} – ${i + 1}"
                        if (numbered) s.order = "${i + 1}"
                    }
                    prefixDialog = false
                }) { Text("Uygula") }
            },
            dismissButton = { OutlinedButton(onClick = { prefixDialog = false }) { Text("Vazgeç") } }
        )
    }
}
