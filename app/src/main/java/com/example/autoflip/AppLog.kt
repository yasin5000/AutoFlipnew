package com.example.autoflip

import android.content.Context
import android.content.Intent
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** App er bhitore chhoto log, jate bujha jay ki hocche. */
object AppLog {
    private val lines = ArrayList<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private var last = ""

    @Synchronized
    fun add(msg: String) {
        if (msg == last) return
        last = msg
        lines.add(fmt.format(Date()) + "  " + msg)
        while (lines.size > 150) lines.removeAt(0)
        Log.d("AutoFlip", msg)
    }

    @Synchronized
    fun text(): String =
        if (lines.isEmpty()) "(ekhono kono log nai)" else lines.takeLast(40).reversed().joinToString("\n")
}

/** Tigo app khulbe. */
object Launcher {
    fun openTarget(ctx: Context): Boolean {
        val prefs = Prefs(ctx)
        val pkg = prefs.targetPackage.ifBlank { prefs.learnedPkg }
        if (pkg.isBlank()) {
            AppLog.add("Tigo package name nai, khola gelo na")
            return false
        }
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            AppLog.add("Tigo ($pkg) er launch intent paoa jayni")
            return false
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(intent)
            AppLog.add("Tigo khola hoyeche ($pkg)")
            true
        } catch (e: Exception) {
            AppLog.add("Tigo khola jayni: " + e.message)
            false
        }
    }
}
