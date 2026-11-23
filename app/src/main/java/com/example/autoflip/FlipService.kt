package com.example.autoflip

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class FlipService : AccessibilityService() {

    companion object {
        var instance: FlipService? = null

        // Content description / view id te ei shobdo thakle flip button dhora hobe
        private val FLIP_HINTS = listOf("flip", "switch_camera", "switchcamera", "camera_switch", "rotate", "reverse")
        private const val POLL_MS = 400L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var flippedThisCall = false
    private var lastCallSeen = 0L
    private var lastStartClick = 0L

    /** Event er upor nirbhor na kore protit 0.4 sec e screen dekhe. */
    private val poll = object : Runnable {
        override fun run() {
            safeTick()
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AppLog.add("Accessibility service CHALU hoyeche")

        try {
            val i = Intent(this, GuardService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        } catch (e: Exception) {
            AppLog.add("Guard start hoyni: " + e.message)
        }

        // Guard service ke off theke on korle, Tigo abar khule dibo
        val prefs = Prefs(this)
        if (prefs.relaunch) {
            prefs.relaunch = false
            handler.postDelayed({ Launcher.openTarget(this) }, 1500)
        }

        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(poll)
        instance = null
        AppLog.add("Accessibility service BONDHO hoyeche")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        safeTick()
    }

    private fun safeTick() {
        try {
            tick()
        } catch (e: Exception) {
            AppLog.add("tick error: " + e.javaClass.simpleName + " " + e.message)
        }
    }

    private fun tick() {
        val prefs = Prefs(this)
        if (!prefs.enabled) return

        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        if (pkg == packageName) return

        val target = prefs.targetPackage
        if (target.isNotBlank() && pkg != target) return

        val now = System.currentTimeMillis()

        // 1) Call screen: sathe sathe flip
        if (isCallScreen(root, prefs.markers)) {
            if (target.isBlank()) prefs.learnedPkg = pkg
            lastCallSeen = now
            if (!flippedThisCall) {
                flippedThisCall = true
                val d = prefs.delayMs
                AppLog.add("Call screen dekha gese, flip hobe (" + d + " ms por)")
                if (d <= 0L) flipNow() else handler.postDelayed({ flipNow() }, d)
            }
            return
        }

        // Call screen chole gese, porer call er jonno ready
        if (flippedThisCall && now - lastCallSeen > 3000) {
            flippedThisCall = false
            AppLog.add("Call sesh, porer call er jonno ready")
        }

        // 2) Start button dekhle sathe sathe click
        if (prefs.autoStart && now - lastStartClick > 2500) {
            val allowed = target.ifBlank { prefs.learnedPkg }
            if (allowed.isNotBlank() && pkg == allowed) {
                val start = findStartNode(root, prefs.startText)
                if (start != null) {
                    lastStartClick = now
                    flippedThisCall = false
                    AppLog.add("Start button dekha gese, click korchi")
                    clickNode(start)
                }
            }
        }
    }

    private fun isCallScreen(root: AccessibilityNodeInfo, markers: String): Boolean {
        return markers.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .any { root.findAccessibilityNodeInfosByText(it).isNotEmpty() }
    }

    /** text ba contentDescription, duto-i dekhe. */
    private fun findStartNode(root: AccessibilityNodeInfo, startText: String): AccessibilityNodeInfo? {
        val wanted = startText.trim()
        if (wanted.isEmpty()) return null
        val list = root.findAccessibilityNodeInfosByText(wanted)
        for (n in list) {
            val t = n.text?.toString()?.trim() ?: ""
            val c = n.contentDescription?.toString()?.trim() ?: ""
            if (t.equals(wanted, ignoreCase = true) || c.equals(wanted, ignoreCase = true)) return n
        }
        return null
    }

    private fun clickNode(node: AccessibilityNodeInfo) {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            AppLog.add("Start: node click hoyeche")
            return
        }
        val r = Rect()
        node.getBoundsInScreen(r)
        AppLog.add("Start: coordinate tap (" + r.centerX() + ", " + r.centerY() + ")")
        tap(r.exactCenterX(), r.exactCenterY())
    }

    /** Flip button e click kore. Age node khuje, na paile coordinate e tap. */
    fun flipNow() {
        val root = rootInActiveWindow
        if (root != null) {
            val node = findFlipNode(root)
            if (node != null) {
                var target: AccessibilityNodeInfo? = node
                while (target != null && !target.isClickable) target = target.parent
                if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    AppLog.add("Flip: button node click hoyeche")
                    return
                }
            }
        }
        tapAtSavedPosition()
    }

    private fun findFlipNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val id = node.viewIdResourceName?.lowercase() ?: ""
        if (FLIP_HINTS.any { desc.contains(it) || id.contains(it) }) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFlipNode(child)
            if (found != null) return found
        }
        return null
    }

    private fun tapAtSavedPosition() {
        val prefs = Prefs(this)
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(dm)
        val x = dm.widthPixels * prefs.xPct
        val y = dm.heightPixels * prefs.yPct
        AppLog.add("Flip: saved position e tap (" + x.toInt() + ", " + y.toInt() + ")")
        tap(x, y)
    }

    private fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        val ok = dispatchGesture(gesture, null, null)
        if (!ok) AppLog.add("tap dispatch hoyni (gesture permission check korun)")
    }

    /** Debug: ekhon je screen khola ache tar text gulo log e rakhe. */
    fun dumpScreen() {
        val root = rootInActiveWindow
        if (root == null) {
            AppLog.add("DUMP: screen er data paoa jayni")
            return
        }
        AppLog.add("DUMP app=" + root.packageName)
        var count = 0
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null || count >= 50) return
            val t = n.text?.toString()
            val d = n.contentDescription?.toString()
            if (!t.isNullOrBlank() || !d.isNullOrBlank() || n.isClickable) {
                count++
                val cls = n.className?.toString()?.substringAfterLast('.') ?: "?"
                AppLog.add("  " + cls + " t=" + t + " d=" + d + " id=" + n.viewIdResourceName + " click=" + n.isClickable)
            }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
    }
}
