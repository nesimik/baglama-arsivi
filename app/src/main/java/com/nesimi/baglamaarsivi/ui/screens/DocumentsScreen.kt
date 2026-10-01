package com.nesimi.baglamaarsivi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesimi.baglamaarsivi.data.DocumentCategory
import com.nesimi.baglamaarsivi.data.DocumentItem
import com.nesimi.baglamaarsivi.ui.MainViewModel
import com.nesimi.baglamaarsivi.ui.Screen
import com.nesimi.baglamaarsivi.ui.components.DocumentCard
import com.nesimi.baglamaarsivi.ui.components.EmptyState
import com.nesimi.baglamaarsivi.ui.components.SearchField
import com.nesimi.baglamaarsivi.util.DocumentOpener
import com.nesimi.baglamaarsivi.util.FileManager
import com.nesimi.baglamaarsivi.util.Tr

@Composable
fun DocumentsScreen(vm: MainViewModel, turkuFilter: Long?) {
    val docs by vm.documents.collectAsStateWithLifecycle()
    val turkus by vm.turkus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var q by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<String?>(null) }
    var editDoc by remember { mutableStateOf<DocumentItem?>(null) }
    val names = remember(turkus) { turkus.associate { it.turku.id to it.turku.name } }
    val shown = docs.filter { d ->
        (turkuFilter == null || d.turkuId == turkuFilter) &&
            (cat == null || d.category == cat) &&
            (q.isBlank() || Tr.contains(d.title, q) || Tr.contains(names[d.turkuId], q) || Tr.contains(d.category, q))
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Text("Notalar & Belgeler", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
            SearchField(q, { q = it }, "Belge, nota veya türkü adı…", Modifier.padding(horizontal = 16.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(selected = cat == null, onClick = { cat = null }, label = { Text("Tümü (${docs.size})") }) }
                items(DocumentCategory.entries) { c ->
                    val n = docs.count { it.category == c.displayName }
                    if (n > 0) FilterChip(selected = cat == c.displayName, onClick = { cat = c.displayName }, label = { Text("${c.displayName} ($n)") })
                }
            }
            if (shown.isEmpty()) EmptyState(Icons.Default.Description, if (docs.isEmpty()) "Henüz belge yok" else "Sonuç yok",
                if (docs.isEmpty()) "Nota, söz, akor şeması veya ses kaydı ekleyebilirsin." else "Aramayı veya kategoriyi değiştir.")
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { d ->
                    DocumentCard(
                        d, d.turkuId?.let { names[it] },
                        onOpen = { openDoc(context, vm, d) }, onFavorite = { vm.toggleDocFavorite(d) },
                        onShare = { FileManager.resolve(context, d.localPath)?.let { DocumentOpener.share(context, it, d.title) } },
                        onEdit = { editDoc = d }, onDelete = { vm.deleteDoc(d.id) }, onChooseApp = { openDoc(context, vm, d, true) }
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { vm.navigate(Screen.Import(isVideo = false, turkuId = turkuFilter)) },
            icon = { Icon(Icons.Default.Add, null) }, text = { Text("Belge Ekle") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
    editDoc?.let { DocEditDialog(vm, it) { editDoc = null } }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DocEditDialog(vm: MainViewModel, d: DocumentItem, onDismiss: () -> Unit) {
    val turkus by vm.turkus.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf(d.title) }
    var cat by remember { mutableStateOf(d.category) }
    var turkuId by remember { mutableStateOf(d.turkuId) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Belgeyi Düzenle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it }, label = { Text("Başlık") }, modifier = Modifier.fillMaxWidth())
                Text("Kategori", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DocumentCategory.entries.forEach { c -> FilterChip(selected = cat == c.displayName, onClick = { cat = c.displayName }, label = { Text(c.displayName) }) }
                }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = turkus.firstOrNull { it.turku.id == turkuId }?.turku?.name ?: "Genel arşiv (türküye bağlı değil)",
                        onValueChange = {}, readOnly = true, label = { Text("Bağlı türkü") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("Genel arşiv") }, onClick = { turkuId = null; expanded = false })
                        turkus.forEach { tw -> DropdownMenuItem(text = { Text(tw.turku.name) }, onClick = { turkuId = tw.turku.id; expanded = false }) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.updateDoc(d.copy(title = title.trim().ifBlank { d.title }, category = cat, turkuId = turkuId)); onDismiss() }) {
                Text("Kaydet", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
