package com.example.autoflip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Phone restart hole guard abar chalu kore. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            val i = Intent(context, GuardService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        } catch (e: Exception) {
            // ignore
        }
    }
}
