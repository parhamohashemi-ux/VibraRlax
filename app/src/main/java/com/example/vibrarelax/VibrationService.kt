package com.example.vibrarelax

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.*
import androidx.core.app.NotificationCompat

/** Shared, in-process state so the UI can observe the running session. */
object Session {
    var running = false
    var remainingSec = -1          // -1 = no timer
    var listener: (() -> Unit)? = null
    fun notifyChanged() { Handler(Looper.getMainLooper()).post { listener?.invoke() } }
}

class VibrationService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_UPDATE = "update"
        const val ACTION_STOP = "stop"
        const val EXTRA_PATTERN = "pattern"
        const val EXTRA_INTENSITY = "intensity"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_TARGET = "target" // 0 phone, 1 controller, 2 both
        private const val CHANNEL = "session"
        private const val NOTIF_ID = 1
    }

    private lateinit var vibrator: Vibrator
    private val handler = Handler(Looper.getMainLooper())
    private var pattern = "Continuous"
    private var intensity = 60
    private var target = 0
    private var controllers: List<VibratorManager> = emptyList()

    private val tick = object : Runnable {
        override fun run() {
            if (Session.remainingSec > 0) {
                Session.remainingSec--
                Session.notifyChanged()
                if (Session.remainingSec == 0) { stopSession(); return }
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                pattern = intent.getStringExtra(EXTRA_PATTERN) ?: pattern
                intensity = intent.getIntExtra(EXTRA_INTENSITY, intensity)
                target = intent.getIntExtra(EXTRA_TARGET, target)
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
                goForeground()
                Session.running = true
                Session.remainingSec = if (minutes > 0) minutes * 60 else -1
                Session.notifyChanged()
                vibrate()
                handler.removeCallbacks(tick)
                handler.postDelayed(tick, 1000)
            }
            ACTION_UPDATE -> {
                pattern = intent.getStringExtra(EXTRA_PATTERN) ?: pattern
                intensity = intent.getIntExtra(EXTRA_INTENSITY, intensity)
                target = intent.getIntExtra(EXTRA_TARGET, target)
                if (Session.running) vibrate()
            }
            ACTION_STOP -> stopSession()
        }
        return START_NOT_STICKY
    }

    private fun cancelAll() {
        vibrator.cancel()
        if (Build.VERSION.SDK_INT >= 31) controllers.forEach { try { it.cancel() } catch (_: Exception) {} }
    }

    private fun vibrate() {
        val (t, a) = Patterns.build(pattern, intensity)
        val effect = VibrationEffect.createWaveform(t, a, 0)
        cancelAll()
        if (target == 0 || target == 2) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, attrs)
        }
        if (target == 1 || target == 2) {
            controllers = Controllers.find()
            if (Build.VERSION.SDK_INT >= 31) {
                controllers.forEach {
                    try { it.vibrate(CombinedVibration.createParallel(effect)) } catch (_: Exception) {}
                }
            }
        }
    }

    private fun stopSession() {
        handler.removeCallbacks(tick)
        cancelAll()
        Session.running = false
        Session.remainingSec = -1
        Session.notifyChanged()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Massage session", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, VibrationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("VibraRelax")
            .setContentText("Session running")
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        cancelAll()
        Session.running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
