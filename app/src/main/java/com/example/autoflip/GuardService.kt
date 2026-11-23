package com.example.autoflip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings

/**
 * Background e chole, protit 2 second e check kore Accessibility Service ON ache kina.
 * OFF hole abar ON kore ar Tigo abar khule dey (ADB permission thakle).
 * ADB permission na thakle notification dey, tap korle Accessibility settings khule.
 */
class GuardService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var deadTicks = 0
    private var alerted = false

    private val tick = object : Runnable {
        override fun run() {
            try {
                runCheck()
            } catch (e: Exception) {
                AppLog.add("guard error: " + e.message)
            }
            handler.postDelayed(this, 2000)
        }
    }

    private fun runCheck() {
        val ctx = this
        val prefs = Prefs(ctx)
        if (!prefs.guard) return

        val enabled = ServiceGuard.isEnabled(ctx)

        if (!ServiceGuard.canWrite(ctx)) {
            // ADB permission nai: nije on korte parbo na, shudhu jante dibo
            if (!enabled && !alerted) {
                alerted = true
                showOffAlert()
            }
            if (enabled && alerted) {
                alerted = false
                getSystemService(NotificationManager::class.java).cancel(2)
            }
            return
        }

        if (!enabled) {
            AppLog.add("Accessibility OFF dhora poreche, abar ON kora hocche")
            prefs.relaunch = true
            ServiceGuard.setEntry(ctx, true)
            deadTicks = 0
        } else if (FlipService.instance == null) {
            // List e ache kintu service chalu hoyni: ~10 sec por off/on kore restart
            deadTicks++
            if (deadTicks >= 5) {
                deadTicks = 0
                AppLog.add("Service chalu hoyni, restart kora hocche")
                prefs.relaunch = true
                ServiceGuard.setEntry(ctx, false)
                handler.postDelayed({ ServiceGuard.setEntry(ctx, true) }, 700)
            }
        } else {
            deadTicks = 0
        }
    }

    private fun showOffAlert() {
        val nm = getSystemService(NotificationManager::class.java)
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("alert", "AutoFlip OFF alert", NotificationManager.IMPORTANCE_HIGH)
            )
            Notification.Builder(this, "alert")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = builder
            .setContentTitle("AutoFlip OFF hoye geche")
            .setContentText("Ekhane tap kore Accessibility ON korun")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(2, n)
        AppLog.add("Accessibility OFF, kintu ADB permission nai. Notification dewa hoyeche")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("guard", "AutoFlip Guard", NotificationManager.IMPORTANCE_MIN)
            )
            Notification.Builder(this, "guard")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val n = builder
            .setContentTitle("AutoFlip chalu ache")
            .setContentText("Accessibility service pahara dichhe")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }
}
