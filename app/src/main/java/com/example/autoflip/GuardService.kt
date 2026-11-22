package com.example.autoflip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

/** Background e chole, protit 2 second e check kore Accessibility Service ON ache kina. */
class GuardService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var deadTicks = 0

    private val tick = object : Runnable {
        override fun run() {
            try {
                val ctx = this@GuardService
                if (Prefs(ctx).guard && ServiceGuard.canWrite(ctx)) {
                    if (!ServiceGuard.isEnabled(ctx)) {
                        // OFF hoye gese: abar ON
                        ServiceGuard.setEntry(ctx, true)
                        deadTicks = 0
                    } else if (FlipService.instance == null) {
                        // List e ache kintu service chalu hoyni: ~10 sec por off/on kore restart
                        deadTicks++
                        if (deadTicks >= 5) {
                            deadTicks = 0
                            ServiceGuard.setEntry(ctx, false)
                            handler.postDelayed({ ServiceGuard.setEntry(ctx, true) }, 700)
                        }
                    } else {
                        deadTicks = 0
                    }
                }
            } catch (e: Exception) {
                Log.e("AutoFlip", "guard error", e)
            }
            handler.postDelayed(this, 2000)
        }
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
