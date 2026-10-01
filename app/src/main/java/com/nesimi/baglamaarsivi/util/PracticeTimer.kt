package com.nesimi.baglamaarsivi.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.nesimi.baglamaarsivi.MainActivity
import com.nesimi.baglamaarsivi.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Çalışma sayacı. İki mod:
 *  - İleri sayım (kronometre)
 *  - Geri sayım (ör. 10 dk) -> süre bitince sesli alarm + titreşim (uygulama kapalıyken de)
 * Durum SharedPreferences'ta tutulur; uygulama kapanıp açılsa da kaybolmaz.
 */
data class TimerState(
    val countdown: Boolean = false,
    val targetMs: Long = 0L,
    val accumulatedMs: Long = 0L,
    val runningSince: Long = 0L,
    val turkuId: Long? = null,
    val finished: Boolean = false,
    val startedAt: Long = 0L
) {
    val isActive: Boolean get() = runningSince > 0 || accumulatedMs > 0 || finished
    val isRunning: Boolean get() = runningSince > 0
    fun elapsed(now: Long = System.currentTimeMillis()): Long {
        val e = accumulatedMs + if (runningSince > 0) now - runningSince else 0
        return if (countdown) e.coerceAtMost(targetMs) else e
    }
    fun remaining(now: Long = System.currentTimeMillis()): Long = (targetMs - elapsed(now)).coerceAtLeast(0)
}

object PracticeTimer {
    private const val PREFS = "calisma_sayaci"
    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _state.value = TimerState(
            countdown = p.getBoolean("countdown", false),
            targetMs = p.getLong("target", 0),
            accumulatedMs = p.getLong("acc", 0),
            runningSince = p.getLong("since", 0),
            turkuId = p.getLong("turku", 0).takeIf { it > 0 },
            finished = p.getBoolean("finished", false),
            startedAt = p.getLong("startedAt", 0)
        )
        // Uygulama kapalıyken geri sayım bitmiş olabilir
        val s = _state.value
        if (s.countdown && s.isRunning && s.remaining() <= 0) markFinished(context)
    }

    private fun save(context: Context, s: TimerState) {
        _state.value = s
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("countdown", s.countdown).putLong("target", s.targetMs).putLong("acc", s.accumulatedMs)
            .putLong("since", s.runningSince).putLong("turku", s.turkuId ?: 0).putBoolean("finished", s.finished)
            .putLong("startedAt", s.startedAt).apply()
    }

    fun setTurku(context: Context, id: Long?) = save(context, _state.value.copy(turkuId = id))

    fun start(context: Context, turkuId: Long?, countdownMinutes: Int?) {
        val now = System.currentTimeMillis()
        val s = TimerState(
            countdown = countdownMinutes != null && countdownMinutes > 0,
            targetMs = (countdownMinutes ?: 0) * 60_000L,
            accumulatedMs = 0, runningSince = now, turkuId = turkuId, finished = false, startedAt = now
        )
        save(context, s)
        if (s.countdown) TimerService.start(context, now + s.targetMs)
    }

    fun pause(context: Context) {
        val s = _state.value
        if (!s.isRunning) return
        save(context, s.copy(accumulatedMs = s.elapsed(), runningSince = 0))
        TimerService.stop(context)
    }

    fun resume(context: Context) {
        val s = _state.value
        if (s.isRunning || s.finished) return
        val now = System.currentTimeMillis()
        save(context, s.copy(runningSince = now))
        if (s.countdown) TimerService.start(context, now + s.remaining(now))
    }

    /** Süreyi durdurur ve "kaydet / kaydetme" kararını bekleyen duruma geçer. */
    fun finish(context: Context) {
        val s = _state.value
        save(context, s.copy(accumulatedMs = s.elapsed(), runningSince = 0, finished = true))
        TimerService.stop(context)
    }

    fun markFinished(context: Context) {
        val s = _state.value
        save(context, s.copy(accumulatedMs = s.targetMs, runningSince = 0, finished = true))
    }

    fun reset(context: Context) {
        val keepTurku = _state.value.turkuId
        save(context, TimerState(turkuId = keepTurku))
        TimerService.stop(context)
        TimerService.stopAlarm()
    }
}

/** Geri sayım bitince uygulama kapalı olsa bile alarm çalan ön plan servisi. */
class TimerService : Service() {
    companion object {
        private const val CHANNEL = "calisma_sayaci"
        private const val CHANNEL_ALARM = "calisma_alarm"
        private const val NOTIF_ID = 4101
        private const val NOTIF_DONE = 4102
        private const val EXTRA_END = "end"
        private const val ACTION_STOP_ALARM = "stop_alarm"
        private var player: MediaPlayer? = null
        private var vibrator: Vibrator? = null

        fun start(context: Context, endAt: Long) {
            val i = Intent(context, TimerService::class.java).putExtra(EXTRA_END, endAt)
            try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimerService::class.java))
        }

        fun stopAlarm() {
            try { player?.stop(); player?.release() } catch (_: Exception) {}
            player = null
            try { vibrator?.cancel() } catch (_: Exception) {}
            vibrator = null
        }

        fun channels(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Çalışma sayacı", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ALARM, "Çalışma süresi doldu", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // sesi biz çalıyoruz
            })
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private val finishRunnable = Runnable { onTimeUp() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopAlarm()
            getSystemService(NotificationManager::class.java).cancel(NOTIF_DONE)
            stopSelf()
            return START_NOT_STICKY
        }
        val endAt = intent?.getLongExtra(EXTRA_END, 0L) ?: 0L
        channels(this)
        val notif = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Bağlama çalışması")
            .setContentText("Süre bitince alarm çalacak")
            .setWhen(endAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setContentIntent(openApp())
            .build()
        ServiceCompat.startForeground(
            this, NOTIF_ID, notif,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        handler.removeCallbacks(finishRunnable)
        val delay = (endAt - System.currentTimeMillis()).coerceAtLeast(0)
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BaglamaArsivi:sayac").apply { acquire(delay + 60_000) }
        } catch (_: Exception) {
        }
        handler.postDelayed(finishRunnable, delay)
        return START_NOT_STICKY
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun onTimeUp() {
        PracticeTimer.markFinished(this)
        playAlarm()
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, TimerService::class.java).setAction(ACTION_STOP_ALARM),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("⏰ Çalışma süresi doldu!")
            .setContentText("Kaydetmek için dokun")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .addAction(0, "Alarmı durdur", stopIntent)
            .build()
        try { getSystemService(NotificationManager::class.java).notify(NOTIF_DONE, n) } catch (_: Exception) {}
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // Alarm en fazla 30 sn çalar
        handler.postDelayed({ stopAlarm(); stopSelf() }, 30_000)
    }

    @Suppress("DEPRECATION")
    private fun playAlarm() {
        stopAlarm()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@TimerService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
        }
        try {
            val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            vibrator = v
            val pattern = longArrayOf(0, 600, 400, 600, 400, 600)
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                v.vibrate(pattern, 0)
            }
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(finishRunnable)
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        super.onDestroy()
    }
}
