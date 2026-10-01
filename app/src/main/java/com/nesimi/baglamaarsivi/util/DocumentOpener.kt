package com.nesimi.baglamaarsivi.util

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/** Belgeyi seçilen uygulamayla açar; bir kez seçilen uygulamayı hatırlar. */
object DocumentOpener {
    const val PREFS = "app_document_prefs"
    const val KEY_EXT = "default_app_ext_"
    const val KEY_DOC = "default_app_doc_"

    fun mime(file: File): String = when (val ext = file.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "txt" -> "text/plain"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        "mp4" -> "video/mp4"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    fun uriFor(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun open(context: Context, file: File, docId: Long, forceChooser: Boolean = false): Boolean {
        if (!file.exists()) return false
        val ext = file.extension.lowercase()
        val type = mime(file)
        return try {
            val uri = uriFor(context, file)
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val saved = if (!forceChooser) prefs.getString("$KEY_DOC$docId", null) ?: prefs.getString("$KEY_EXT$ext", null) else null
            val base = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, type)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (!saved.isNullOrBlank() && installed(context, saved)) {
                context.startActivity(Intent(base).setPackage(saved))
                return true
            }
            val receiver = Intent(context, AppChosenReceiver::class.java).apply {
                putExtra(AppChosenReceiver.EXTRA_EXT, ext)
                putExtra(AppChosenReceiver.EXTRA_DOC, docId)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(context, (ext.hashCode() xor docId.toInt()), receiver, flags)
            val chooser = Intent.createChooser(base, "Belgeyi Aç", pi.intentSender).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun share(context: Context, file: File, title: String): Boolean = try {
        val uri = uriFor(context, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime(file)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }

    fun clearDefault(context: Context, docId: Long, ext: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("$KEY_DOC$docId").remove("$KEY_EXT${ext.lowercase()}").apply()
    }

    @Suppress("DEPRECATION")
    private fun installed(context: Context, pkg: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            context.packageManager.getPackageInfo(pkg, 0)
        }
        true
    } catch (_: Exception) {
        false
    }
}

class AppChosenReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_EXT = "extra_file_extension"
        const val EXTRA_DOC = "extra_doc_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val comp: ComponentName? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT, ComponentName::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT)
        }
        val pkg = comp?.packageName ?: return
        val ext = intent.getStringExtra(EXTRA_EXT)?.lowercase()
        val docId = intent.getLongExtra(EXTRA_DOC, 0L)
        val e = context.getSharedPreferences(DocumentOpener.PREFS, Context.MODE_PRIVATE).edit()
        if (!ext.isNullOrBlank()) e.putString("${DocumentOpener.KEY_EXT}$ext", pkg)
        if (docId > 0) e.putString("${DocumentOpener.KEY_DOC}$docId", pkg)
        e.apply()
    }
}
