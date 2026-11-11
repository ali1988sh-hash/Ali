package com.example.fvgalert

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FvgService : Service() {
    companion object {
        @Volatile var running = false
    }

    private var thread: Thread? = null
    private var wl: PowerManager.WakeLock? = null
    private var alertId = 100

    private fun nm() = getSystemService(NotificationManager::class.java)

    private fun pi() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
    )

    private fun statusNotif(text: String): Notification =
        Notification.Builder(this, "status")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("FVG Alert — XAU/USD")
            .setContentText(text)
            .setContentIntent(pi())
            .setOngoing(true)
            .build()

    private fun alert(msg: String) {
        val n = Notification.Builder(this, "alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("FVG Alert")
            .setContentText(msg)
            .setStyle(Notification.BigTextStyle().bigText(msg))
            .setContentIntent(pi())
            .setAutoCancel(true)
            .build()
        nm().notify(alertId++, n)
    }

    override fun onCreate() {
        super.onCreate()
        nm().createNotificationChannel(NotificationChannel("status", "وضعیت", NotificationManager.IMPORTANCE_LOW))
        nm().createNotificationChannel(
            NotificationChannel("alerts", "هشدارها", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (thread != null) return START_STICKY
        startForeground(1, statusNotif("در حال راه‌اندازی…"))
        running = true
        wl = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fvgalert:poll").apply { acquire() }

        val p = getSharedPreferences("cfg", MODE_PRIVATE)
        val poll = (p.getString("poll", "150")?.toLongOrNull() ?: 150L).coerceAtLeast(60L)
        val tf = p.getString("tf", "15")?.toIntOrNull() ?: 15
        val eng = Engine(
            p.getString("key", "") ?: "", tf,
            p.getString("min", "0")?.toDoubleOrNull() ?: 0.0,
            p.getBoolean("touch", true), p.getBoolean("new", false)
        ) { alert(it) }

        thread = Thread {
            var first = true
            var next = 0L
            val step = tf * 60000L
            val clock = SimpleDateFormat("HH:mm:ss", Locale.US)
            while (running) {
                try {
                    if (first || System.currentTimeMillis() >= next) {
                        eng.refresh(first)
                        next = (System.currentTimeMillis() / step + 1) * step + 5000
                    }
                    eng.check(first)
                    first = false
                    nm().notify(1, statusNotif(
                        String.format(Locale.US, "%s — فعال: %d — قیمت: %.2f",
                            clock.format(Date()), eng.activeCount(), eng.price)
                    ))
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    nm().notify(1, statusNotif("خطا: ${e.message}"))
                }
                try { Thread.sleep(poll * 1000) } catch (e: InterruptedException) { break }
            }
        }.also { it.start() }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        thread?.interrupt()
        thread = null
        wl?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null
}
