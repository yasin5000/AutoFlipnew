package com.example.autoflip

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var adbHelp: TextView
    private lateinit var logView: TextView
    private val ui = Handler(Looper.getMainLooper())

    private val refresher = object : Runnable {
        override fun run() {
            refreshStatus()
            ui.postDelayed(this, 1500)
        }
    }

    private fun startGuard() {
        try {
            val i = Intent(this, GuardService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Guard start hoyni: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun askBattery() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "Battery permission ager theke deya ache ✔", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun askOverlay() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Display over other apps ager theke deya ache ✔", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun openAutostart() {
        val candidates = listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
        )
        for (c in candidates) {
            try {
                startActivity(Intent().setComponent(c))
                return
            } catch (e: Exception) {
                // porer ta try korbo
            }
        }
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        )
        Toast.makeText(this, "Ekhane Battery → Unrestricted ar Autostart ON korun", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = Prefs(this)
        val pad = (16 * resources.displayMetrics.density).toInt()

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        startGuard()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        setContentView(ScrollView(this).apply { addView(root) })

        fun label(t: String) = TextView(this).apply {
            text = t
            setPadding(0, pad, 0, 0)
        }

        root.addView(TextView(this).apply { text = "AutoFlip v3"; textSize = 24f })

        status = TextView(this)
        root.addView(status)

        root.addView(Button(this).apply {
            text = "1) Accessibility Service ON korun"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        root.addView(Button(this).apply {
            text = "2) Battery 'Unrestricted' permission nin"
            setOnClickListener { askBattery() }
        })

        root.addView(Button(this).apply {
            text = "3) Autostart / App settings kholen"
            setOnClickListener { openAutostart() }
        })

        root.addView(Button(this).apply {
            text = "4) 'Display over other apps' permission"
            setOnClickListener { askOverlay() }
        })

        root.addView(Switch(this).apply {
            text = "Auto re-enable (off hole nije on)"
            isChecked = p.guard
            setOnCheckedChangeListener { _, c -> p.guard = c }
        })

        adbHelp = TextView(this).apply {
            text = "Auto re-enable er jonno PC theke ekbar ei command chalan (USB debugging ON kore):\n\n" +
                "adb shell pm grant com.example.autoflip android.permission.WRITE_SECURE_SETTINGS"
            setTextIsSelectable(true)
            setPadding(0, pad / 2, 0, pad / 2)
        }
        root.addView(adbHelp)

        root.addView(Switch(this).apply {
            text = "Auto flip chalu"
            isChecked = p.enabled
            setOnCheckedChangeListener { _, c -> p.enabled = c }
        })

        root.addView(label("Tigo app er package name (khali = sob app)"))
        val pkg = EditText(this).apply { setText(p.targetPackage); setSingleLine() }
        root.addView(pkg)

        root.addView(label("Call screen chenar text (comma diye)"))
        val markers = EditText(this).apply { setText(p.markers) }
        root.addView(markers)

        root.addView(Switch(this).apply {
            text = "Start button auto click"
            isChecked = p.autoStart
            setOnCheckedChangeListener { _, c -> p.autoStart = c }
        })
        root.addView(label("Start button er text (default: Start)"))
        val startBox = EditText(this).apply { setText(p.startText); setSingleLine() }
        root.addView(startBox)

        val xLabel = label("")
        val yLabel = label("")
        fun updLabels() {
            xLabel.text = "Flip button X: ${(p.xPct * 100).toInt()}%"
            yLabel.text = "Flip button Y: ${(p.yPct * 100).toInt()}%"
        }
        updLabels()

        fun seek(initial: Float, onChange: (Float) -> Unit) = SeekBar(this).apply {
            max = 100
            progress = (initial * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                    onChange(v / 100f); updLabels()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }

        root.addView(xLabel)
        root.addView(seek(p.xPct) { p.xPct = it })
        root.addView(yLabel)
        root.addView(seek(p.yPct) { p.yPct = it })

        root.addView(label("Koto second por flip hobe? (0 = sathe sathe)"))
        val delayBox = EditText(this).apply {
            setText((p.delayMs / 1000.0).toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine()
        }
        root.addView(delayBox)

        root.addView(Button(this).apply {
            text = "Save"
            setOnClickListener {
                p.targetPackage = pkg.text.toString()
                p.markers = markers.text.toString()
                p.startText = startBox.text.toString().ifBlank { "Start" }
                val sec = delayBox.text.toString().toDoubleOrNull() ?: 0.0
                p.delayMs = (sec * 1000).toLong().coerceIn(0L, 10000L)
                Toast.makeText(context, "Saved: ${p.delayMs / 1000.0} sec por flip hobe", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(Button(this).apply {
            text = "Tigo app kholo"
            setOnClickListener {
                p.targetPackage = pkg.text.toString()
                if (!Launcher.openTarget(context)) {
                    Toast.makeText(context, "Tigo khola gelo na. Package name thik ache kina dekhun", Toast.LENGTH_LONG).show()
                }
            }
        })

        root.addView(Button(this).apply {
            text = "Test: ekhon flip tap koro (1 sec por)"
            setOnClickListener {
                Toast.makeText(context, "Tigo call screen e jan, 1 sec por tap hobe", Toast.LENGTH_LONG).show()
                window.decorView.postDelayed({ FlipService.instance?.flipNow() }, 1000)
            }
        })

        root.addView(Button(this).apply {
            text = "Debug: 6 sec por screen er text log e rakho"
            setOnClickListener {
                Toast.makeText(context, "Tigo te jan. 6 sec por screen porbe, tarpor ekhane fire asun", Toast.LENGTH_LONG).show()
                window.decorView.postDelayed({ FlipService.instance?.dumpScreen() }, 6000)
            }
        })

        root.addView(label("Log (notun ta upore)"))
        logView = TextView(this).apply {
            textSize = 11f
            setTextIsSelectable(true)
        }
        root.addView(logView)
    }

    private fun refreshStatus() {
        val listed = ServiceGuard.isEnabled(this)
        val running = FlipService.instance != null
        val canGuard = ServiceGuard.canWrite(this)
        val learned = Prefs(this).learnedPkg

        var t = when {
            listed && running -> "Service: ON ✔ (kaj korche)"
            listed -> "Service: ON kintu chalu hoyni ⚠ (Accessibility theke off/on korun)"
            else -> "Service: OFF ✘ (Accessibility theke AutoFlip chalu korun)"
        }
        t += if (canGuard) "\nAuto re-enable: READY ✔" else "\nAuto re-enable: ADB permission dorkar ✘ (niche dekhun)"
        t += if (Settings.canDrawOverlays(this)) "\nDisplay over apps: ✔" else "\nDisplay over apps: ✘ (button 4)"
        if (learned.isNotBlank()) t += "\nDetected app: $learned"
        status.text = t
        adbHelp.visibility = if (canGuard) View.GONE else View.VISIBLE
        logView.text = AppLog.text()
    }

    override fun onResume() {
        super.onResume()
        ui.removeCallbacks(refresher)
        ui.post(refresher)
    }

    override fun onPause() {
        ui.removeCallbacks(refresher)
        super.onPause()
    }
}
