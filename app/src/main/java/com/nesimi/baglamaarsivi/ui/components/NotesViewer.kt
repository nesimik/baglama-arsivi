package com.nesimi.baglamaarsivi.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nesimi.baglamaarsivi.data.DocumentItem
import com.nesimi.baglamaarsivi.util.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val IMAGE_TYPES = setOf("PNG", "JPG", "JPEG", "WEBP", "GIF", "BMP", "HEIC", "HEIF")

fun DocumentItem.isViewableNote(): Boolean {
    val t = fileType.uppercase()
    return t == "PDF" || t in IMAGE_TYPES
}

/**
 * Video oynatırken nota gösteren panel.
 * - İki parmakla yakınlaştır / kaydır, çift dokunuşla büyüt-küçült, +/−/sığdır düğmeleri
 * - Birden fazla nota varsa sekmeler, PDF'de sayfa değiştirme
 */
@Composable
fun NotesPanel(
    docs: List<DocumentItem>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    maximized: Boolean,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
    onAddNote: () -> Unit,
    onOpenExternal: (DocumentItem) -> Unit,
    modifier: Modifier = Modifier,
    onSwapSide: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val doc = docs.firstOrNull { it.id == selectedId } ?: docs.firstOrNull()
    // Yakınlaştırma durumu (nota değişince sıfırlanır)
    var scale by remember(doc?.id) { mutableFloatStateOf(1f) }
    var offset by remember(doc?.id) { mutableStateOf(Offset.Zero) }
    var page by remember(doc?.id) { mutableIntStateOf(0) }
    var pageCount by remember(doc?.id) { mutableIntStateOf(1) }

    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        // Üst çubuk
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🎼 Nota", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { scale = (scale / 1.4f).coerceAtLeast(1f); if (scale == 1f) offset = Offset.Zero }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Remove, "Küçült")
            }
            IconButton(onClick = { scale = (scale * 1.4f).coerceAtMost(6f) }, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Add, "Büyüt") }
            IconButton(onClick = { scale = 1f; offset = Offset.Zero }, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.FitScreen, "Sığdır") }
            if (onSwapSide != null) IconButton(onClick = onSwapSide, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.SwapHoriz, "Sağa/sola al") }
            IconButton(onClick = onToggleMaximize, modifier = Modifier.size(38.dp)) {
                Icon(if (maximized) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull, if (maximized) "Küçült" else "Tam ekran nota")
            }
            IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Close, "Notayı kapat") }
        }
        if (docs.size > 1) {
            LazyRow(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(docs, key = { it.id }) { d ->
                    FilterChip(
                        selected = d.id == doc?.id, onClick = { onSelect(d.id) },
                        label = { Text(d.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, modifier = Modifier.width(120.dp)) }
                    )
                }
            }
        }

        if (doc == null) {
            Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Bu türküye henüz nota eklenmemiş.", fontWeight = FontWeight.SemiBold)
                Text("Notanın fotoğrafını veya PDF'ini ekleyince burada görünür.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onAddNote) { Text("Nota ekle") }
            }
            return@Column
        }

        val file = remember(doc.localPath) { FileManager.resolve(context, doc.localPath) }
        val type = doc.fileType.uppercase()
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().clipToBounds().background(Color.White)
                .pointerInput(doc.id) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 6f)
                        // Parmakların ortasına doğru yakınlaştır
                        val c = centroid - Offset(size.width / 2f, size.height / 2f)
                        var o = (offset + c) * (newScale / scale) - c + pan
                        val maxX = (size.width * (newScale - 1f)) / 2f
                        val maxY = (size.height * (newScale - 1f)) / 2f
                        o = Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
                        scale = newScale
                        offset = if (newScale == 1f) Offset.Zero else o
                    }
                }
                .pointerInput(doc.id) {
                    detectTapGestures(onDoubleTap = { tap ->
                        if (scale > 1.05f) { scale = 1f; offset = Offset.Zero }
                        else {
                            val target = 2.5f
                            val c = tap - Offset(size.width / 2f, size.height / 2f)
                            val maxX = (size.width * (target - 1f)) / 2f
                            val maxY = (size.height * (target - 1f)) / 2f
                            scale = target
                            offset = Offset((-c.x * (target - 1f)).coerceIn(-maxX, maxX), (-c.y * (target - 1f)).coerceIn(-maxY, maxY))
                        }
                    })
                }
        ) {
            val zoomMod = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            }
            when {
                file == null -> Text("Dosya bulunamadı", color = Color.Black, modifier = Modifier.align(Alignment.Center))
                type in IMAGE_TYPES -> AsyncImage(model = file, contentDescription = doc.title, contentScale = ContentScale.Fit, modifier = zoomMod)
                type == "PDF" -> {
                    val widthPx = constraints.maxWidth.coerceIn(600, 2000)
                    PdfPage(file, page, widthPx, onPageCount = { pageCount = it }, modifier = zoomMod)
                }
                else -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Bu dosya türü burada gösterilemiyor (${doc.fileType}).", color = Color.Black)
                    OutlinedButton(onClick = { onOpenExternal(doc) }) { Text("Uygulamada aç") }
                }
            }
            if (scale > 1.05f) {
                Text(
                    "${(scale * 100).toInt()}%", color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            if (type == "PDF" && pageCount > 1) {
                Surface(
                    shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (page > 0) { page--; scale = 1f; offset = Offset.Zero } }, enabled = page > 0) {
                            Icon(Icons.AutoMirrored.Filled.NavigateBefore, "Önceki sayfa", tint = Color.White)
                        }
                        Text("${page + 1} / $pageCount", color = Color.White, fontSize = 13.sp)
                        IconButton(onClick = { if (page < pageCount - 1) { page++; scale = 1f; offset = Offset.Zero } }, enabled = page < pageCount - 1) {
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, "Sonraki sayfa", tint = Color.White)
                        }
                    }
                }
            }
        }
        if (!maximized) {
            Text(
                "İki parmakla büyüt/küçült • çift dokun: yakınlaştır",
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

/** PDF'in bir sayfasını yüksek çözünürlükte resme çevirip gösterir. */
@Composable
private fun PdfPage(file: File, page: Int, widthPx: Int, onPageCount: (Int) -> Unit, modifier: Modifier) {
    var bitmap by remember(file.path, page) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(file.path) { mutableStateOf(false) }
    LaunchedEffect(file.path, page, widthPx) {
        val result = withContext(Dispatchers.IO) {
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                try {
                    val count = renderer.pageCount
                    val idx = page.coerceIn(0, (count - 1).coerceAtLeast(0))
                    val p = renderer.openPage(idx)
                    try {
                        // Büyütmede netlik için 2 kat çözünürlük
                        val w = (widthPx * 2).coerceAtMost(3000)
                        val h = (w.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        count to bmp.asImageBitmap()
                    } finally {
                        p.close()
                    }
                } finally {
                    renderer.close()
                    pfd.close()
                }
            } catch (_: Exception) {
                null
            }
        }
        if (result == null) error = true else { onPageCount(result.first); bitmap = result.second }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val b = bitmap
        when {
            error -> Text("PDF açılamadı", color = Color.Black)
            b == null -> CircularProgressIndicator()
            else -> Image(bitmap = b, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
}
