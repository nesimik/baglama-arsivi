package com.nesimi.baglamaarsivi.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesimi.baglamaarsivi.BuildConfig
import com.nesimi.baglamaarsivi.data.AppThemeMode
import com.nesimi.baglamaarsivi.data.DocumentItem
import com.nesimi.baglamaarsivi.data.Turku
import com.nesimi.baglamaarsivi.data.VideoItem
import com.nesimi.baglamaarsivi.ui.MainViewModel
import com.nesimi.baglamaarsivi.ui.Screen
import com.nesimi.baglamaarsivi.ui.components.ConfirmDialog
import com.nesimi.baglamaarsivi.ui.components.EmptyState
import com.nesimi.baglamaarsivi.ui.components.SectionTitle
import com.nesimi.baglamaarsivi.ui.components.VideoCard
import com.nesimi.baglamaarsivi.util.Tr
import java.text.SimpleDateFormat
import java.util.Date

@Composable
private fun rememberBackupLaunchers(vm: MainViewModel): Pair<() -> Unit, () -> Unit> {
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let { vm.backupTo(it) } }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingRestore = uri }
    pendingRestore?.let { uri ->
        ConfirmDialog(
            "Geri yüklensin mi?",
            "Seçilen yedekteki türküler, videolar ve belgeler yüklenecek. Şu an uygulamadaki veritabanı bu yedekle DEĞİŞTİRİLİR (mevcut videolar silinmez). Emin misin?",
            "Geri Yükle",
            onConfirm = { vm.restoreFrom(uri) },
            onDismiss = { pendingRestore = null }
        )
    }
    val name = "BaglamaArsivi_yedek_" + SimpleDateFormat("yyyy-MM-dd_HHmm", Tr.LOCALE).format(Date()) + ".zip"
    return Pair({ create.launch(name) }, { open.launch(arrayOf("application/zip", "application/x-tar", "application/octet-stream", "*/*")) })
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val last by vm.lastBackup.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val (doBackup, doRestore) = rememberBackupLaunchers(vm)
    LaunchedEffect(Unit) { vm.refreshStorage() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Ayarlar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SectionTitle("🎨 Görünüm")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppThemeMode.entries.forEach { m -> FilterChip(selected = theme == m, onClick = { vm.setTheme(m) }, label = { Text(m.title) }) }
        }

        SectionTitle("💾 Yedekleme")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp)) {
                val days = if (last > 0) ((System.currentTimeMillis() - last) / 86_400_000L).toInt() else -1
                Text(if (last > 0) "Son yedek: ${Tr.dateTime(last)}" else "Henüz yedek alınmadı", fontWeight = FontWeight.Bold)
                if (days < 0 || days >= 7) Text("⚠️ Videoların yalnızca bu telefonda. Düzenli yedek almanı öneririm.", fontSize = 12.sp, color = Color(0xFFC2410C))
                Text("Tam yedek: uygulamada görünen tüm videolar, notalar, türküler, numaralar, işaretler ve çalışma kayıtları tek bir .zip dosyasına toplanır. " +
                    "Videoların ikinci kopyası yalnızca bu yedek dosyasında olur.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { if (com.nesimi.baglamaarsivi.util.VideoStore.galleryEnabled) vm.backupToPhoneFolder() else doBackup() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Backup, null); Spacer(Modifier.width(4.dp)); Text("Yedek Al")
                    }
                    FilledTonalButton(onClick = doRestore, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Restore, null); Spacer(Modifier.width(4.dp)); Text("Geri Yükle") }
                }
                if (com.nesimi.baglamaarsivi.util.VideoStore.galleryEnabled) {
                    Text("Yedek, telefonun Dosyalar uygulamasında İndirilenler (Download) → Bağlama Arşivim klasörüne kaydedilir.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = doBackup) { Text("Başka bir yere kaydet (Drive, SD kart…)") }
                }
                TextButton(onClick = { vm.navigate(Screen.Migration) }) {
                    Icon(Icons.Default.CloudDownload, null); Spacer(Modifier.width(4.dp)); Text("Eski “Bağlama Arşivim” uygulamasından aktar")
                }
            }
        }

        SectionTitle("📦 Depolama")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column {
                ListItem(
                    headlineContent = { Text("Kullanılan alan: ${Tr.size(storage.totalBytes)}") },
                    supportingContent = { Text("Video ${Tr.size(storage.videoBytes)} • Belge ${Tr.size(storage.documentBytes)} • Telefonda boş: ${Tr.size(storage.freeDeviceBytes)}") },
                    leadingContent = { Icon(Icons.Default.Storage, null) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                    modifier = Modifier.padding(0.dp)
                )
                HorizontalDivider()
                Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.navigate(Screen.Storage) }, modifier = Modifier.weight(1f)) { Text("Detaylar") }
                    OutlinedButton(onClick = { vm.navigate(Screen.Trash) }, modifier = Modifier.weight(1f)) { Text("Çöp Kutusu") }
                }
            }
        }

        SectionTitle("ℹ️ Hakkında")
        Text("Bağlama Arşivi ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
        Text("Bağlama derslerinin videoları, notaları ve çalışma takibi için kişisel arşiv. Tüm veriler yalnızca telefonunda saklanır; internet gerektirmez.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun MigrationScreen(vm: MainViewModel) {
    val (_, doRestore) = rememberBackupLaunchers(vm)
    Column(Modifier.fillMaxSize()) {
        BackTopBar("Eski verileri aktar", onBack = { vm.back() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Eski “Bağlama Arşivim” uygulaması farklı bir imzayla kurulduğu için veriler kendiliğinden geçemez. " +
                "Aşağıdaki yollardan biriyle bir kez aktarman yeterli; sonraki tüm güncellemeler verilerini koruyarak üstüne kurulur.",
                fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            StepCard("1. yol (önerilen) – AI Studio ile",
                "AI Studio'da eski projeye “Tam Yedek Dışa Aktar” özelliğini ekle (GitHub'daki GECIS_REHBERI dosyasındaki hazır metni Gemini'ye yapıştır), " +
                    "telefona kur, Ayarlar'dan yedeği al. Oluşan .zip dosyasını aşağıdaki butonla seç.")
            StepCard("2. yol – Bilgisayar + USB kablo",
                "Telefonda USB hata ayıklamayı aç, bilgisayarda şu komutu çalıştır:\n\nadb exec-out run-as com.aistudio.baglamaarsivim.zpkrv tar c databases files > eski_arsiv.tar\n\n" +
                    "Oluşan eski_arsiv.tar dosyasını telefona kopyala ve aşağıdaki butonla seç.")
            StepCard("3. yol – Videoları tek tek paylaş",
                "WhatsApp'taki orijinal videolar hâlâ duruyorsa, onları WhatsApp'tan “Paylaş → Bağlama Arşivi” ile yeniden ekleyebilirsin.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = doRestore, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Default.Restore, null); Spacer(Modifier.width(6.dp)); Text("Yedek / eski arşiv dosyasını seç", fontWeight = FontWeight.Bold)
            }
            Text("Aktarım bitince eski uygulamayı kontrol edip silebilirsin.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun StepCard(title: String, text: String) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StorageScreen(vm: MainViewModel) {
    val storage by vm.storage.collectAsStateWithLifecycle()
    val videos by vm.videos.collectAsStateWithLifecycle()
    val internalCount by vm.internalVideos.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshStorage(); vm.refreshInternalVideos() }
    val galleryBytes = videos.filter { com.nesimi.baglamaarsivi.util.VideoStore.isContent(it.localPath) }.sumOf { it.fileSize }
    val biggest = remember(videos) { videos.sortedByDescending { it.fileSize }.take(15) }
    Column(Modifier.fillMaxSize()) {
        BackTopBar("Depolama", onBack = { vm.back() })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(Tr.size(storage.totalBytes), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                        Text("uygulamanın kullandığı alan", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        val tot = (storage.totalBytes + storage.freeDeviceBytes).coerceAtLeast(1)
                        LinearProgressIndicator(progress = { storage.totalBytes.toFloat() / tot }, modifier = Modifier.fillMaxWidth().height(8.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("🎬 Galerideki videolar (Filmler/Bağlama Arşivi): ${Tr.size(galleryBytes)}")
                        Text("📦 Uygulama içindeki videolar: ${Tr.size(storage.videoBytes)}")
                        Text("📄 Belgeler: ${Tr.size(storage.documentBytes)}")
                        Text("🖼️ Önbellek ve küçük resimler: ${Tr.size(storage.cacheBytes)}")
                        Text("📱 Telefonda boş alan: ${Tr.size(storage.freeDeviceBytes)}")
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.clearCache() }) { Text("Önbelleği temizle") }
                            if (com.nesimi.baglamaarsivi.util.VideoStore.galleryEnabled) OutlinedButton(onClick = { vm.syncFolder(force = true) }) { Text("Fazladan kopyaları temizle") }
                        }
                        if (com.nesimi.baglamaarsivi.util.VideoStore.galleryEnabled) Text(
                            "Uygulama videoları kopyalamaz, telefondaki asıl yerinden (ör. WhatsApp klasörü) oynatır. Uygulamadan silinen video telefondan da silinir; telefondan silinen video uygulamadan da kalkar. Yedek Al ile hepsi tek .zip dosyasına toplanır.",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            item { SectionTitle("En büyük videolar") }
            items(biggest, key = { it.id }) { v -> VideoCard(v, onClick = { vm.navigate(Screen.Player(v.id)) }, subtitle = Tr.size(v.fileSize), onDelete = { vm.deleteVideo(v.id) }) }
        }
    }
}

@Composable
fun TrashScreen(vm: MainViewModel) {
    val turkus by vm.deletedTurkus.collectAsStateWithLifecycle()
    val videos by vm.deletedVideos.collectAsStateWithLifecycle()
    val docs by vm.deletedDocs.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmT by remember { mutableStateOf<Turku?>(null) }
    var confirmV by remember { mutableStateOf<VideoItem?>(null) }
    var confirmD by remember { mutableStateOf<DocumentItem?>(null) }
    Column(Modifier.fillMaxSize()) {
        BackTopBar("Çöp Kutusu", onBack = { vm.back() }) {
            IconButton(onClick = { confirmEmpty = true }, enabled = turkus.isNotEmpty() || videos.isNotEmpty() || docs.isNotEmpty()) { Icon(Icons.Default.DeleteSweep, "Boşalt") }
        }
        Text("Silinenler 30 gün saklanır, sonra otomatik temizlenir.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        TabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Türkü (${turkus.size})") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Video (${videos.size})") })
            Tab(tab == 2, { tab = 2 }, text = { Text("Belge (${docs.size})") })
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (tab) {
                0 -> {
                    if (turkus.isEmpty()) item { EmptyState(Icons.Default.Delete, "Boş", "Silinen türkü yok.") }
                    items(turkus, key = { it.id }) { t -> TrashRow(t.name, "Silinme: ${Tr.date(t.deletedAt)}", { vm.restoreTurku(t.id) }, { confirmT = t }) }
                }
                1 -> {
                    if (videos.isEmpty()) item { EmptyState(Icons.Default.Delete, "Boş", "Silinen video yok.") }
                    items(videos, key = { it.id }) { v -> TrashRow(v.title, Tr.size(v.fileSize), { vm.restoreVideo(v.id) }, { confirmV = v }) }
                }
                else -> {
                    if (docs.isEmpty()) item { EmptyState(Icons.Default.Delete, "Boş", "Silinen belge yok.") }
                    items(docs, key = { it.id }) { d -> TrashRow(d.title, d.category, { vm.restoreDoc(d.id) }, { confirmD = d }) }
                }
            }
        }
    }
    if (confirmEmpty) ConfirmDialog("Çöp kutusu boşaltılsın mı?", "Silinenler ve video dosyaları telefondan da kalıcı olarak silinir.", "Boşalt", { vm.emptyTrash() }, { confirmEmpty = false })
    confirmT?.let { t -> ConfirmDialog("Kalıcı silinsin mi?", "“${t.name}” ve videoları telefondan da kalıcı olarak silinir.", "Kalıcı Sil", { vm.deleteTurkuForever(t.id) }, { confirmT = null }) }
    confirmV?.let { v -> ConfirmDialog("Kalıcı silinsin mi?", "“${v.title}” telefondan da kalıcı olarak silinir.", "Kalıcı Sil", { vm.deleteVideoForever(v) }, { confirmV = null }) }
    confirmD?.let { d -> ConfirmDialog("Kalıcı silinsin mi?", "“${d.title}” dosyasıyla birlikte silinir.", "Kalıcı Sil", { vm.deleteDocForever(d) }, { confirmD = null }) }
}

@Composable
private fun TrashRow(title: String, sub: String, onRestore: () -> Unit, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRestore) { Icon(Icons.Default.RestoreFromTrash, "Geri yükle", tint = MaterialTheme.colorScheme.primary) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Kalıcı sil", tint = MaterialTheme.colorScheme.error) }
        }
    }
}


