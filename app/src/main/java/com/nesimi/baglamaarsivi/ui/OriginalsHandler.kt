package com.nesimi.baglamaarsivi.ui

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesimi.baglamaarsivi.util.VideoStore

/**
 * Videolar galeriye kaydedildikten sonra, WhatsApp'taki (veya başka yerdeki) orijinal kopyaları
 * silmeyi teklif eder. Kullanıcı "Silme" diyebilir; telefon da ayrıca kendi onayını sorar.
 */
@Composable
fun OriginalsHandler(vm: MainViewModel) {
    val pending by vm.pendingOriginals.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val readPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        vm.toast(if (r.resultCode == Activity.RESULT_OK) "Orijinal kopyalar silindi 🧹 Videolar Filmler/Bağlama Arşivi klasöründe." else "Orijinaller silinmedi, telefonda duruyor.")
        vm.clearPendingOriginals()
    }

    fun proceed() {
        val uris = pending.mapNotNull { (name, size) -> VideoStore.findOriginal(context, name, size) }.distinct()
        if (uris.isEmpty()) {
            vm.toast("Orijinal dosyalar bulunamadı (zaten silinmiş ya da farklı bir yerde olabilir).")
            vm.clearPendingOriginals()
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val pi = MediaStore.createDeleteRequest(context.contentResolver, uris)
                deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } else {
                // Android 10: tek tek onay gerekir; ilkini dener
                try {
                    uris.forEach { context.contentResolver.delete(it, null, null) }
                    vm.toast("Orijinal kopyalar silindi 🧹")
                    vm.clearPendingOriginals()
                } catch (e: RecoverableSecurityException) {
                    deleteLauncher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
                }
            }
        } catch (e: Exception) {
            vm.toast("Orijinaller silinemedi: ${e.message}. Elle silebilirsin.")
            vm.clearPendingOriginals()
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) proceed() else {
            vm.toast("İzin verilmedi; orijinaller telefonda duruyor.")
            vm.clearPendingOriginals()
        }
    }

    if (pending.isEmpty()) return
    AlertDialog(
        onDismissRequest = { vm.clearPendingOriginals() },
        title = { Text("Orijinaller silinsin mi?") },
        text = {
            Column {
                Text("${pending.size} video arşive kaydedildi ve telefonun Filmler/Bağlama Arşivi klasörüne konuldu.")
                Text(
                    "\nWhatsApp'taki (veya seçtiğin yerdeki) orijinal kopyaları silersen telefonda yer açılır; video tek kopya olarak arşivde ve galeride kalır.",
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (ContextCompat.checkSelfPermission(context, readPermission) == PackageManager.PERMISSION_GRANTED) proceed()
                else permLauncher.launch(readPermission)
            }) { Text("Orijinalleri Sil", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { vm.clearPendingOriginals() }) { Text("Silme, kalsın") } }
    )
}
