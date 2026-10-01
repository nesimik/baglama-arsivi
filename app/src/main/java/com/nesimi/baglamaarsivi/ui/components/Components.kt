package com.nesimi.baglamaarsivi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import coil.compose.AsyncImage
import com.nesimi.baglamaarsivi.data.DocumentItem
import com.nesimi.baglamaarsivi.data.StudyStatus
import com.nesimi.baglamaarsivi.data.TurkuWithDetails
import com.nesimi.baglamaarsivi.data.VideoItem
import com.nesimi.baglamaarsivi.ui.BusyState
import com.nesimi.baglamaarsivi.ui.theme.FavGold
import com.nesimi.baglamaarsivi.ui.theme.LocalIsDark
import com.nesimi.baglamaarsivi.ui.theme.StatusBlue
import com.nesimi.baglamaarsivi.ui.theme.StatusGreen
import com.nesimi.baglamaarsivi.ui.theme.StatusYellow
import com.nesimi.baglamaarsivi.util.FileManager
import com.nesimi.baglamaarsivi.util.SectionColors
import com.nesimi.baglamaarsivi.util.Tr

fun statusColor(s: StudyStatus): Color = when (s) {
    StudyStatus.CALISIYORUM -> StatusBlue
    StudyStatus.OGRENILECEK -> StatusYellow
    StudyStatus.OGRENILDI -> StatusGreen
}

@Composable
fun StatusBadge(status: StudyStatus, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = statusColor(status)
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(50),
        color = c.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, c.copy(alpha = 0.45f))
    ) {
        Text(
            status.displayName,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            color = c,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(12.dp)); action() }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Clear, "Temizle") } },
        singleLine = true,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun Thumbnail(path: String?, modifier: Modifier = Modifier, durationMs: Long = 0, progress: Float = 0f) {
    val context = LocalContext.current
    val file = remember(path) { FileManager.resolve(context, path) }
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (file != null) {
            AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center).size(28.dp))
        }
        if (durationMs > 0) {
            Text(
                Tr.duration(durationMs),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
        if (progress > 0.02f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun TurkuCard(item: TurkuWithDetails, compact: Boolean, onClick: () -> Unit, onFavorite: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val t = item.turku
    val st = StudyStatus.fromString(t.status)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(if (compact) 36.dp else 46.dp).clip(RoundedCornerShape(12.dp)).background(statusColor(st).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(t.name.take(1).uppercase(Tr.LOCALE), fontWeight = FontWeight.Bold, color = statusColor(st), fontSize = if (compact) 16.sp else 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                val sub = listOf(t.region, t.makam, t.artist).filter { it.isNotBlank() }.joinToString(" • ")
                if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!compact) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusBadge(st)
                        Text("🎬 ${item.videoCount}  📄 ${item.documentCount}" + if (item.practiceSec > 0) "  ⏱ ${Tr.practice(item.practiceSec)}" else "",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            IconButton(onClick = onFavorite) {
                Icon(if (t.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder, "Favori", tint = if (t.isFavorite) FavGold else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.invoke()
        }
    }
}

/** Video kartı – zeminler açık renkli, bölüm rengi yalnızca hafif bir tonda. */
@Composable
fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    subtitle: String? = null,
    onOrderClick: (() -> Unit)? = null,
    onFavorite: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onOrderChange: ((String) -> Unit)? = null,
    orderSuggestion: String = ""
) {
    val dark = LocalIsDark.current
    val style = SectionColors.forTag(video.displayOrderTag, dark)
    val progress = if (video.durationMs > 0) (video.lastPlaybackPositionMs.toFloat() / video.durationMs).coerceIn(0f, 1f) else 0f
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = style.container),
        border = BorderStroke(if (isPlaying) 2.dp else 1.dp, if (isPlaying) MaterialTheme.colorScheme.primary else style.border)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(video.thumbnailPath, Modifier.width(112.dp).height(64.dp), video.durationMs, progress)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val tag = video.displayOrderTag
                    if (onOrderChange != null) {
                        OrderTagField(tag, orderSuggestion, style.badgeBg, style.badgeText, onOrderChange)
                    } else Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = style.badgeBg,
                        modifier = if (onOrderClick != null) Modifier.clickable { onOrderClick() } else Modifier
                    ) {
                        Row(Modifier.padding(horizontal = 6.dp, vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (tag.isBlank()) Icon(Icons.Default.Tag, null, Modifier.size(12.dp), tint = style.badgeText)
                            Text(if (tag.isBlank()) "Sıra" else "#$tag", color = style.badgeText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (isPlaying) {
                        Spacer(Modifier.width(6.dp))
                        Text("▶ Oynatılıyor", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(video.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                val info = subtitle ?: listOf(video.lessonDate.takeIf { it.isNotBlank() }?.let { "📅 $it" }, video.instructor.takeIf { it.isNotBlank() }?.let { "👤 $it" }).filterNotNull().joinToString("  ")
                if (info.isNotBlank()) Text(info, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onFavorite != null) {
                IconButton(onClick = onFavorite) {
                    Icon(if (video.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder, "Favori", tint = if (video.isFavorite) FavGold else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (onEdit != null || onDelete != null || onOrderClick != null) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Diğer") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (onOrderClick != null) DropdownMenuItem(text = { Text("Sıra numarası ver") }, leadingIcon = { Icon(Icons.Default.Tag, null) }, onClick = { menu = false; onOrderClick() })
                        if (onEdit != null) DropdownMenuItem(text = { Text("Bilgileri düzenle") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEdit() })
                        if (onDelete != null) DropdownMenuItem(text = { Text("Sil") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
    }
}

fun docIcon(type: String): ImageVector = when (type.uppercase()) {
    "PDF" -> Icons.Default.PictureAsPdf
    "PNG", "JPG", "JPEG", "WEBP", "HEIC" -> Icons.Default.Image
    "MP3", "M4A", "AAC", "OGG", "OPUS", "WAV" -> Icons.Default.MusicNote
    else -> Icons.Outlined.Description
}

@Composable
fun DocumentCard(
    doc: DocumentItem,
    turkuName: String?,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onChooseApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(docIcon(doc.fileType), null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(doc.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                val info = listOfNotNull(doc.category, doc.fileType.uppercase(), Tr.size(doc.fileSize).takeIf { doc.fileSize > 0 }, turkuName?.let { "🎵 $it" }).joinToString(" • ")
                Text(info, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onFavorite) {
                Icon(if (doc.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder, "Favori", tint = if (doc.isFavorite) FavGold else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Diğer") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Farklı uygulamayla aç") }, onClick = { menu = false; onChooseApp() })
                    DropdownMenuItem(text = { Text("Paylaş") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { menu = false; onShare() })
                    DropdownMenuItem(text = { Text("Düzenle / türküye bağla") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Sil") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, onClick: (() -> Unit)? = null) {
    Card(
        modifier = modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = color, maxLines = 1)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = true) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

@Composable
fun OrderTagDialog(current: String, title: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sıra Numarası") },
        text = {
            Column {
                Text("“$title”", fontWeight = FontWeight.SemiBold, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                Text("Noktalı numara da verebilirsiniz: 1, 1.2, 2, 2/1, 2,2 … Aynı ilk sayıya sahip videolar aynı renkte (bölüm) görünür.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    label = { Text("Sıra no") }, placeholder = { Text("ör. 2.1") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text); onDismiss() }) { Text("Kaydet", fontWeight = FontWeight.Bold) } },
        dismissButton = {
            Row {
                if (current.isNotBlank()) TextButton(onClick = { onSave(""); onDismiss() }) { Text("Kaldır", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Vazgeç") }
            }
        }
    )
}

@Composable
fun BusyOverlay(state: BusyState) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(enabled = true, onClick = {}), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.padding(32.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val p = state.progress
                if (p != null && p.total > 0) {
                    CircularProgressIndicator(progress = { p.fraction })
                    Spacer(Modifier.height(12.dp))
                    Text("%" + (p.fraction * 100).toInt(), fontWeight = FontWeight.Bold)
                } else {
                    CircularProgressIndicator()
                }
                Spacer(Modifier.height(12.dp))
                Text(state.title, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                if (p != null && p.label.isNotBlank()) {
                    Text(p.label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}


/**
 * Kartın üstünde doğrudan yazılabilen sıra numarası kutusu (ör. 1.1, 1.2, 2.1).
 * Klavyede "Tamam"a basınca veya kutudan çıkınca kaydedilir; video otomatik olarak yerine kayar.
 */
@Composable
fun OrderTagField(tag: String, suggestion: String, bg: Color, fg: Color, onCommit: (String) -> Unit) {
    val focus = LocalFocusManager.current
    var value by remember(tag) { mutableStateOf(TextFieldValue(tag)) }
    var focused by remember { mutableStateOf(false) }
    var prefill by remember { mutableStateOf<String?>(null) }
    fun normalized() = value.text.trim().replace(',', '.').removePrefix("#").trim()
    fun commit() {
        val n = normalized()
        if (n != tag) onCommit(n)
    }
    BasicTextField(
        value = value,
        onValueChange = { v -> value = v.copy(text = v.text.filter { it.isDigit() || it == '.' || it == ',' || it == '/' || it == '-' }.take(8)) },
        singleLine = true,
        textStyle = TextStyle(color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit(); focus.clearFocus() }),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(fg),
        modifier = Modifier
            .width(64.dp)
            .background(bg, RoundedCornerShape(8.dp))
            .border(if (focused) 2.dp else 0.dp, if (focused) fg else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 5.dp)
            .onFocusChanged { st ->
                if (st.isFocused && !focused) {
                    if (value.text.isBlank() && suggestion.isNotBlank()) {
                        prefill = suggestion
                        value = TextFieldValue(suggestion, TextRange(suggestion.length))
                    }
                } else if (!st.isFocused && focused) {
                    // Önerilen numaraya hiç dokunulmadıysa kaydetme
                    if (prefill != null && value.text == prefill) value = TextFieldValue(tag) else commit()
                    prefill = null
                }
                focused = st.isFocused
            },
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center) {
                if (value.text.isEmpty()) Text("No", color = fg.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                inner()
            }
        }
    )
}
