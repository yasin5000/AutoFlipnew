package com.example.autoflip

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("autoflip", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    /** Khali rakhle sob app e kaj korbe. Tigo er package name dile shudhu oi app e. */
    var targetPackage: String
        get() = sp.getString("pkg", "") ?: ""
        set(v) = sp.edit().putString("pkg", v.trim()).apply()

    /** Comma diye alada kora text; screen e ei text paoa gele "call interface" dhora hobe. */
    var markers: String
        get() = sp.getString("markers", "Love this user,Something to say") ?: ""
        set(v) = sp.edit().putString("markers", v).apply()

    /** Flip button er position (screen er % hisebe, 0..1). */
    var xPct: Float
        get() = sp.getFloat("x", 0.93f)
        set(v) = sp.edit().putFloat("x", v).apply()

    var yPct: Float
        get() = sp.getFloat("y", 0.80f)
        set(v) = sp.edit().putFloat("y", v).apply()

    /** Interface dekha dewar koto ms pore tap korbe. 0 = sathe sathe. */
    var delayMs: Long
        get() = sp.getLong("delay", 0L)
        set(v) = sp.edit().putLong("delay", v).apply()

    /** Home screen e Start button dekhle auto click. */
    var autoStart: Boolean
        get() = sp.getBoolean("autoStart", true)
        set(v) = sp.edit().putBoolean("autoStart", v).apply()

    var startText: String
        get() = sp.getString("startText", "Start") ?: "Start"
        set(v) = sp.edit().putString("startText", v.trim()).apply()

    /** Call screen jei app e dekha gese (package name dewa na thakle auto mone rakhe). */
    var learnedPkg: String
        get() = sp.getString("learnedPkg", "") ?: ""
        set(v) = sp.edit().putString("learnedPkg", v).apply()
}
