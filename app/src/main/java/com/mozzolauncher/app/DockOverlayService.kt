package com.mozzolauncher.app

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DockOverlayService : AccessibilityService() {

    companion object {
        var instance: DockOverlayService? = null
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: PrefsManager
    private lateinit var repo: AppRepository
    private var rootView: LinearLayout? = null
    private var handleView: View? = null
    private var panelView: LinearLayout? = null
    private var recyclerView: RecyclerView? = null
    private var isExpanded = false
    private val handler = Handler(Looper.getMainLooper())
    private val autoHideRunnable = Runnable {
        if (prefs.overlayMode == "HIDDEN" && isExpanded) {
            setExpanded(false)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = PrefsManager(this)
        repo = AppRepository(this)
        buildOverlay()
        refreshConfig()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) {
            rootView?.visibility = View.GONE
        } else if (prefs.overlayMode != "OFF") {
            rootView?.visibility = View.VISIBLE
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        rootView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildOverlay() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val handle = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(110))
            setBackgroundResource(R.drawable.bg_handle_left)
            isFocusable = true
            setOnClickListener {
                setExpanded(!isExpanded)
            }
            setOnHoverListener { _, event ->
                if (event.action == MotionEvent.ACTION_HOVER_ENTER && !isExpanded) {
                    setExpanded(true)
                }
                false
            }
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_UP) {
                    setExpanded(!isExpanded)
                    true
                } else false
            }
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundResource(R.drawable.bg_dock_left)
            setPadding(dp(8), dp(12), dp(8), dp(12))
        }

        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@DockOverlayService, RecyclerView.VERTICAL, false)
        }
        panel.addView(rv)

        val btnHome = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { topMargin = dp(8) }
            setBackgroundResource(android.R.color.transparent)
            setImageResource(R.drawable.ic_apps_grid)
            setOnClickListener {
                setExpanded(false)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
        panel.addView(btnHome)

        container.addView(handle)
        container.addView(panel)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            x = 0
            y = 0
        }

        wm.addView(container, params)
        rootView = container
        handleView = handle
        panelView = panel
        recyclerView = rv
    }

    private fun setExpanded(expand: Boolean) {
        isExpanded = expand
        panelView?.visibility = if (expand) View.VISIBLE else View.GONE
        handler.removeCallbacks(autoHideRunnable)
        if (expand && prefs.overlayMode == "HIDDEN") {
            handler.postDelayed(autoHideRunnable, 4500)
        }
    }

    fun refreshConfig() {
        val root = rootView ?: return
        val handle = handleView ?: return
        val panel = panelView ?: return
        val rv = recyclerView ?: return

        val mode = prefs.overlayMode
        if (mode == "OFF") {
            root.visibility = View.GONE
            return
        }
        root.visibility = View.VISIBLE

        val lp = root.layoutParams as WindowManager.LayoutParams
        root.removeAllViews()
        if (prefs.dockSide == "RIGHT") {
            lp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            handle.setBackgroundResource(R.drawable.bg_handle_right)
            panel.setBackgroundResource(R.drawable.bg_dock_right)
            root.addView(panel)
            root.addView(handle)
        } else {
            lp.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            handle.setBackgroundResource(R.drawable.bg_handle_left)
            panel.setBackgroundResource(R.drawable.bg_dock_left)
            root.addView(handle)
            root.addView(panel)
        }
        wm.updateViewLayout(root, lp)

        val allApps = repo.loadAllLaunchableApps().associateBy { it.packageName }
        val items = mutableListOf<AppInfo>()
        for (pkg in prefs.getPinnedApps()) {
            allApps[pkg]?.let { items.add(it.copy(isPinned = true)) }
        }
        if (prefs.showRecents) {
            repo.getRecentPackages(prefs, 6)
                .filter { !prefs.getPinnedApps().contains(it) && allApps.containsKey(it) }
                .take(3)
                .forEach { pkg -> allApps[pkg]?.let { items.add(it.copy(isRecent = true)) } }
        }

        rv.adapter = DockAdapter(
            items = items,
            iconSizeDp = prefs.iconSizeDp,
            onClick = { app ->
                setExpanded(false)
                repo.launchApp(app.packageName, prefs)
            },
            onLongClick = { }
        )

        if (mode == "ALWAYS") {
            handle.visibility = View.GONE
            panel.visibility = View.VISIBLE
            isExpanded = true
        } else {
            handle.visibility = View.VISIBLE
            setExpanded(false)
        }
    }
}
