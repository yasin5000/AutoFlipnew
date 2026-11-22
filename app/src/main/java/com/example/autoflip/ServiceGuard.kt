package com.example.autoflip

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Accessibility service ON/OFF kora. WRITE_SECURE_SETTINGS permission lage,
 * jeta PC theke ekbar adb diye dite hoy:
 * adb shell pm grant com.example.autoflip android.permission.WRITE_SECURE_SETTINGS
 */
object ServiceGuard {

    fun canWrite(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private fun names(ctx: Context): Pair<String, String> {
        val cn = ComponentName(ctx, FlipService::class.java)
        return cn.flattenToString() to cn.flattenToShortString()
    }

    fun isEnabled(ctx: Context): Boolean {
        val (full, short) = names(ctx)
        val cur = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val flag = Settings.Secure.getInt(ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        return flag && cur.split(":").any { it.equals(full, true) || it.equals(short, true) }
    }

    /** on = true hole list e jure dey, false hole list theke soray. */
    fun setEntry(ctx: Context, on: Boolean): Boolean {
        if (!canWrite(ctx)) return false
        val (full, short) = names(ctx)
        val cur = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val list = cur.split(":")
            .filter { it.isNotBlank() && !it.equals(full, true) && !it.equals(short, true) }
            .toMutableList()
        if (on) list.add(full)
        Settings.Secure.putString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list.joinToString(":"))
        if (on) Settings.Secure.putInt(ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        return true
    }
}
