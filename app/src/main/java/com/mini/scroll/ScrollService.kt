package com.mini.scroll

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView

class ScrollService : AccessibilityService() {

    private val apps = setOf(
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", // TikTok
        "com.instagram.android",
        "com.google.android.youtube",
        "com.twitter.android" // X
    )
    private val h = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var tv: TextView? = null
    private var on = false
    private var count = 0
    private var acc = 0L
    private var t0 = 0L
    private var lastScroll = 0L
    private var leftAt = 0L

    private val tick = object : Runnable {
        override fun run() { draw(); h.postDelayed(this, 1000) }
    }
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = stop()
    }

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val p = e.packageName?.toString() ?: return
        if (e.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            if (p !in apps) return
            start()
            val now = SystemClock.elapsedRealtime()
            if (now - lastScroll > 400) count++ // debounce: 1 swipe = 1 hitungan
            lastScroll = now
            draw()
        } else if (p in apps) start()
        else if (p != packageName && !p.contains("systemui") &&
            !p.contains("inputmethod") && !p.contains("keyboard")) stop()
    }

    private fun start() {
        if (on) return
        val now = SystemClock.elapsedRealtime()
        if (leftAt != 0L && now - leftAt > 300_000L) { count = 0; acc = 0 } // reset bila pergi >5 menit
        on = true
        t0 = now
        show()
        h.post(tick)
    }

    private fun stop() {
        if (!on) return
        on = false
        leftAt = SystemClock.elapsedRealtime()
        acc += leftAt - t0
        h.removeCallbacks(tick)
        tv?.let { v -> runCatching { wm?.removeView(v) } }
        tv = null
    }

    private fun draw() {
        val s = (acc + if (on) SystemClock.elapsedRealtime() - t0 else 0L) / 1000
        tv?.text = "⏱️ %02d:%02d | 📜 %d".format(s / 60, s % 60, count)
    }

    private fun show() {
        if (tv != null) return
        tv = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            setShadowLayer(4f, 1f, 1f, Color.BLACK)
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (28 * resources.displayMetrics.density).toInt()
        }
        runCatching { wm?.addView(tv, lp) }
        draw()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        runCatching { unregisterReceiver(screenOff) }
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}
}
