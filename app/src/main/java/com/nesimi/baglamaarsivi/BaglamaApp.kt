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
