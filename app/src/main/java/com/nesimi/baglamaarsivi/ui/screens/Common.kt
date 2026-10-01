package com.nesimi.baglamaarsivi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nesimi.baglamaarsivi.data.LEVELS
import com.nesimi.baglamaarsivi.data.StudyStatus
import com.nesimi.baglamaarsivi.data.Turku

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold, maxLines = 1) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
        actions = { actions() }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TurkuEditDialog(initial: Turku?, onDismiss: () -> Unit, onSave: (Turku) -> Unit) {
    val base = initial ?: Turku(name = "")
    var name by remember { mutableStateOf(base.name) }
    var region by remember { mutableStateOf(base.region) }
    var artist by remember { mutableStateOf(base.artist) }
    var makam by remember { mutableStateOf(base.makam) }
    var level by remember { mutableStateOf(base.level) }
    var status by remember { mutableStateOf(StudyStatus.fromString(base.status)) }
    var fav by remember { mutableStateOf(base.isFavorite) }
    var desc by remember { mutableStateOf(base.description) }
    var tags by remember { mutableStateOf(base.tags) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Yeni Türkü" else "Türküyü Düzenle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Türkü adı *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(region, { region = it }, label = { Text("Yöre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(artist, { artist = it }, label = { Text("Sanatçı / Kaynak kişi") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(makam, { makam = it }, label = { Text("Makam / Ayak / Düzen") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Seviye", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LEVELS.forEach { l -> FilterChip(selected = level == l, onClick = { level = l }, label = { Text(l) }) }
                }
                Text("Durum", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StudyStatus.entries.forEach { s -> FilterChip(selected = status == s, onClick = { status = s }, label = { Text("${s.badgeIcon} ${s.displayName}") }) }
                }
                OutlinedTextField(desc, { desc = it }, label = { Text("Açıklama") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(tags, { tags = it }, label = { Text("Etiketler (virgülle)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⭐ Favori", modifier = Modifier.weight(1f))
                    Switch(checked = fav, onCheckedChange = { fav = it })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(
                    base.copy(
                        name = name.trim(), region = region.trim(), artist = artist.trim(), makam = makam.trim(),
                        level = level, status = status.name, isFavorite = fav, description = desc.trim(), tags = tags.trim()
                    )
                )
                onDismiss()
            }) { Text("Kaydet", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
