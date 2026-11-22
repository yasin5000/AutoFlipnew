package com.example.autoflip

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = Prefs(this)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        setContentView(ScrollView(this).apply { addView(root) })

        fun label(t: String) = TextView(this).apply {
            text = t
            setPadding(0, pad, 0, 0)
        }

        root.addView(TextView(this).apply { text = "AutoFlip"; textSize = 24f })

        status = TextView(this)
        root.addView(status)

        root.addView(Button(this).apply {
            text = "1) Accessibility Service ON korun"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

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
            text = "Test: ekhon flip tap koro (1 sec por)"
            setOnClickListener {
                Toast.makeText(context, "Tigo call screen e jan, 1 sec por tap hobe", Toast.LENGTH_LONG).show()
                window.decorView.postDelayed({ FlipService.instance?.flipNow() }, 1000)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(packageName) == true
        val learned = Prefs(this).learnedPkg
        val base = if (enabled) "Service: ON ✔" else "Service: OFF ✘ (Accessibility theke AutoFlip chalu korun)"
        status.text = if (learned.isNotBlank()) "$base\nDetected app: $learned" else base
    }
}
