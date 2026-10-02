package com.nesimi.baglamaarsivi

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import com.nesimi.baglamaarsivi.data.ArchiveRepository

class BaglamaApp : Application() {
    val repository: ArchiveRepository by lazy { ArchiveRepository(this) }

    override fun onCreate() {
        super.onCreate()
        if (isMainProcess()) safetyCopyOnUpdate()
    }

    private fun isMainProcess(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 28) getProcessName() == packageName else true

    /**
     * Güncelleme güvencesi: uygulamanın yeni sürümü ilk kez açıldığında, veritabanına dokunulmadan ÖNCE
     * kopyası alınır (files/guvenlik). Bir sorun olursa veriler bu kopyadan kurtarılabilir.
     * Son 3 kopya saklanır; videolar kopyalanmaz (sadece küçük veritabanı dosyası).
     */
    private fun safetyCopyOnUpdate() {
        try {
            val prefs = getSharedPreferences("surum", MODE_PRIVATE)
            val current = BuildConfig.VERSION_CODE
            val last = prefs.getInt("son_surum", -1)
            if (last == current) return
            val db = com.nesimi.baglamaarsivi.data.AppDatabase.databaseFile(this)
            if (last != -1 && db.exists()) {
                val dir = java.io.File(filesDir, "guvenlik/v${last}_${System.currentTimeMillis()}").apply { mkdirs() }
                for (suffix in listOf("", "-wal", "-shm")) {
                    val f = java.io.File(db.path + suffix)
                    if (f.exists()) f.copyTo(java.io.File(dir, f.name), overwrite = true)
                }
                java.io.File(filesDir, "guvenlik").listFiles()?.sortedByDescending { it.lastModified() }?.drop(3)?.forEach { it.deleteRecursively() }
            }
            prefs.edit().putInt("son_surum", current).apply()
        } catch (_: Exception) {
        }
    }
}

/**
 * Geri yüklemeden sonra uygulamayı temiz şekilde yeniden başlatır (ayrı süreçte çalışır).
 */
class RestartActivity : Activity() {
    companion object {
        private const val EXTRA_PID = "main_pid"
        fun restart(context: Context) {
            val i = Intent(context, RestartActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_PID, Process.myPid())
            context.startActivity(i)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pid = intent.getIntExtra(EXTRA_PID, -1)
        if (pid > 0) Process.killProcess(pid)
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(launch)
        }
        finish()
        Runtime.getRuntime().exit(0)
    }
}
