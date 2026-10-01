package com.nesimi.baglamaarsivi.ui

import android.app.Activity
import android.app.RecoverableSecurityException
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesimi.baglamaarsivi.util.VideoStore
import java.io.File

/**
 * Başka bir uygulamaya ait (ör. WhatsApp) video dosyalarını silmek gerektiğinde:
 *  - "Tüm dosyalara erişim" izni varsa doğrudan siler,
 *  - yoksa telefonun kendi "Silinsin mi?" onay penceresini açar.
 * askFirst=true ise önce uygulama kendi açıklamasını gösterir (kullanıcı "Silme" diyebilir).
 */
@Composable
fun OriginalsHandler(vm: MainViewModel) {
    val req by vm.systemDelete.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) vm.toast("Silindi 🧹") else vm.toast("Silinmedi, dosya telefonda duruyor.")
        VideoStore.checkTick.value++
        vm.clearSystemDelete()
    }

    fun proceed() {
        val uris = req?.uris.orEmpty().filter { VideoStore.exists(context, it.toString()) }
        if (uris.isEmpty()) { vm.clearSystemDelete(); return }
        // 1) Tüm dosyalara erişim varsa doğrudan sil
        if (VideoStore.hasAllFilesAccess()) {
            var n = 0
            uris.forEach { u ->
                val p = VideoStore.dataPath(context, u)
                if (p != null && File(p).delete()) { VideoStore.scanQuietly(context, p); n++ }
                else if (VideoStore.delete(context, u.toString())) n++
            }
            vm.toast("$n dosya silindi 🧹")
            VideoStore.checkTick.value++
            vm.clearSystemDelete()
            return
        }
        // 2) Telefonun onay penceresi
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val pi = MediaStore.createDeleteRequest(context.contentResolver, uris)
                launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } else {
                try {
                    uris.forEach { context.contentResolver.delete(it, null, null) }
                    vm.toast("Silindi 🧹")
                    vm.clearSystemDelete()
                } catch (e: RecoverableSecurityException) {
                    launcher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
                }
            }
        } catch (e: Exception) {
            vm.toast("Silinemedi: ${e.message}")
            vm.clearSystemDelete()
        }
    }

    val r = req ?: return
    if (!r.askFirst) {
        LaunchedEffect(r) { proceed() }
        return
    }
    AlertDialog(
        onDismissRequest = { vm.clearSystemDelete() },
        title = { Text("Çift kopyalar silinsin mi?") },
        text = { Text(r.reason + "\n\nİkinci kopyaları silersen galeride her video bir kez görünür.") },
        confirmButton = { TextButton(onClick = { proceed() }) { Text("Kopyaları Sil", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { vm.declineDuplicates(r.uris) }) { Text("Silme, kalsın") } }
    )
}
