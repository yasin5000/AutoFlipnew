package com.example.autoflip

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class FlipService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoFlip"
        var instance: FlipService? = null

        // Content description / view id te ei shobdo thakle flip button dhora hobe
        private val FLIP_HINTS = listOf("flip", "switch_camera", "switchcamera", "camera_switch", "rotate", "reverse")
    }

    private val handler = Handler(Looper.getMainLooper())
    private var flippedThisCall = false
    private var pending = false
    private var lastSeen = 0L
    private var lastStartClick = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val prefs = Prefs(this)
        if (!prefs.enabled) return
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (prefs.targetPackage.isNotBlank() && pkg != prefs.targetPackage) return

        val root = rootInActiveWindow ?: return
        val now = System.currentTimeMillis()

        // 1) Call screen: sathe sathe flip
        if (isCallScreen(root, prefs.markers)) {
            // Package name na dile, call screen jei app e dekha gese sheta mone rakhi
            if (prefs.targetPackage.isBlank()) prefs.learnedPkg = pkg
            lastSeen = now
            if (!flippedThisCall && !pending) {
                pending = true
                flippedThisCall = true
                handler.postDelayed({
                    pending = false
                    flipNow()
                }, prefs.delayMs)
            }
            return
        }

        // Call screen na thakle (call sesh)
        if (flippedThisCall && !pending && now - lastSeen > 3000) {
            flippedThisCall = false
        }

        // 2) Home screen: Start button dekhle sathe sathe click
        if (prefs.autoStart) {
            val allowedPkg = prefs.targetPackage.ifBlank { prefs.learnedPkg }
            if (allowedPkg.isNotBlank() && pkg == allowedPkg && now - lastStartClick > 3000) {
                val start = findStartNode(root, prefs.startText)
                if (start != null) {
                    lastStartClick = now
                    flippedThisCall = false
                    Log.d(TAG, "Start button found, clicking")
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

    private fun findStartNode(root: AccessibilityNodeInfo, startText: String): AccessibilityNodeInfo? {
        val wanted = startText.trim()
        if (wanted.isEmpty()) return null
        return root.findAccessibilityNodeInfosByText(wanted).firstOrNull {
            it.isVisibleToUser && it.text?.toString()?.trim().equals(wanted, ignoreCase = true)
        }
    }

    private fun clickNode(node: AccessibilityNodeInfo) {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return

        val r = Rect()
        node.getBoundsInScreen(r)
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
                    Log.d(TAG, "Flip: node click")
                    return
                }
            }
        }
        tapAtSavedPosition()
    }

    private fun findFlipNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val id = node.viewIdResourceName?.lowercase() ?: ""
        if (node.isClickable) {
            Log.d(TAG, "clickable: id=$id desc=$desc class=${node.className}")
        }
        if (FLIP_HINTS.any { desc.contains(it) || id.contains(it) }) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findFlipNode(child)?.let { return it }
        }
        return null
    }

    private fun tapAtSavedPosition() {
        val prefs = Prefs(this)
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(dm)
        tap(dm.widthPixels * prefs.xPct, dm.heightPixels * prefs.yPct)
    }

    private fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        val ok = dispatchGesture(gesture, null, null)
        Log.d(TAG, "tap at ($x, $y) dispatched=$ok")
    }
}
