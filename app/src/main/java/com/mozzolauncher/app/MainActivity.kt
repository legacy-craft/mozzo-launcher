package com.mozzolauncher.app

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.WallpaperManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.text.format.DateFormat
import android.util.Base64
import android.util.SizeF
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    data class AppItem(
        val id: String,
        val label: String,
        val normalizedLabel: String,
        val packageName: String,
        val activityName: String,
        val icon: Drawable,
        val isLauncherSettings: Boolean = false,
        val shortcutIntentUri: String? = null
    )

    data class SettingShortcut(
        val title: String,
        val keywords: String,
        val action: String
    )

    data class WidgetEntry(
        val id: Int,
        var spanX: Int,
        var spanY: Int
    )

    private enum class WidgetEditMode { MOVE, RESIZE }

    private val prefs by lazy {
        val np = getSharedPreferences("mozzo_launcher_prefs", Context.MODE_PRIVATE)
        val op = getSharedPreferences("lynx_launcher_prefs", Context.MODE_PRIVATE)
        if (np.all.isEmpty() && op.all.isNotEmpty()) {
            val ed = np.edit()
            for ((k, v) in op.all) {
                when (v) {
                    is Boolean -> ed.putBoolean(k, v)
                    is Int -> ed.putInt(k, v)
                    is Long -> ed.putLong(k, v)
                    is Float -> ed.putFloat(k, v)
                    is String -> ed.putString(k, v)
                }
            }
            ed.apply()
        }
        np
    }

    private lateinit var rootContainer: FrameLayout
    private lateinit var wallpaperView: ImageView
    private lateinit var homeCenterColumn: LinearLayout
    private lateinit var clockContainer: LinearLayout
    private lateinit var clockTimeText: TextClock
    private lateinit var clockDateText: TextClock
    private lateinit var widgetsScrollView: ScrollView
    private lateinit var widgetsContainer: GridLayout
    private lateinit var dockContainer: LinearLayout
    private lateinit var dockFavoritesContainer: LinearLayout
    private lateinit var btnDrawerMozzo: ImageButton
    private lateinit var editModeBanner: LinearLayout
    private lateinit var drawerPanel: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var btnClearSearch: ImageButton
    private lateinit var settingsResultsList: ListView
    private lateinit var appsGridView: GridView

    private var allApps: List<AppItem> = emptyList()
    private var filteredApps: List<AppItem> = emptyList()
    private var filteredSettings: List<SettingShortcut> = emptyList()

    private lateinit var appsAdapter: AppsAdapter
    private lateinit var settingsAdapter: SettingsAdapter

    private var appWidgetManager: AppWidgetManager? = null
    private var appWidgetHost: AppWidgetHost? = null
    private val widgetEntries = mutableListOf<WidgetEntry>()
    private val widgetCellViews = mutableMapOf<Int, FrameLayout>()
    private val widgetHostViews = mutableMapOf<Int, AppWidgetHostView>()
    private var pendingWidgetId: Int = -1
    private var pendingWidgetSpanX: Int = 4
    private var pendingWidgetSpanY: Int = 2
    private var settingsDialog: AlertDialog? = null
    private var secondaryDialog: AlertDialog? = null
    private var movingDockEntry: String? = null
    private var preMoveDockSnapshot: List<String>? = null
    private var editingWidgetId: Int? = null
    private var widgetEditMode: WidgetEditMode? = null
    private var preEditWidgetSnapshot: List<WidgetEntry>? = null
    private var insetTop = 0
    private var insetBottom = 0
    private var insetLeft = 0
    private var insetRight = 0
    private val wallpaperExecutor = Executors.newSingleThreadExecutor()

    private val REQ_PICK_WALLPAPER = 1001
    private val REQ_PICK_WIDGET = 1002
    private val REQ_CREATE_WIDGET = 1003
    private val REQ_BIND_WIDGET = 1004
    private val REQ_EXPORT_SETTINGS = 1005
    private val REQ_IMPORT_SETTINGS = 1006
    private val APPWIDGET_HOST_ID = 2048
    private val WIDGET_CELL_HEIGHT_DP = 76

    private val settingShortcuts by lazy {
        listOf(
            SettingShortcut(getString(R.string.set_wifi), "wifi internet network wireless connection ${getString(R.string.kw_wifi)}", Settings.ACTION_WIFI_SETTINGS),
            SettingShortcut(getString(R.string.set_tethering), "hotspot tethering router usb wifi share connection ${getString(R.string.kw_tethering)}", Settings.ACTION_WIRELESS_SETTINGS),
            SettingShortcut(getString(R.string.set_bluetooth), "bluetooth devices headphones ${getString(R.string.kw_bluetooth)}", Settings.ACTION_BLUETOOTH_SETTINGS),
            SettingShortcut(getString(R.string.set_display), "display screen brightness font text ${getString(R.string.kw_display)}", Settings.ACTION_DISPLAY_SETTINGS),
            SettingShortcut(getString(R.string.set_sound), "sound volume ringtone vibration ${getString(R.string.kw_sound)}", Settings.ACTION_SOUND_SETTINGS),
            SettingShortcut(getString(R.string.set_battery), "battery power saving charging ${getString(R.string.kw_battery)}", Intent.ACTION_POWER_USAGE_SUMMARY),
            SettingShortcut(getString(R.string.set_apps), "apps applications manage uninstall ${getString(R.string.kw_apps)}", Settings.ACTION_APPLICATION_SETTINGS),
            SettingShortcut(getString(R.string.set_storage), "storage memory space ${getString(R.string.kw_storage)}", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
            SettingShortcut(getString(R.string.set_location), "location gps position ${getString(R.string.kw_location)}", Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            SettingShortcut(getString(R.string.set_security), "security fingerprint pin password lock ${getString(R.string.kw_security)}", Settings.ACTION_SECURITY_SETTINGS),
            SettingShortcut(getString(R.string.set_date), "date time timezone ${getString(R.string.kw_date)}", Settings.ACTION_DATE_SETTINGS),
            SettingShortcut(getString(R.string.set_keyboard), "language keyboard input ${getString(R.string.kw_keyboard)}", Settings.ACTION_INPUT_METHOD_SETTINGS),
            SettingShortcut(getString(R.string.set_about), "about phone device info system android ${getString(R.string.kw_about)}", Settings.ACTION_DEVICE_INFO_SETTINGS),
            SettingShortcut(getString(R.string.set_system), "system settings ${getString(R.string.kw_system)}", Settings.ACTION_SETTINGS)
        )
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadInstalledApps()
        }
    }

    private val shortcutReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.android.launcher.action.INSTALL_SHORTCUT") {
                handleInstallShortcutIntent(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingWidgetId = savedInstanceState?.getInt("pending_widget_id", -1) ?: -1
        pendingWidgetSpanX = savedInstanceState?.getInt("pending_widget_span_x", 4) ?: 4
        pendingWidgetSpanY = savedInstanceState?.getInt("pending_widget_span_y", 2) ?: 2
        setupTransparentSystemBars()
        setContentView(R.layout.activity_main)

        bindViews()
        configureWallpaperWindowMode()
        setupWidgetHost()
        setupAdapters()
        setupListeners()

        applyDockSide(prefs.getString("dock_side", "left") ?: "left")
        applyPanelOpacity(prefs.getInt("panel_opacity_pct", 75))
        applyClockVisibility(prefs.getBoolean("show_clock", true))
        refreshClockFormat()
        focusDrawerButtonOnHome()

        rootContainer.post {
            if (isFinishing || isDestroyed) return@post
            applySavedWallpaper(forceSystemSync = !prefs.getBoolean("system_wallpaper_synced", false))
            loadInstalledApps()
            restoreAllWidgets()
            handleInstallShortcutIntent(intent)
        }

        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, pkgFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(packageReceiver, pkgFilter)
        }

        val scFilter = IntentFilter("com.android.launcher.action.INSTALL_SHORTCUT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(shortcutReceiver, scFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(shortcutReceiver, scFilter)
        }

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            insetTop = bars.top
            insetBottom = bars.bottom
            insetLeft = bars.left
            insetRight = bars.right

            val cLp = clockContainer.layoutParams as FrameLayout.LayoutParams
            cLp.topMargin = insetTop + dp(14)
            clockContainer.layoutParams = cLp

            applyDockSide(prefs.getString("dock_side", "left") ?: "left")
            insets
        }
        ViewCompat.requestApplyInsets(rootContainer)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("pending_widget_id", pendingWidgetId)
        outState.putInt("pending_widget_span_x", pendingWidgetSpanX)
        outState.putInt("pending_widget_span_y", pendingWidgetSpanY)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupTransparentSystemBars()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyDockSide(prefs.getString("dock_side", "left") ?: "left")
        rebuildWidgetsUI()
        if (!canUseSystemWallpaper() && !prefs.getBoolean("has_custom_wallpaper", false)) {
            applySavedWallpaper()
        }
        if (settingsDialog?.isShowing == true) {
            settingsDialog?.dismiss()
            settingsDialog = null
            showLauncherSettingsDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshClockFormat()
        focusDrawerButtonOnHome()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            if (intent.action == "com.android.launcher.action.INSTALL_SHORTCUT") {
                handleInstallShortcutIntent(intent)
                return
            }
        }
        exitEditMode(commit = true)
        secondaryDialog?.dismiss()
        settingsDialog?.dismiss()
        if (drawerPanel.visibility == View.VISIBLE) {
            closeDrawer()
        } else {
            focusDrawerButtonOnHome()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && (movingDockEntry != null || editingWidgetId != null)) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    exitEditMode(commit = true)
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    exitEditMode(commit = false)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (handleEditModeDpad(event.keyCode)) {
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS || keyCode == KeyEvent.KEYCODE_INFO) {
            showLauncherSettingsDialog()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStart() {
        super.onStart()
        try {
            appWidgetHost?.startListening()
        } catch (_: Exception) {
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            appWidgetHost?.stopListening()
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        secondaryDialog?.dismiss()
        settingsDialog?.dismiss()
        secondaryDialog = null
        settingsDialog = null
        widgetCellViews.clear()
        widgetHostViews.clear()
        wallpaperExecutor.shutdownNow()
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(shortcutReceiver) } catch (_: Exception) {}
    }

    private fun setupTransparentSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            @Suppress("DEPRECATION")
            statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isStatusBarContrastEnforced = false
                isNavigationBarContrastEnforced = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes = attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    private fun bindViews() {
        rootContainer = findViewById(R.id.rootContainer)
        wallpaperView = findViewById(R.id.wallpaperView)
        homeCenterColumn = findViewById(R.id.homeCenterColumn)
        clockContainer = findViewById(R.id.clockContainer)
        clockTimeText = findViewById(R.id.clockTimeText)
        clockDateText = findViewById(R.id.clockDateText)
        widgetsScrollView = findViewById(R.id.widgetsScrollView)
        widgetsContainer = findViewById(R.id.widgetsContainer)
        dockContainer = findViewById(R.id.dockContainer)
        dockFavoritesContainer = findViewById(R.id.dockFavoritesContainer)
        btnDrawerMozzo = findViewById(R.id.btnDrawerMozzo)
        editModeBanner = findViewById(R.id.editModeBanner)
        drawerPanel = findViewById(R.id.drawerPanel)
        searchInput = findViewById(R.id.searchInput)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        settingsResultsList = findViewById(R.id.settingsResultsList)
        appsGridView = findViewById(R.id.appsGridView)
    }

    private fun setupWidgetHost() {
        appWidgetManager = try {
            AppWidgetManager.getInstance(this)
        } catch (_: Exception) {
            null
        }
        appWidgetHost = if (appWidgetManager != null) {
            try {
                AppWidgetHost(this, APPWIDGET_HOST_ID)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    private fun setupAdapters() {
        appsAdapter = AppsAdapter()
        appsGridView.adapter = appsAdapter

        settingsAdapter = SettingsAdapter()
        settingsResultsList.adapter = settingsAdapter
    }

    private fun triggerHaptic() {
        rootContainer.performHapticFeedback(
            HapticFeedbackConstants.LONG_PRESS,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
    }

    private fun setHomeFocusBlocked(blocked: Boolean) {
        val mode = if (blocked) ViewGroup.FOCUS_BLOCK_DESCENDANTS else ViewGroup.FOCUS_AFTER_DESCENDANTS
        homeCenterColumn.descendantFocusability = mode
        dockContainer.descendantFocusability = mode
        rootContainer.isFocusable = !blocked
    }

    private fun focusDrawerButtonOnHome() {
        if (movingDockEntry != null || editingWidgetId != null) return
        if (drawerPanel.visibility != View.VISIBLE &&
            settingsDialog?.isShowing != true &&
            secondaryDialog?.isShowing != true
        ) {
            btnDrawerMozzo.visibility = View.VISIBLE
            btnDrawerMozzo.isFocusable = true
            btnDrawerMozzo.post {
                if (movingDockEntry == null &&
                    editingWidgetId == null &&
                    drawerPanel.visibility != View.VISIBLE &&
                    settingsDialog?.isShowing != true &&
                    secondaryDialog?.isShowing != true
                ) {
                    btnDrawerMozzo.requestFocus()
                }
            }
        }
    }

    private fun trackSecondaryDialog(dialog: AlertDialog, onDismissExtra: (() -> Unit)? = null) {
        secondaryDialog?.dismiss()
        secondaryDialog = dialog
        dialog.setOnDismissListener {
            if (secondaryDialog === dialog) {
                secondaryDialog = null
            }
            onDismissExtra?.invoke()
        }
        dialog.show()
    }

    private fun refreshClockFormat() {
        val locale = Locale.getDefault()
        clockTimeText.format12Hour = DateFormat.getBestDateTimePattern(locale, "hma")
        clockTimeText.format24Hour = DateFormat.getBestDateTimePattern(locale, "Hm")
        val datePattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        clockDateText.format12Hour = datePattern
        clockDateText.format24Hour = datePattern
    }

    private fun openClockOrDateSettings() {
        val alarmIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        if (alarmIntent.resolveActivity(packageManager) != null) {
            try {
                startActivity(alarmIntent)
                return
            } catch (_: Exception) { }
        }
        try {
            startActivity(Intent(Settings.ACTION_DATE_SETTINGS))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) { }
        }
    }

    private fun setupListeners() {
        btnDrawerMozzo.setOnClickListener {
            if (drawerPanel.visibility == View.VISIBLE) closeDrawer() else openDrawer()
        }
        btnDrawerMozzo.setOnLongClickListener { true }

        val longPressLauncher = View.OnLongClickListener {
            triggerHaptic()
            showLauncherSettingsDialog()
            true
        }

        val wallpaperDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                triggerHaptic()
                showLauncherSettingsDialog()
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (drawerPanel.visibility == View.VISIBLE) {
                    closeDrawer()
                    return true
                }
                return false
            }
        })

        val scrollDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                triggerHaptic()
                showLauncherSettingsDialog()
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (drawerPanel.visibility == View.VISIBLE) {
                    closeDrawer()
                    return true
                }
                return false
            }
        })

        rootContainer.setOnTouchListener { _, event ->
            wallpaperDetector.onTouchEvent(event)
            true
        }
        widgetsScrollView.setOnTouchListener { _, event ->
            scrollDetector.onTouchEvent(event)
            false
        }

        clockContainer.setOnClickListener {
            openClockOrDateSettings()
        }
        clockContainer.setOnLongClickListener(longPressLauncher)

        btnClearSearch.setOnClickListener {
            searchInput.setText("")
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                btnClearSearch.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                filterItems(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        appsGridView.setOnItemClickListener { _, _, position, _ ->
            val app = filteredApps.getOrNull(position) ?: return@setOnItemClickListener
            launchApp(app)
            closeDrawer()
        }

        appsGridView.setOnItemLongClickListener { _, _, position, _ ->
            val app = filteredApps.getOrNull(position) ?: return@setOnItemLongClickListener false
            triggerHaptic()
            showAppOptionsDialog(app)
            true
        }

        settingsResultsList.setOnItemClickListener { _, _, position, _ ->
            val setting = filteredSettings.getOrNull(position) ?: return@setOnItemClickListener
            openSettingAction(setting.action)
            closeDrawer()
        }

        settingsResultsList.setOnItemLongClickListener { _, _, position, _ ->
            val setting = filteredSettings.getOrNull(position) ?: return@setOnItemLongClickListener false
            triggerHaptic()
            val token = "setting:${setting.title}|${setting.action}"
            val favorites = getDockFavorites()
            val isFav = favorites.contains(token)
            val label = if (isFav) getString(R.string.dock_remove) else getString(R.string.dock_add)
            val dlg = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(getString(R.string.setting_dialog_fmt, setting.title))
                .setItems(arrayOf(label)) { _, _ ->
                    toggleDockFavorite(token)
                }
                .create()
            trackSecondaryDialog(dlg)
            true
        }
    }

    private fun getWidgetGridCols(): Int {
        return if (resources.displayMetrics.widthPixels > resources.displayMetrics.heightPixels) 6 else 4
    }

    private fun computeDefaultWidgetSpans(info: AppWidgetProviderInfo?, maxCols: Int): Pair<Int, Int> {
        if (info == null) return minOf(4, maxCols) to 2
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            info.targetCellWidth > 0 &&
            info.targetCellHeight > 0
        ) {
            return info.targetCellWidth.coerceIn(1, maxCols) to info.targetCellHeight.coerceIn(1, 6)
        }
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val minWdp = (info.minWidth / density).toInt()
        val minHdp = (info.minHeight / density).toInt()
        val spanX = if (minWdp > 0) ((minWdp + 30) / 70).coerceIn(1, maxCols) else minOf(4, maxCols)
        val spanY = if (minHdp > 0) ((minHdp + 30) / 70).coerceIn(1, 6) else 2
        return spanX to spanY
    }

    private fun saveWidgetEntries() {
        val arr = JSONArray()
        for (entry in widgetEntries) {
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("spanX", entry.spanX)
            obj.put("spanY", entry.spanY)
            obj.put("height", entry.spanY * WIDGET_CELL_HEIGHT_DP)
            arr.put(obj)
        }
        prefs.edit().putString("widgets_list_json", arr.toString()).apply()
    }

    private fun restoreAllWidgets() {
        widgetEntries.clear()
        val awm = appWidgetManager
        if (awm == null) {
            rebuildWidgetsUI()
            return
        }
        val maxCols = getWidgetGridCols()
        val raw = prefs.getString("widgets_list_json", null)
        if (!raw.isNullOrEmpty()) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.getInt("id")
                    val info = awm.getAppWidgetInfo(id)
                    if (info != null) {
                        val (defX, defY) = computeDefaultWidgetSpans(info, maxCols)
                        val spanX = if (obj.has("spanX")) {
                            obj.optInt("spanX", defX).coerceIn(1, maxCols)
                        } else {
                            defX
                        }
                        val spanY = if (obj.has("spanY")) {
                            obj.optInt("spanY", defY).coerceIn(1, 6)
                        } else if (obj.has("height")) {
                            ((obj.optInt("height", 152) + 38) / WIDGET_CELL_HEIGHT_DP).coerceIn(1, 6)
                        } else {
                            defY
                        }
                        widgetEntries.add(WidgetEntry(id, spanX, spanY))
                    } else {
                        try { appWidgetHost?.deleteAppWidgetId(id) } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        } else {
            val legacyId = prefs.getInt("appwidget_id", -1)
            val info = if (legacyId != -1) awm.getAppWidgetInfo(legacyId) else null
            if (info != null) {
                val (defX, defY) = computeDefaultWidgetSpans(info, maxCols)
                widgetEntries.add(WidgetEntry(legacyId, defX, defY))
                prefs.edit().remove("appwidget_id").apply()
                saveWidgetEntries()
            }
        }
        rebuildWidgetsUI()
    }

    private fun notifyWidgetSize(hostView: AppWidgetHostView, widthDp: Int, heightDp: Int) {
        val safeWidthDp = widthDp.coerceAtLeast(56)
        val safeHeightDp = heightDp.coerceAtLeast(56)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                hostView.updateAppWidgetSize(
                    Bundle(),
                    listOf(SizeF(safeWidthDp.toFloat(), safeHeightDp.toFloat()))
                )
            } else {
                @Suppress("DEPRECATION")
                hostView.updateAppWidgetSize(null, safeWidthDp, safeHeightDp, safeWidthDp, safeHeightDp)
            }
        } catch (_: Exception) { }
    }

    private fun getWidgetGridUnitWidthPx(cols: Int): Int {
        val colW = homeCenterColumn.layoutParams?.width ?: 0
        val availW = when {
            widgetsContainer.width > 0 -> widgetsContainer.width
            colW > 0 -> colW
            else -> (resources.displayMetrics.widthPixels - dp(100)).coerceAtLeast(dp(180))
        }
        return (availW / cols).coerceAtLeast(dp(44))
    }

    private fun syncWidgetFocusAndSizes() {
        val cols = getWidgetGridCols()
        val unitW = getWidgetGridUnitWidthPx(cols)
        val unitH = dp(WIDGET_CELL_HEIGHT_DP)
        val fullGap = dp(3) * 2
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val dockSide = prefs.getString("dock_side", "left") ?: "left"

        for (entry in widgetEntries) {
            val effX = entry.spanX.coerceIn(1, cols)
            val effY = entry.spanY.coerceIn(1, 6)
            val itemW = (unitW * effX - fullGap).coerceAtLeast(dp(40))
            val itemH = (unitH * effY - fullGap).coerceAtLeast(dp(56))

            val cell = widgetCellViews[entry.id]
            if (cell != null) {
                cell.nextFocusLeftId = if (dockSide == "left") R.id.btnDrawerMozzo else View.NO_ID
                cell.nextFocusRightId = if (dockSide == "right") R.id.btnDrawerMozzo else View.NO_ID
            }
            val host = widgetHostViews[entry.id]
            if (host != null) {
                notifyWidgetSize(host, (itemW / density).toInt(), (itemH / density).toInt())
            }
        }
    }

    private fun createWidgetCellWrapper(entryId: Int, hostView: AppWidgetHostView): FrameLayout {
        val cellWrapper = object : FrameLayout(this) {
            private var downX = 0f
            private var downY = 0f
            private var triggered = false
            private val slop = ViewConfiguration.get(context).scaledTouchSlop
            private val longPressTask = Runnable {
                val current = widgetEntries.firstOrNull { it.id == entryId } ?: return@Runnable
                triggered = true
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showWidgetMenuDialog(current)
            }

            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                if (editingWidgetId != null) {
                    return true
                }
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = ev.x
                        downY = ev.y
                        triggered = false
                        postDelayed(longPressTask, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (Math.abs(ev.x - downX) > slop || Math.abs(ev.y - downY) > slop) {
                            removeCallbacks(longPressTask)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        removeCallbacks(longPressTask)
                    }
                }
                return triggered
            }
        }

        cellWrapper.isFocusable = true
        cellWrapper.setBackgroundResource(R.drawable.bg_tv_focus_item)
        cellWrapper.setOnClickListener {
            val activeId = editingWidgetId ?: return@setOnClickListener
            if (activeId == entryId) {
                exitEditMode(commit = true)
            } else if (widgetEditMode == WidgetEditMode.MOVE) {
                moveWidgetEntryToTarget(activeId, entryId)
                exitEditMode(commit = true)
            }
        }
        cellWrapper.setOnLongClickListener {
            val current = widgetEntries.firstOrNull { it.id == entryId } ?: return@setOnLongClickListener false
            triggerHaptic()
            showWidgetMenuDialog(current)
            true
        }

        val hostLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            gravity = Gravity.CENTER
        }
        cellWrapper.addView(hostView, hostLp)
        return cellWrapper
    }

    private fun rebuildWidgetsUI() {
        val cols = getWidgetGridCols()
        if (widgetsContainer.columnCount != cols) {
            widgetsContainer.columnCount = cols
        }
        val activeIds = widgetEntries.map { it.id }.toHashSet()
        val staleIds = widgetCellViews.keys.filter { it !in activeIds }
        for (staleId in staleIds) {
            widgetCellViews.remove(staleId)?.let { widgetsContainer.removeView(it) }
            widgetHostViews.remove(staleId)
        }

        val unitW = getWidgetGridUnitWidthPx(cols)
        val unitH = dp(WIDGET_CELL_HEIGHT_DP)
        val halfGap = dp(3)
        val fullGap = halfGap * 2
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val dockSide = prefs.getString("dock_side", "left") ?: "left"

        widgetsContainer.removeAllViews()
        for (entry in widgetEntries) {
            var cellWrapper = widgetCellViews[entry.id]
            var hostView = widgetHostViews[entry.id]

            if (cellWrapper == null || hostView == null) {
                val info = appWidgetManager?.getAppWidgetInfo(entry.id) ?: continue
                hostView = try {
                    appWidgetHost?.createView(this, entry.id, info)?.apply {
                        setAppWidget(entry.id, info)
                    }
                } catch (_: Exception) {
                    null
                } ?: continue
                cellWrapper = createWidgetCellWrapper(entry.id, hostView)
                widgetCellViews[entry.id] = cellWrapper
                widgetHostViews[entry.id] = hostView
            }

            val effX = entry.spanX.coerceIn(1, cols)
            val effY = entry.spanY.coerceIn(1, 6)
            val itemW = (unitW * effX - fullGap).coerceAtLeast(dp(40))
            val itemH = (unitH * effY - fullGap).coerceAtLeast(dp(56))

            val colSpec = GridLayout.spec(GridLayout.UNDEFINED, effX)
            val rowSpec = GridLayout.spec(GridLayout.UNDEFINED, effY)
            cellWrapper.layoutParams = GridLayout.LayoutParams(rowSpec, colSpec).apply {
                width = itemW
                height = itemH
                setMargins(halfGap, halfGap, halfGap, halfGap)
            }
            cellWrapper.isSelected = (editingWidgetId == entry.id)
            cellWrapper.nextFocusLeftId = if (dockSide == "left") R.id.btnDrawerMozzo else View.NO_ID
            cellWrapper.nextFocusRightId = if (dockSide == "right") R.id.btnDrawerMozzo else View.NO_ID

            widgetsContainer.addView(cellWrapper)
            notifyWidgetSize(hostView, (itemW / density).toInt(), (itemH / density).toInt())
        }

        editingWidgetId?.let { activeId ->
            widgetCellViews[activeId]?.post {
                if (editingWidgetId == activeId) {
                    widgetCellViews[activeId]?.requestFocus()
                }
            }
        }
    }

    private fun startWidgetEditMode(widgetId: Int, mode: WidgetEditMode) {
        if (widgetEntries.none { it.id == widgetId }) return
        exitEditMode(commit = true)
        preEditWidgetSnapshot = widgetEntries.map { it.copy() }
        editingWidgetId = widgetId
        widgetEditMode = mode
        setHomeFocusBlocked(false)
        rebuildWidgetsUI()
        updateEditModeBanner()
    }

    private fun moveWidgetEntryByDelta(widgetId: Int, delta: Int): Boolean {
        val idx = widgetEntries.indexOfFirst { it.id == widgetId }
        if (idx == -1) return false
        val next = (idx + delta).coerceIn(0, widgetEntries.lastIndex)
        if (next == idx) return false
        val item = widgetEntries.removeAt(idx)
        widgetEntries.add(next, item)
        saveWidgetEntries()
        rebuildWidgetsUI()
        return true
    }

    private fun moveWidgetEntryToTarget(activeId: Int, targetId: Int) {
        val fromIdx = widgetEntries.indexOfFirst { it.id == activeId }
        val toIdx = widgetEntries.indexOfFirst { it.id == targetId }
        if (fromIdx == -1 || toIdx == -1 || fromIdx == toIdx) return
        val item = widgetEntries.removeAt(fromIdx)
        widgetEntries.add(toIdx, item)
        saveWidgetEntries()
        rebuildWidgetsUI()
    }

    private fun adjustEditingWidgetSpan(dX: Int, dY: Int) {
        val id = editingWidgetId ?: return
        val entry = widgetEntries.firstOrNull { it.id == id } ?: return
        val cols = getWidgetGridCols()
        val nextX = (entry.spanX + dX).coerceIn(1, cols)
        val nextY = (entry.spanY + dY).coerceIn(1, 6)
        if (nextX != entry.spanX || nextY != entry.spanY) {
            entry.spanX = nextX
            entry.spanY = nextY
            saveWidgetEntries()
            rebuildWidgetsUI()
            updateEditModeBanner()
        }
    }

    private fun showWidgetMenuDialog(entry: WidgetEntry) {
        if (widgetEntries.none { it.id == entry.id }) return

        val options = arrayOf(
            getString(R.string.widget_move),
            getString(R.string.widget_resize),
            getString(R.string.widget_remove)
        )

        val dlg = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(getString(R.string.widget_menu_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startWidgetEditMode(entry.id, WidgetEditMode.MOVE)
                    1 -> startWidgetEditMode(entry.id, WidgetEditMode.RESIZE)
                    2 -> {
                        val idx = widgetEntries.indexOfFirst { it.id == entry.id }
                        if (idx != -1) {
                            widgetEntries.removeAt(idx)
                            try { appWidgetHost?.deleteAppWidgetId(entry.id) } catch (_: Exception) {}
                            saveWidgetEntries()
                            rebuildWidgetsUI()
                            focusDrawerButtonOnHome()
                            Toast.makeText(this, getString(R.string.toast_widget_removed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .create()
        trackSecondaryDialog(dlg)
    }

    private fun cleanupPendingWidget() {
        if (pendingWidgetId != -1) {
            try { appWidgetHost?.deleteAppWidgetId(pendingWidgetId) } catch (_: Exception) {}
            pendingWidgetId = -1
        }
    }

    private fun pickNewWidget() {
        val awm = appWidgetManager
        if (awm == null || appWidgetHost == null) {
            Toast.makeText(this, getString(R.string.toast_widgets_unsupported_tv), Toast.LENGTH_LONG).show()
            return
        }
        val providers = try {
            awm.installedProviders
        } catch (_: Exception) {
            emptyList()
        }
        if (providers.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.toast_no_widgets), Toast.LENGTH_SHORT).show()
            return
        }

        val maxCols = getWidgetGridCols()
        data class ProviderEntry(
            val info: AppWidgetProviderInfo,
            val title: String,
            val appName: String,
            val appPkg: String,
            val spanX: Int,
            val spanY: Int,
            val appIcon: Drawable?,
            val preview: Drawable?
        )

        val sorted = providers.mapNotNull { info ->
            try {
                val widgetLabel = info.loadLabel(packageManager).orEmpty().trim()
                val appLabel = try {
                    val appInfo = packageManager.getApplicationInfo(info.provider.packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    info.provider.packageName
                }
                val appIcon = try {
                    packageManager.getApplicationIcon(info.provider.packageName)
                } catch (_: Exception) {
                    try {
                        info.loadIcon(this, resources.displayMetrics.densityDpi)
                    } catch (_: Exception) {
                        null
                    }
                }
                val preview = try {
                    info.loadPreviewImage(this, resources.displayMetrics.densityDpi)
                } catch (_: Exception) {
                    null
                }
                val (sx, sy) = computeDefaultWidgetSpans(info, maxCols)
                ProviderEntry(
                    info = info,
                    title = widgetLabel.ifEmpty { appLabel },
                    appName = appLabel,
                    appPkg = info.provider.packageName,
                    spanX = sx,
                    spanY = sy,
                    appIcon = appIcon,
                    preview = preview
                )
            } catch (_: Exception) {
                null
            }
        }.sortedWith(compareBy({ it.appName.lowercase(Locale.ROOT) }, { it.title.lowercase(Locale.ROOT) }))

        if (sorted.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_no_widgets), Toast.LENGTH_SHORT).show()
            return
        }

        val grouped = LinkedHashMap<String, MutableList<ProviderEntry>>()
        for (entry in sorted) {
            grouped.getOrPut(entry.appName) { mutableListOf() }.add(entry)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        val titleView = TextView(this).apply {
            text = getString(R.string.widget_picker_title)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F8FAFC"))
            setPadding(dp(4), 0, dp(4), dp(8))
        }
        container.addView(titleView)

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(Color.argb(238, 9, 13, 20))
                setStroke(dp(1), Color.argb(90, 148, 163, 184))
            }
        }

        val availW = resources.displayMetrics.widthPixels
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val pickerCols = if ((availW / density) >= 600f) 3 else 2
        var pickerDialog: AlertDialog? = null
        var firstCardView: View? = null

        for ((appName, items) in grouped) {
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(10), dp(4), dp(6))
            }
            val headerIcon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(8) }
                scaleType = ImageView.ScaleType.FIT_CENTER
                val icon = items.firstOrNull()?.appIcon
                if (icon != null) setImageDrawable(icon) else setImageResource(android.R.drawable.sym_def_app_icon)
            }
            val headerText = TextView(this).apply {
                text = appName
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#E2E8F0"))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            headerRow.addView(headerIcon)
            headerRow.addView(headerText)
            listContainer.addView(headerRow)

            var idx = 0
            while (idx < items.size) {
                val cardRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    isBaselineAligned = false
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dp(8)
                    }
                }
                for (c in 0 until pickerCols) {
                    if (idx + c >= items.size) {
                        val spacer = View(this).apply {
                            layoutParams = LinearLayout.LayoutParams(0, 0, 1f).apply {
                                if (c < pickerCols - 1) marginEnd = dp(8)
                            }
                        }
                        cardRow.addView(spacer)
                        continue
                    }
                    val item = items[idx + c]
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        isFocusable = true
                        isClickable = true
                        setPadding(dp(8), dp(8), dp(8), dp(8))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(14).toFloat()
                            setColor(Color.parseColor("#162133"))
                            setStroke(dp(1), Color.parseColor("#334155"))
                        }
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            if (c < pickerCols - 1) marginEnd = dp(8)
                        }
                        setOnFocusChangeListener { v, hasFocus ->
                            val gd = v.background as? GradientDrawable ?: return@setOnFocusChangeListener
                            gd.setStroke(dp(if (hasFocus) 2 else 1), if (hasFocus) Color.parseColor("#38BDF8") else Color.parseColor("#334155"))
                            v.invalidate()
                        }
                    }

                    val topMeta = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    val tvTitle = TextView(this).apply {
                        text = item.title
                        textSize = 12f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#F8FAFC"))
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = dp(4)
                        }
                    }
                    val tvBadge = TextView(this).apply {
                        text = getString(R.string.widget_cell_fmt, item.spanX, item.spanY)
                        textSize = 11f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#38BDF8"))
                        setPadding(dp(6), dp(2), dp(6), dp(2))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(8).toFloat()
                            setColor(Color.parseColor("#0F172A"))
                        }
                    }
                    topMeta.addView(tvTitle)
                    topMeta.addView(tvBadge)
                    card.addView(topMeta)

                    val previewBox = FrameLayout(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(86)
                        ).apply {
                            topMargin = dp(6)
                        }
                        background = GradientDrawable().apply {
                            cornerRadius = dp(10).toFloat()
                            setColor(Color.parseColor("#0B1220"))
                        }
                    }
                    val previewImg = ImageView(this).apply {
                        if (item.preview != null) {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            ).apply {
                                val p = dp(6)
                                setMargins(p, p, p, p)
                            }
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setImageDrawable(item.preview)
                        } else {
                            layoutParams = FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            if (item.appIcon != null) {
                                setImageDrawable(item.appIcon)
                            } else {
                                setImageResource(android.R.drawable.ic_menu_add)
                            }
                        }
                    }
                    previewBox.addView(previewImg)
                    card.addView(previewBox)

                    card.setOnClickListener {
                        pickerDialog?.dismiss()
                        pendingWidgetSpanX = item.spanX
                        pendingWidgetSpanY = item.spanY
                        startBindSelectedWidget(item.info)
                    }
                    if (firstCardView == null) firstCardView = card
                    cardRow.addView(card)
                }
                listContainer.addView(cardRow)
                idx += pickerCols
            }
        }

        container.addView(listContainer)
        scroll.addView(container)

        setHomeFocusBlocked(true)
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(scroll)
            .setCancelable(true)
            .create()
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        pickerDialog = dialog
        trackSecondaryDialog(dialog) {
            if (drawerPanel.visibility != View.VISIBLE && settingsDialog?.isShowing != true) {
                setHomeFocusBlocked(false)
                focusDrawerButtonOnHome()
            }
        }
        val targetW = if (pickerCols >= 3) {
            (availW * 0.78f).toInt().coerceIn(dp(560), dp(760)).coerceAtMost((availW * 0.94f).toInt())
        } else {
            (availW * 0.94f).toInt().coerceAtMost(dp(460))
        }
        dialog.window?.setLayout(targetW, ViewGroup.LayoutParams.WRAP_CONTENT)
        firstCardView?.let { first ->
            first.post { first.requestFocus() }
        }
    }

    private fun startBindSelectedWidget(info: AppWidgetProviderInfo) {
        val awm = appWidgetManager
        val host = appWidgetHost
        if (awm == null || host == null) {
            Toast.makeText(this, getString(R.string.toast_widgets_unsupported_tv), Toast.LENGTH_LONG).show()
            return
        }
        try {
            val appWidgetId = host.allocateAppWidgetId()
            pendingWidgetId = appWidgetId
            val bound = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                awm.bindAppWidgetIdIfAllowed(appWidgetId, info.profile, info.provider, Bundle())
            } else {
                awm.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)
            }
            if (bound) {
                configureOrAddWidget(appWidgetId)
            } else {
                val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
                    }
                }
                if (bindIntent.resolveActivity(packageManager) == null) {
                    cleanupPendingWidget()
                    Toast.makeText(this, getString(R.string.toast_widget_bind_blocked), Toast.LENGTH_LONG).show()
                    return
                }
                startActivityForResult(bindIntent, REQ_BIND_WIDGET)
            }
        } catch (_: Exception) {
            cleanupPendingWidget()
            Toast.makeText(this, getString(R.string.toast_widget_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun configureOrAddWidget(appWidgetId: Int) {
        val awm = appWidgetManager
        val host = appWidgetHost
        val info = awm?.getAppWidgetInfo(appWidgetId)
        if (info == null || host == null) {
            cleanupPendingWidget()
            Toast.makeText(this, getString(R.string.toast_widget_error), Toast.LENGTH_SHORT).show()
            return
        }
        val isOptional = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0
        } else {
            false
        }
        if (info.configure != null && !isOptional) {
            try {
                host.startAppWidgetConfigureActivityForResult(
                    this,
                    appWidgetId,
                    0,
                    REQ_CREATE_WIDGET,
                    null
                )
            } catch (_: Exception) {
                try {
                    finishAddingWidget(appWidgetId)
                } catch (_: Exception) {
                    cleanupPendingWidget()
                    Toast.makeText(this, getString(R.string.toast_widget_error), Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            finishAddingWidget(appWidgetId)
        }
    }

    private fun finishAddingWidget(appWidgetId: Int) {
        val maxCols = getWidgetGridCols()
        val info = appWidgetManager?.getAppWidgetInfo(appWidgetId)
        val (defX, defY) = computeDefaultWidgetSpans(info, maxCols)
        val spanX = pendingWidgetSpanX.coerceIn(1, maxCols).takeIf { info != null } ?: defX
        val spanY = pendingWidgetSpanY.coerceIn(1, 6).takeIf { info != null } ?: defY
        if (widgetEntries.none { it.id == appWidgetId }) {
            widgetEntries.add(WidgetEntry(appWidgetId, spanX, spanY))
        }
        saveWidgetEntries()
        rebuildWidgetsUI()
        pendingWidgetId = -1
        focusDrawerButtonOnHome()
        Toast.makeText(this, getString(R.string.toast_widget_added), Toast.LENGTH_SHORT).show()
    }

    private fun showLauncherSettingsDialog() {
        if (settingsDialog?.isShowing == true) return

        val currentSize = prefs.getInt("icon_size_dp", 48)
        val currentSide = prefs.getString("dock_side", "left") ?: "left"
        val currentClock = prefs.getBoolean("show_clock", true)
        var selectedSize = currentSize

        fun makeActionButton(label: String, isPrimary: Boolean = false): TextView {
            return TextView(this).apply {
                text = label
                textSize = 14f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                isFocusable = true
                isFocusableInTouchMode = false
                isClickable = true
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setTextColor(Color.WHITE)
                setTypeface(null, if (isPrimary) Typeface.BOLD else Typeface.NORMAL)
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(if (isPrimary) Color.parseColor("#0284C7") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (isPrimary) Color.parseColor("#38BDF8") else Color.parseColor("#334155"))
                }
                setOnFocusChangeListener { v, hasFocus ->
                    val gd = v.background as? GradientDrawable ?: return@setOnFocusChangeListener
                    gd.setStroke(dp(if (hasFocus) 2 else 1), if (hasFocus) Color.WHITE else Color.parseColor("#334155")); v.invalidate()
                }
            }
        }

        val availW = resources.displayMetrics.widthPixels
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val useTwoColumns = (availW / density) >= 600f

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }

        val headerTitle = TextView(this).apply {
            text = getString(R.string.settings_title)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F8FAFC"))
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(headerTitle)

        val leftColumn: LinearLayout
        val rightColumn: LinearLayout
        if (useTwoColumns) {
            leftColumn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(18)
                }
            }
            rightColumn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val columnsContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
                tag = "settings_columns_row"
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            columnsContainer.addView(leftColumn)
            columnsContainer.addView(rightColumn)
            root.addView(columnsContainer)
        } else {
            leftColumn = root
            rightColumn = root
        }

        fun makeSectionLabel(text: String) = TextView(this).apply {
            this.text = text
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, dp(10), 0, dp(6))
        }

        leftColumn.addView(makeSectionLabel(getString(R.string.sec_icon_size)))
        val sizes = listOf(48, 56, 64, 72, 80)
        val sizeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val sizeButtons = mutableListOf<Pair<Int, TextView>>()

        fun refreshSizeButtons() {
            for ((sz, btn) in sizeButtons) {
                val active = (sz == selectedSize)
                btn.background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(if (active) Color.parseColor("#0284C7") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (active) Color.parseColor("#38BDF8") else Color.parseColor("#334155"))
                }
                btn.setTextColor(if (active) Color.WHITE else Color.parseColor("#CBD5E1"))
                btn.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
            }
        }

        for (sz in sizes) {
            val btn = TextView(this).apply {
                text = "${sz}"
                textSize = 14f
                gravity = Gravity.CENTER
                isFocusable = true
                setPadding(0, dp(8), 0, dp(8))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = if (sz != sizes.last()) dp(6) else 0
                }
                setOnFocusChangeListener { v, hasFocus ->
                    val gd = v.background as? GradientDrawable ?: return@setOnFocusChangeListener
                    gd.setStroke(dp(if (hasFocus) 2 else 1), if (hasFocus) Color.WHITE else Color.parseColor("#334155")); v.invalidate()
                }
                setOnClickListener {
                    selectedSize = sz
                    refreshSizeButtons()
                    prefs.edit().putInt("icon_size_dp", sz).commit()
                }
            }
            sizeButtons.add(sz to btn)
            sizeRow.addView(btn)
        }
        refreshSizeButtons()
        leftColumn.addView(sizeRow)

        leftColumn.addView(makeSectionLabel(getString(R.string.show_home_clock)))
        var selClk = currentClock
        val clockRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnOn = makeActionButton("ON").apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
        }
        val btnOff = makeActionButton("OFF").apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        fun syncClk() {
            listOf(true to btnOn, false to btnOff).forEach { (st, b) ->
                val act = (st == selClk)
                b.background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(if (act) Color.parseColor("#0284C7") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (act) Color.parseColor("#38BDF8") else Color.parseColor("#334155"))
                }
                b.setTextColor(if (act) Color.WHITE else Color.parseColor("#CBD5E1"))
                b.setTypeface(null, if (act) Typeface.BOLD else Typeface.NORMAL)
            }
        }
        listOf(true to btnOn, false to btnOff).forEach { (st, b) ->
            b.setOnClickListener {
                selClk = st
                syncClk()
                prefs.edit().putBoolean("show_clock", st).commit()
            }
        }
        syncClk()
        clockRow.addView(btnOn)
        clockRow.addView(btnOff)
        leftColumn.addView(clockRow)
        leftColumn.addView(makeSectionLabel(getString(R.string.sec_dock_pos)))
        var selectedSide = currentSide
        val dockRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnLeft = makeActionButton(getString(R.string.pos_left)).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
        }
        val btnBottom = makeActionButton(getString(R.string.pos_bottom)).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
        }
        val btnRight = makeActionButton(getString(R.string.pos_right)).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        fun refreshDockButtons() {
            listOf("left" to btnLeft, "bottom" to btnBottom, "right" to btnRight).forEach { (side, b) ->
                val active = (side == selectedSide)
                b.background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(if (active) Color.parseColor("#0284C7") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (active) Color.parseColor("#38BDF8") else Color.parseColor("#334155"))
                }
                b.setTextColor(if (active) Color.WHITE else Color.parseColor("#CBD5E1"))
                b.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
            }
        }
        listOf("left" to btnLeft, "bottom" to btnBottom, "right" to btnRight).forEach { (side, btn) ->
            btn.setOnClickListener {
                selectedSide = side
                refreshDockButtons()
                prefs.edit().putString("dock_side", side).commit()
            }
        }
        refreshDockButtons()
        dockRow.addView(btnLeft)
        dockRow.addView(btnBottom)
        dockRow.addView(btnRight)
        leftColumn.addView(dockRow)

        rightColumn.addView(makeSectionLabel(getString(R.string.sec_opacity)))
        val opacities = listOf(0, 25, 50, 75, 100)
        var selectedOp = prefs.getInt("panel_opacity_pct", 75)
        val opRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val opButtons = mutableListOf<Pair<Int, TextView>>()

        val scroll = ScrollView(this)
        fun updateDialogBg(pct: Int) {
            val effectivePct = pct.coerceAtLeast(82)
            val alpha = (effectivePct * 255) / 100
            scroll.background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(Color.argb(alpha, 9, 13, 20))
                setStroke(dp(1), Color.argb(90, 148, 163, 184))
            }
        }

        fun refreshOpButtons() {
            for ((op, btn) in opButtons) {
                val active = (op == selectedOp)
                btn.background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(if (active) Color.parseColor("#0284C7") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (active) Color.parseColor("#38BDF8") else Color.parseColor("#334155"))
                }
                btn.setTextColor(if (active) Color.WHITE else Color.parseColor("#CBD5E1"))
                btn.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
            }
        }
        for (op in opacities) {
            val btn = TextView(this).apply {
                text = "${op}%"
                textSize = 13f
                gravity = Gravity.CENTER
                isFocusable = true
                setPadding(0, dp(8), 0, dp(8))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = if (op != opacities.last()) dp(6) else 0
                }
                setOnFocusChangeListener { v, hasFocus ->
                    val gd = v.background as? GradientDrawable ?: return@setOnFocusChangeListener
                    gd.setStroke(dp(if (hasFocus) 2 else 1), if (hasFocus) Color.WHITE else Color.parseColor("#334155")); v.invalidate()
                }
                setOnClickListener {
                    selectedOp = op
                    refreshOpButtons()
                    prefs.edit().putInt("panel_opacity_pct", op).commit()
                    updateDialogBg(op)
                }
            }
            opButtons.add(op to btn)
            opRow.addView(btn)
        }
        refreshOpButtons()
        rightColumn.addView(opRow)

        rightColumn.addView(makeSectionLabel(getString(R.string.sec_widgets_fmt, widgetEntries.size)))
        val btnAddWidget = makeActionButton(getString(R.string.btn_add_widget)).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        rightColumn.addView(btnAddWidget)

        rightColumn.addView(makeSectionLabel(getString(R.string.sec_wallpaper)))
        val wallRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnPickWall = makeActionButton(getString(R.string.btn_pick_photo)).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
        }
        val btnResetWall = makeActionButton(getString(R.string.btn_default)).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        wallRow.addView(btnPickWall)
        wallRow.addView(btnResetWall)
        rightColumn.addView(wallRow)

        root.addView(makeSectionLabel(getString(R.string.sec_backup_system)))
        val sysRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnDefaultLauncher = makeActionButton(getString(R.string.btn_default_launcher)).apply {
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
        }
        val btnExport = makeActionButton(getString(R.string.btn_export)).apply {
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
        }
        val btnImport = makeActionButton(getString(R.string.btn_import)).apply {
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        sysRow.addView(btnDefaultLauncher)
        sysRow.addView(btnExport)
        sysRow.addView(btnImport)
        root.addView(sysRow)

        val btnClose = makeActionButton(getString(R.string.btn_close), isPrimary = true).apply {
            layoutParams = if (useTwoColumns) {
                LinearLayout.LayoutParams(dp(240), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = dp(14)
                }
            } else {
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(18)
                }
            }
        }
        root.addView(btnClose)

        scroll.addView(root)
        updateDialogBg(selectedOp)

        setHomeFocusBlocked(true)
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(scroll)
            .setCancelable(true)
            .setOnDismissListener {
                settingsDialog = null
                if (drawerPanel.visibility != View.VISIBLE) {
                    applyClockVisibility(prefs.getBoolean("show_clock", true))
                }
                applyDockSide(prefs.getString("dock_side", "left") ?: "left")
                applyPanelOpacity(prefs.getInt("panel_opacity_pct", 75))
                appsAdapter.notifyDataSetChanged()
                if (drawerPanel.visibility != View.VISIBLE && secondaryDialog?.isShowing != true) {
                    setHomeFocusBlocked(false)
                    focusDrawerButtonOnHome()
                }
            }
            .create()
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        settingsDialog = dialog

        btnAddWidget.setOnClickListener {
            dialog.dismiss()
            pickNewWidget()
        }
        btnPickWall.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            try {
                startActivityForResult(intent, REQ_PICK_WALLPAPER)
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.toast_no_file_picker), Toast.LENGTH_SHORT).show()
            }
        }
        btnResetWall.setOnClickListener {
            File(filesDir, "custom_wallpaper.img").delete()
            prefs.edit().putBoolean("has_custom_wallpaper", false).apply()
            applySavedWallpaper(forceSystemSync = true)
            Toast.makeText(this, getString(R.string.toast_wall_reset), Toast.LENGTH_SHORT).show()
        }
        btnDefaultLauncher.setOnClickListener {
            dialog.dismiss()
            openDefaultLauncherSettings()
        }
        btnExport.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "mozzo-launcher-backup.json")
            }
            try {
                startActivityForResult(intent, REQ_EXPORT_SETTINGS)
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.toast_no_file_picker), Toast.LENGTH_SHORT).show()
            }
        }
        btnImport.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain"))
            }
            try {
                startActivityForResult(intent, REQ_IMPORT_SETTINGS)
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.toast_no_file_picker), Toast.LENGTH_SHORT).show()
            }
        }
        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        val targetW = if (useTwoColumns) {
            (availW * 0.72f).toInt().coerceIn(dp(540), dp(700)).coerceAtMost((availW * 0.92f).toInt())
        } else {
            (availW * 0.92f).toInt().coerceAtMost(dp(440))
        }
        dialog.window?.apply {
            setLayout(targetW, ViewGroup.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.45f)
        }
        btnClose.post {
            btnClose.requestFocus()
        }
    }

    private fun openDefaultLauncherSettings() {
        val candidates = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
        Toast.makeText(this, getString(R.string.toast_setting_error), Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) {
            if (requestCode == REQ_PICK_WIDGET || requestCode == REQ_CREATE_WIDGET || requestCode == REQ_BIND_WIDGET) {
                cleanupPendingWidget()
            }
            return
        }

        when (requestCode) {
            REQ_PICK_WALLPAPER -> {
                val uri = data?.data ?: return
                saveAndApplyWallpaper(uri)
            }
            REQ_PICK_WIDGET, REQ_BIND_WIDGET -> {
                val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
                if (id != -1) configureOrAddWidget(id) else cleanupPendingWidget()
            }
            REQ_CREATE_WIDGET -> {
                val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
                if (id != -1) finishAddingWidget(id) else cleanupPendingWidget()
            }
            REQ_EXPORT_SETTINGS -> {
                val uri = data?.data ?: return
                val ok = try {
                    val stream = contentResolver.openOutputStream(uri)
                    if (stream != null) {
                        stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                            writer.write(SettingsBackupHelper.exportToJson(prefs))
                        }
                        true
                    } else {
                        false
                    }
                } catch (_: Exception) {
                    false
                }
                Toast.makeText(
                    this,
                    getString(if (ok) R.string.toast_export_ok else R.string.toast_export_err),
                    Toast.LENGTH_SHORT
                ).show()
            }
            REQ_IMPORT_SETTINGS -> {
                val uri = data?.data ?: return
                val jsonText = try {
                    val maxBytes = 1024 * 1024
                    contentResolver.openInputStream(uri)?.use { input ->
                        val buffer = ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val read = input.read(chunk)
                            if (read <= 0) break
                            total += read
                            if (total > maxBytes) return@use null
                            buffer.write(chunk, 0, read)
                        }
                        buffer.toString(Charsets.UTF_8.name())
                    }
                } catch (_: Exception) {
                    null
                }
                val imported = jsonText != null && SettingsBackupHelper.importFromJson(jsonText, prefs)
                if (imported) {
                    applyClockVisibility(prefs.getBoolean("show_clock", true))
                    applyDockSide(prefs.getString("dock_side", "left") ?: "left")
                    applyPanelOpacity(prefs.getInt("panel_opacity_pct", 75))
                    loadInstalledApps()
                    appsAdapter.notifyDataSetChanged()
                    Toast.makeText(this, getString(R.string.toast_import_ok), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.toast_import_err), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveAndApplyWallpaper(uri: Uri) {
        try {
            val destFile = File(filesDir, "custom_wallpaper.img")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            prefs.edit().putBoolean("has_custom_wallpaper", true).apply()
            applySavedWallpaper(forceSystemSync = true)
            Toast.makeText(this, getString(R.string.toast_wall_updated), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.toast_wall_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun isLowRamDevice(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return am?.isLowRamDevice == true
    }

    private fun createMozzoWaveBitmap(): Bitmap {
        val dm = resources.displayMetrics
        val maxSide = if (isLowRamDevice()) 960 else 1920
        val minSide = if (isLowRamDevice()) 480 else 720
        val w = dm.widthPixels.coerceIn(minSide, maxSide)
        val h = dm.heightPixels.coerceIn(minSide, maxSide)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.shader = LinearGradient(
            0f, 0f, w * 0.8f, h.toFloat(),
            intArrayOf(
                Color.parseColor("#07152B"),
                Color.parseColor("#0D2B52"),
                Color.parseColor("#123C73"),
                Color.parseColor("#081933")
            ),
            floatArrayOf(0f, 0.35f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        paint.shader = RadialGradient(
            w * 0.75f, h * 0.38f, w * 0.95f,
            intArrayOf(Color.parseColor("#442998E4"), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        val wave1 = Path().apply {
            moveTo(0f, h * 0.56f)
            cubicTo(w * 0.32f, h * 0.42f, w * 0.68f, h * 0.68f, w.toFloat(), h * 0.50f)
            lineTo(w.toFloat(), h.toFloat())
            lineTo(0f, h.toFloat())
            close()
        }
        paint.color = Color.parseColor("#2438BDF8")
        canvas.drawPath(wave1, paint)

        val wave2 = Path().apply {
            moveTo(0f, h * 0.66f)
            cubicTo(w * 0.38f, h * 0.78f, w * 0.65f, h * 0.48f, w.toFloat(), h * 0.62f)
            lineTo(w.toFloat(), h.toFloat())
            lineTo(0f, h.toFloat())
            close()
        }
        paint.color = Color.parseColor("#2E0EA5E9")
        canvas.drawPath(wave2, paint)

        val wave3 = Path().apply {
            moveTo(0f, h * 0.76f)
            cubicTo(w * 0.28f, h * 0.66f, w * 0.74f, h * 0.86f, w.toFloat(), h * 0.74f)
            lineTo(w.toFloat(), h.toFloat())
            lineTo(0f, h.toFloat())
            close()
        }
        paint.color = Color.parseColor("#380369A1")
        canvas.drawPath(wave3, paint)

        val crest = Path().apply {
            moveTo(0f, h * 0.56f)
            cubicTo(w * 0.32f, h * 0.42f, w * 0.68f, h * 0.68f, w.toFloat(), h * 0.50f)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.parseColor("#557DD3FC")
        canvas.drawPath(crest, paint)

        return bmp
    }

    private fun decodeSampledBitmap(file: File, maxDim: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sampleSize = 1
            while ((bounds.outWidth / sampleSize) > maxDim || (bounds.outHeight / sampleSize) > maxDim) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (_: Exception) {
            null
        }
    }

    private fun canUseSystemWallpaper(): Boolean {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            return false
        }
        return try {
            val wm = WallpaperManager.getInstance(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wm.isWallpaperSupported && wm.isSetWallpaperAllowed
            } else {
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun configureWallpaperWindowMode() {
        if (canUseSystemWallpaper()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            rootContainer.setBackgroundColor(Color.TRANSPARENT)
            wallpaperView.visibility = View.GONE
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            val fallbackColor = Color.parseColor("#07152B")
            window.setBackgroundDrawable(ColorDrawable(fallbackColor))
            rootContainer.setBackgroundColor(fallbackColor)
            wallpaperView.visibility = View.VISIBLE
        }
    }

    private fun resolveWallpaperBitmap(): Bitmap {
        val destFile = File(filesDir, "custom_wallpaper.img")
        if (prefs.getBoolean("has_custom_wallpaper", false) && destFile.exists()) {
            val dm = resources.displayMetrics
            val cap = if (isLowRamDevice()) 1080 else 2560
            val maxDim = maxOf(dm.widthPixels, dm.heightPixels, if (isLowRamDevice()) 960 else 1920).coerceAtMost(cap)
            val decoded = decodeSampledBitmap(destFile, maxDim)
            if (decoded != null) return decoded
        }
        return createMozzoWaveBitmap()
    }

    private fun applySavedWallpaper(forceSystemSync: Boolean = false) {
        if (canUseSystemWallpaper()) {
            wallpaperView.visibility = View.GONE
            if (!forceSystemSync) return
            val bmp = resolveWallpaperBitmap()
            try {
                if (!wallpaperExecutor.isShutdown) {
                    wallpaperExecutor.execute {
                        try {
                            WallpaperManager.getInstance(this).setBitmap(bmp)
                            prefs.edit().putBoolean("system_wallpaper_synced", true).apply()
                        } catch (_: Exception) {
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) {
                                    wallpaperView.setImageBitmap(bmp)
                                    wallpaperView.visibility = View.VISIBLE
                                    rootContainer.setBackgroundColor(Color.parseColor("#07152B"))
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                wallpaperView.setImageBitmap(bmp)
                wallpaperView.visibility = View.VISIBLE
            }
            return
        }

        wallpaperView.setImageBitmap(resolveWallpaperBitmap())
        wallpaperView.visibility = View.VISIBLE
    }

    private fun applyClockVisibility(show: Boolean) {
        clockContainer.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun applyPanelOpacity(percent: Int) {
        val alpha = (percent.coerceIn(0, 100) * 255) / 100
        val fillColor = Color.argb(alpha, 9, 13, 20)
        val strokeColor = if (percent == 0) Color.TRANSPARENT else Color.argb(64, 148, 163, 184)
        val side = prefs.getString("dock_side", "left") ?: "left"
        val r = dp(24).toFloat()

        dockContainer.background = GradientDrawable().apply {
            setColor(fillColor)
            setStroke(dp(1), strokeColor)
            cornerRadii = when (side) {
                "right" -> floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
                "bottom" -> floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                else -> floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
            }
        }
        drawerPanel.background = GradientDrawable().apply {
            setColor(fillColor)
            setStroke(dp(1), strokeColor)
            cornerRadius = r
        }
    }

    private fun applyDockSide(side: String) {
        val isBottom = side == "bottom"
        val isRight = side == "right"

        val dockLp = dockContainer.layoutParams as FrameLayout.LayoutParams
        dockLp.marginStart = 0
        dockLp.marginEnd = 0
        if (isBottom) {
            dockContainer.orientation = LinearLayout.HORIZONTAL
            dockContainer.setPadding(dp(8), dp(5), dp(8), dp(6) + insetBottom)
            dockLp.width = ViewGroup.LayoutParams.WRAP_CONTENT
            dockLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            dockLp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            dockLp.setMargins(
                dp(12) + insetLeft,
                0,
                dp(12) + insetRight,
                0
            )
        } else {
            dockContainer.orientation = LinearLayout.VERTICAL
            dockContainer.setPadding(dp(5), dp(6), dp(5), dp(6))
            dockLp.width = ViewGroup.LayoutParams.WRAP_CONTENT
            dockLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            dockLp.gravity = (if (isRight) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
            dockLp.setMargins(
                if (isRight) 0 else insetLeft,
                dp(16) + insetTop,
                if (isRight) insetRight else 0,
                dp(16) + insetBottom
            )
        }
        dockContainer.layoutParams = dockLp

        val oldScroll = dockContainer.getChildAt(0)
        (dockFavoritesContainer.parent as? ViewGroup)?.removeView(dockFavoritesContainer)
        if (oldScroll != null && oldScroll !== btnDrawerMozzo) {
            dockContainer.removeView(oldScroll)
        }

        dockFavoritesContainer.orientation = if (isBottom) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        val newScroll: ViewGroup = if (isBottom) {
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(dockFavoritesContainer)
            }
        } else {
            ScrollView(this).apply {
                isVerticalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 0, 1f)
                addView(dockFavoritesContainer)
            }
        }
        dockContainer.addView(newScroll, 0)

        applyPanelOpacity(prefs.getInt("panel_opacity_pct", 75))

        val homeLp = homeCenterColumn.layoutParams as FrameLayout.LayoutParams
        val drawerLp = drawerPanel.layoutParams as FrameLayout.LayoutParams
        homeLp.marginStart = 0
        homeLp.marginEnd = 0
        drawerLp.marginStart = 0
        drawerLp.marginEnd = 0

        val screenW = resources.displayMetrics.widthPixels
        val isWide = screenW > resources.displayMetrics.heightPixels
        val dPad = dp(8)
        drawerLp.width = screenW - (dPad * 2) - insetLeft - insetRight
        drawerLp.height = ViewGroup.LayoutParams.MATCH_PARENT
        drawerLp.gravity = Gravity.CENTER
        drawerLp.setMargins(
            dPad + insetLeft,
            dp(8) + insetTop,
            dPad + insetRight,
            dp(8) + insetBottom
        )
        drawerPanel.setPadding(dp(10), dp(10), dp(10), dp(10))

        val searchBar = searchInput.parent as View
        (searchBar.layoutParams as LinearLayout.LayoutParams).apply {
            width = minOf(drawerLp.width - dp(24), dp(360))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val iconSizeDp = prefs.getInt("icon_size_dp", 48)
        val baseTop = if (prefs.getBoolean("show_clock", true)) dp(112) else dp(24)
        val topClockSpace = insetTop + baseTop

        if (isBottom) {
            val sPad = dp(14)
            homeLp.width = if (isWide) {
                (screenW * 0.56f).toInt()
            } else {
                screenW - sPad * 2 - insetLeft - insetRight
            }
            homeCenterColumn.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            homeLp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            val bottomDockReserve = dp(iconSizeDp + 18) + insetBottom
            homeLp.setMargins(
                sPad + insetLeft,
                topClockSpace,
                sPad + insetRight,
                bottomDockReserve
            )
        } else {
            val dockSpace = dp(iconSizeDp + 18)
            val outerEdge = dp(12)
            val oppGravity = if (isRight) Gravity.START else Gravity.END

            val availW = screenW - dockSpace - outerEdge - insetLeft - insetRight
            homeLp.width = if (isWide) (availW * 0.65f).toInt() else availW
            homeCenterColumn.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            homeLp.gravity = oppGravity or Gravity.TOP

            val leftM = if (isRight) outerEdge + insetLeft else dockSpace + insetLeft
            val rightM = if (isRight) dockSpace + insetRight else outerEdge + insetRight
            homeLp.setMargins(leftM, topClockSpace, rightM, dp(16) + insetBottom)
        }
        homeCenterColumn.layoutParams = homeLp
        drawerPanel.layoutParams = drawerLp

        val bannerLp = editModeBanner.layoutParams as FrameLayout.LayoutParams
        bannerLp.bottomMargin = if (isBottom) dp(iconSizeDp + 28) + insetBottom else dp(20) + insetBottom
        editModeBanner.layoutParams = bannerLp

        appsGridView.columnWidth = dp(iconSizeDp + 16)
        widgetsContainer.post { syncWidgetFocusAndSizes() }

        renderDockFavorites()
    }

    private fun openDrawer() {
        try {
            setHomeFocusBlocked(true)
            homeCenterColumn.visibility = View.INVISIBLE
            clockContainer.visibility = View.INVISIBLE
            dockContainer.visibility = View.INVISIBLE
            drawerPanel.visibility = View.VISIBLE
            if (!searchInput.text.isNullOrEmpty()) {
                searchInput.setText("")
            } else {
                filterItems("")
            }
            searchInput.clearFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
            appsGridView.post {
                appsGridView.requestFocus()
                if (filteredApps.isNotEmpty()) {
                    appsGridView.setSelection(0)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.toast_drawer_error_fmt, e.message ?: ""),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun closeDrawer() {
        drawerPanel.visibility = View.GONE
        homeCenterColumn.visibility = View.VISIBLE
        applyClockVisibility(prefs.getBoolean("show_clock", true))
        dockContainer.visibility = View.VISIBLE
        setHomeFocusBlocked(false)
        searchInput.clearFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
        focusDrawerButtonOnHome()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (movingDockEntry != null || editingWidgetId != null) {
            exitEditMode(commit = false)
            return
        }
        if (drawerPanel.visibility == View.VISIBLE) {
            closeDrawer()
        }
    }

    private fun handleInstallShortcutIntent(intent: Intent?) {
        if (intent == null) return
        val name = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: return
        @Suppress("DEPRECATION")
        val targetIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT) ?: return
        @Suppress("DEPRECATION")
        val iconBmp = intent.getParcelableExtra<Bitmap>(Intent.EXTRA_SHORTCUT_ICON)
        val uriStr = targetIntent.toUri(Intent.URI_INTENT_SCHEME)

        val arr = try {
            JSONArray(prefs.getString("web_shortcuts_json", "[]"))
        } catch (_: Exception) {
            JSONArray()
        }
        val id = "shortcut_${System.currentTimeMillis()}"
        val obj = JSONObject().apply {
            put("id", id)
            put("name", name)
            put("uri", uriStr)
            if (iconBmp != null) {
                val baos = ByteArrayOutputStream()
                iconBmp.compress(Bitmap.CompressFormat.PNG, 90, baos)
                put("icon", Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP))
            }
        }
        arr.put(obj)
        prefs.edit().putString("web_shortcuts_json", arr.toString()).apply()
        loadInstalledApps()
        Toast.makeText(this, getString(R.string.toast_shortcut_added, name), Toast.LENGTH_SHORT).show()
    }

    private fun getSavedWebShortcuts(): List<AppItem> {
        val result = mutableListOf<AppItem>()
        val arr = try {
            JSONArray(prefs.getString("web_shortcuts_json", "[]"))
        } catch (_: Exception) {
            return emptyList()
        }
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val name = obj.optString("name")
            val uri = obj.optString("uri")
            if (id.isEmpty() || name.isEmpty() || uri.isEmpty()) continue
            val iconB64 = obj.optString("icon", "")
            val drawable: Drawable = if (iconB64.isNotEmpty()) {
                try {
                    val bytes = Base64.decode(iconB64, Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    BitmapDrawable(resources, bmp)
                } catch (_: Exception) {
                    resources.getDrawable(android.R.drawable.ic_menu_compass, theme)
                }
            } else {
                resources.getDrawable(android.R.drawable.ic_menu_compass, theme)
            }
            result.add(AppItem(id, name, normalize(name), id, "", drawable, shortcutIntentUri = uri))
        }
        return result
    }

    private fun removeWebShortcut(id: String) {
        val arr = try {
            JSONArray(prefs.getString("web_shortcuts_json", "[]"))
        } catch (_: Exception) {
            return
        }
        val next = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optString("id") != id) next.put(obj)
        }
        prefs.edit().putString("web_shortcuts_json", next.toString()).apply()
        val favs = getDockFavorites()
        if (favs.remove(id)) saveDockFavorites(favs)
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        val seenKeys = HashSet<String>()
        val seenPackages = HashSet<String>()
        val myPkg = packageName
        val loaded = mutableListOf<AppItem>()

        fun queryCategory(cat: String, skipIfPackageSeen: Boolean) {
            val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(cat) }
            val list: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
            }
            for (ri in list) {
                val pkg = ri.activityInfo.packageName
                if (pkg == myPkg) continue
                if (skipIfPackageSeen && seenPackages.contains(pkg)) continue
                val act = ri.activityInfo.name
                val key = "$pkg/$act"
                if (!seenKeys.add(key)) continue
                seenPackages.add(pkg)
                val label = ri.loadLabel(packageManager).toString().ifEmpty { pkg }
                val icon = ri.loadIcon(packageManager)
                loaded.add(AppItem(pkg, label, normalize(label), pkg, act, icon))
            }
        }

        queryCategory(Intent.CATEGORY_LAUNCHER, skipIfPackageSeen = false)
        queryCategory("android.intent.category.LEANBACK_LAUNCHER", skipIfPackageSeen = true)
        loaded.addAll(getSavedWebShortcuts())

        val launcherIcon = try {
            packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            resources.getDrawable(android.R.drawable.ic_menu_preferences, theme)
        }
        loaded.add(
            AppItem(
                id = "internal.mozzo.settings",
                label = getString(R.string.settings_app_label),
                normalizedLabel = "mozzo settings launcher wallpaper dock icons ${normalize(getString(R.string.kw_mozzo_settings))}",
                packageName = "internal.mozzo.settings",
                activityName = "",
                icon = launcherIcon,
                isLauncherSettings = true
            )
        )

        allApps = loaded.sortedBy { it.normalizedLabel }
        filterItems(searchInput.text?.toString() ?: "")
        renderDockFavorites()
    }

    private fun filterItems(rawQuery: String) {
        val q = normalize(rawQuery.trim())
        if (q.isEmpty()) {
            filteredApps = allApps
            filteredSettings = emptyList()
            settingsResultsList.visibility = View.GONE
        } else {
            filteredApps = allApps.filter {
                it.normalizedLabel.contains(q) || it.packageName.lowercase(Locale.ROOT).contains(q)
            }
            filteredSettings = settingShortcuts.filter {
                normalize(it.title).contains(q) || normalize(it.keywords).contains(q)
            }
            settingsResultsList.visibility = if (filteredSettings.isEmpty()) View.GONE else View.VISIBLE
        }
        appsAdapter.notifyDataSetChanged()
        settingsAdapter.notifyDataSetChanged()
    }

    private fun openSettingAction(action: String) {
        try {
            startActivity(Intent(action))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.toast_setting_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchApp(app: AppItem) {
        if (app.isLauncherSettings) {
            showLauncherSettingsDialog()
            return
        }
        if (app.shortcutIntentUri != null) {
            try {
                val parsed = Intent.parseUri(app.shortcutIntentUri, Intent.URI_INTENT_SCHEME).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(parsed)
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.toast_setting_error), Toast.LENGTH_SHORT).show()
            }
            return
        }
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(app.packageName, app.activityName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            startActivity(intent)
        } catch (_: Exception) {
            val fallback = packageManager.getLaunchIntentForPackage(app.packageName)
            if (fallback != null) {
                startActivity(fallback)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val leanback = packageManager.getLeanbackLaunchIntentForPackage(app.packageName)
                if (leanback != null) startActivity(leanback)
            }
        }
    }

    private fun openAppDetails(pkg: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$pkg")
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.toast_setting_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAppOptionsDialog(app: AppItem) {
        if (app.isLauncherSettings) {
            openAppDetails(packageName)
            return
        }
        val favorites = getDockFavorites()
        val isFav = favorites.contains(app.packageName)
        val favLabel = if (isFav) getString(R.string.dock_remove) else getString(R.string.dock_add)

        val options = if (app.shortcutIntentUri != null) {
            arrayOf(favLabel, getString(R.string.shortcut_delete))
        } else {
            arrayOf(favLabel, getString(R.string.app_info))
        }

        val dlg = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(app.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> toggleDockFavorite(app.packageName)
                    1 -> {
                        if (app.shortcutIntentUri != null) {
                            removeWebShortcut(app.packageName)
                        } else {
                            openAppDetails(app.packageName)
                        }
                    }
                }
            }
            .create()
        trackSecondaryDialog(dlg)
    }

    private fun getDockFavorites(): MutableList<String> {
        val raw = prefs.getString("dock_favorites", null)
        val hasV2Defaults = prefs.getBoolean("dock_defaults_v2", false)
        val existing = if (raw.isNullOrEmpty()) {
            mutableListOf()
        } else {
            raw.split(",").filter { it.isNotBlank() }.toMutableList()
        }

        val needsInitial = raw == null || (raw.isEmpty() && !prefs.getBoolean("dock_initialized", false))
        if (!needsInitial && hasV2Defaults) {
            return existing
        }
        if (allApps.isEmpty()) {
            return existing
        }

        val launchablePkgs = allApps.asSequence()
            .filter { !it.isLauncherSettings && it.shortcutIntentUri == null }
            .map { it.packageName }
            .toSet()
        val knownAppOrShortcutIds = allApps.asSequence()
            .filter { !it.isLauncherSettings }
            .map { it.packageName }
            .toSet()

        val validExisting = existing.filter { entry ->
            entry.startsWith("setting:") || entry in knownAppOrShortcutIds
        }

        if (!needsInitial && validExisting.size >= 4) {
            prefs.edit().putBoolean("dock_defaults_v2", true).apply()
            return existing
        }

        fun resolveFirstLaunchablePkg(intent: Intent): String? {
            val direct = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.resolveActivity(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }?.activityInfo?.packageName
            if (direct != null && direct != "android" && direct != packageName && direct in launchablePkgs) {
                return direct
            }
            val candidates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
            }
            return candidates.asSequence()
                .mapNotNull { it.activityInfo?.packageName }
                .firstOrNull { it != "android" && it != packageName && it in launchablePkgs }
        }

        val defaults = validExisting.toMutableList()
        val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val preferredIntents = if (isTv) {
            listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MARKET),
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://")),
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC),
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY)
            )
        } else {
            listOf(
                Intent(Intent.ACTION_DIAL),
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://")),
                Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            )
        }
        for (intent in preferredIntents) {
            if (defaults.size >= 4) break
            val pkg = resolveFirstLaunchablePkg(intent)
            if (pkg != null && !defaults.contains(pkg)) {
                defaults.add(pkg)
            }
        }
        if (defaults.size < 4) {
            for (app in allApps) {
                if (app.isLauncherSettings || app.shortcutIntentUri != null) continue
                if (!defaults.contains(app.packageName)) {
                    defaults.add(app.packageName)
                }
                if (defaults.size >= 4) break
            }
        }
        if (defaults.isNotEmpty()) {
            saveDockFavorites(defaults)
        } else {
            prefs.edit().putBoolean("dock_defaults_v2", true).apply()
        }
        return defaults
    }

    private fun saveDockFavorites(list: List<String>) {
        prefs.edit()
            .putBoolean("dock_initialized", true)
            .putBoolean("dock_defaults_v2", true)
            .putString("dock_favorites", list.joinToString(","))
            .apply()
    }

    private fun toggleDockFavorite(pkg: String) {
        if (movingDockEntry == pkg) {
            exitEditMode(commit = true)
        }
        val list = getDockFavorites()
        if (list.contains(pkg)) {
            list.remove(pkg)
            Toast.makeText(this, getString(R.string.toast_dock_removed), Toast.LENGTH_SHORT).show()
        } else {
            list.add(pkg)
            Toast.makeText(this, getString(R.string.toast_dock_added), Toast.LENGTH_SHORT).show()
        }
        saveDockFavorites(list)
        renderDockFavorites()
    }

    private fun moveDockFavorite(entry: String, delta: Int) {
        val list = getDockFavorites()
        val idx = list.indexOf(entry)
        if (idx == -1) return
        val newIdx = (idx + delta).coerceIn(0, list.lastIndex)
        if (newIdx == idx) return
        list.removeAt(idx)
        list.add(newIdx, entry)
        saveDockFavorites(list)
        renderDockFavorites()
    }

    private fun moveDockFavoriteToTarget(activeEntry: String, targetEntry: String) {
        val list = getDockFavorites()
        val fromIdx = list.indexOf(activeEntry)
        val toIdx = list.indexOf(targetEntry)
        if (fromIdx == -1 || toIdx == -1 || fromIdx == toIdx) return
        list.removeAt(fromIdx)
        list.add(toIdx, activeEntry)
        saveDockFavorites(list)
        renderDockFavorites()
    }

    private fun startDockMoveMode(entry: String) {
        val current = getDockFavorites()
        if (!current.contains(entry)) return
        exitEditMode(commit = true)
        preMoveDockSnapshot = current.toList()
        movingDockEntry = entry
        renderDockFavorites()
        updateEditModeBanner()
    }

    private fun exitEditMode(commit: Boolean) {
        val hadDockMove = movingDockEntry != null
        val hadWidgetEdit = editingWidgetId != null
        if (!hadDockMove && !hadWidgetEdit) return

        if (hadDockMove) {
            if (!commit) {
                preMoveDockSnapshot?.let { saveDockFavorites(it) }
            }
            movingDockEntry = null
            preMoveDockSnapshot = null
            renderDockFavorites()
        }

        var focusRestoredToWidget = false
        if (hadWidgetEdit) {
            val prevId = editingWidgetId
            if (!commit) {
                preEditWidgetSnapshot?.let { snap ->
                    widgetEntries.clear()
                    widgetEntries.addAll(snap.map { it.copy() })
                    saveWidgetEntries()
                }
            }
            editingWidgetId = null
            widgetEditMode = null
            preEditWidgetSnapshot = null
            rebuildWidgetsUI()
            if (commit && prevId != null) {
                val cell = widgetCellViews[prevId]
                if (cell != null) {
                    cell.requestFocus()
                    focusRestoredToWidget = true
                }
            }
        }

        updateEditModeBanner()
        if (!focusRestoredToWidget) {
            focusDrawerButtonOnHome()
        }
    }

    private fun handleEditModeDpad(keyCode: Int): Boolean {
        val dockEntry = movingDockEntry
        if (dockEntry != null) {
            val isBottom = (prefs.getString("dock_side", "left") ?: "left") == "bottom"
            if (isBottom) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> moveDockFavorite(dockEntry, -1)
                    KeyEvent.KEYCODE_DPAD_RIGHT -> moveDockFavorite(dockEntry, 1)
                }
            } else {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> moveDockFavorite(dockEntry, -1)
                    KeyEvent.KEYCODE_DPAD_DOWN -> moveDockFavorite(dockEntry, 1)
                }
            }
            return true
        }

        val widgetId = editingWidgetId ?: return false
        when (widgetEditMode) {
            WidgetEditMode.MOVE -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> moveWidgetEntryByDelta(widgetId, -1)
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> moveWidgetEntryByDelta(widgetId, 1)
                }
                return true
            }
            WidgetEditMode.RESIZE -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> adjustEditingWidgetSpan(-1, 0)
                    KeyEvent.KEYCODE_DPAD_RIGHT -> adjustEditingWidgetSpan(1, 0)
                    KeyEvent.KEYCODE_DPAD_UP -> adjustEditingWidgetSpan(0, -1)
                    KeyEvent.KEYCODE_DPAD_DOWN -> adjustEditingWidgetSpan(0, 1)
                }
                return true
            }
            null -> return false
        }
    }

    private fun updateEditModeBanner() {
        if (movingDockEntry == null && editingWidgetId == null) {
            editModeBanner.visibility = View.GONE
            editModeBanner.removeAllViews()
            return
        }

        editModeBanner.removeAllViews()
        editModeBanner.visibility = View.VISIBLE

        val msg = when {
            movingDockEntry != null -> getString(R.string.banner_dock_move)
            widgetEditMode == WidgetEditMode.MOVE -> getString(R.string.banner_widget_move)
            else -> {
                val entry = widgetEntries.firstOrNull { it.id == editingWidgetId }
                getString(R.string.banner_widget_resize_fmt, entry?.spanX ?: 1, entry?.spanY ?: 1)
            }
        }

        val label = TextView(this).apply {
            text = msg
            textSize = 12f
            setTextColor(Color.parseColor("#F8FAFC"))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(8)
            }
        }
        editModeBanner.addView(label)

        fun makePillButton(textLabel: String, isPrimary: Boolean = false, onClick: () -> Unit): TextView {
            return TextView(this).apply {
                text = textLabel
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                isFocusable = false
                isClickable = true
                setPadding(dp(8), dp(5), dp(8), dp(5))
                background = GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(if (isPrimary) Color.parseColor("#0284C7") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (isPrimary) Color.parseColor("#38BDF8") else Color.parseColor("#334155"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dp(4)
                }
                setOnClickListener { onClick() }
            }
        }

        if (editingWidgetId != null && widgetEditMode == WidgetEditMode.RESIZE) {
            editModeBanner.addView(makePillButton("W-") { adjustEditingWidgetSpan(-1, 0) })
            editModeBanner.addView(makePillButton("W+") { adjustEditingWidgetSpan(1, 0) })
            editModeBanner.addView(makePillButton("H-") { adjustEditingWidgetSpan(0, -1) })
            editModeBanner.addView(makePillButton("H+") { adjustEditingWidgetSpan(0, 1) })
        }

        val doneBtn = makePillButton(getString(R.string.btn_done), isPrimary = true) {
            exitEditMode(commit = true)
        }.apply {
            tag = "edit_mode_done_btn"
        }
        editModeBanner.addView(doneBtn)
    }

    private fun showDockItemMenu(entry: String, title: String, app: AppItem?) {
        val list = getDockFavorites()
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        if (list.size > 1 && list.contains(entry)) {
            options.add(getString(R.string.dock_move))
            actions.add { startDockMoveMode(entry) }
        }

        options.add(getString(R.string.dock_remove))
        actions.add { toggleDockFavorite(entry) }

        if (app != null && !app.isLauncherSettings && app.shortcutIntentUri == null) {
            options.add(getString(R.string.app_info))
            actions.add { openAppDetails(app.packageName) }
        }

        val dlg = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setItems(options.toTypedArray()) { _, which ->
                actions[which].invoke()
            }
            .create()
        trackSecondaryDialog(dlg)
    }

    private fun renderDockFavorites() {
        dockFavoritesContainer.removeAllViews()
        val favPkgs = getDockFavorites()
        val appMap = allApps.associateBy { it.packageName }
        val iconSizePx = dp(prefs.getInt("icon_size_dp", 48))
        val side = prefs.getString("dock_side", "left") ?: "left"
        val isBottom = side == "bottom"

        btnDrawerMozzo.visibility = View.VISIBLE
        btnDrawerMozzo.isFocusable = true
        val drawerLp = btnDrawerMozzo.layoutParams as LinearLayout.LayoutParams
        drawerLp.width = iconSizePx
        drawerLp.height = iconSizePx
        if (isBottom) {
            drawerLp.setMargins(dp(4), 0, 0, 0)
        } else {
            drawerLp.setMargins(0, dp(4), 0, 0)
        }
        btnDrawerMozzo.layoutParams = drawerLp

        fun makeItemLp() = LinearLayout.LayoutParams(iconSizePx, iconSizePx).apply {
            if (isBottom) {
                leftMargin = dp(2)
                rightMargin = dp(2)
            } else {
                topMargin = dp(2)
                bottomMargin = dp(2)
            }
        }

        for (entry in favPkgs) {
            val isMoving = entry == movingDockEntry

            if (entry.startsWith("setting:")) {
                val parts = entry.removePrefix("setting:").split("|", limit = 2)
                val title = parts.getOrElse(0) { getString(R.string.set_system) }
                val action = parts.getOrElse(1) { Settings.ACTION_SETTINGS }
                val btn = ImageButton(this).apply {
                    layoutParams = makeItemLp()
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageResource(android.R.drawable.ic_menu_manage)
                    setColorFilter(Color.parseColor("#38BDF8"))
                    isFocusable = true
                    isSelected = isMoving
                    setBackgroundResource(R.drawable.bg_tv_focus_item)
                    contentDescription = title
                    setOnClickListener {
                        val active = movingDockEntry
                        if (active != null) {
                            if (active != entry) {
                                moveDockFavoriteToTarget(active, entry)
                            }
                            exitEditMode(commit = true)
                        } else {
                            openSettingAction(action)
                        }
                    }
                    setOnLongClickListener {
                        triggerHaptic()
                        showDockItemMenu(entry, title, null)
                        true
                    }
                }
                dockFavoritesContainer.addView(btn)
                if (isMoving) {
                    btn.post { if (movingDockEntry == entry) btn.requestFocus() }
                }
                continue
            }

            val app = appMap[entry] ?: continue
            val btn = ImageButton(this).apply {
                layoutParams = makeItemLp()
                setPadding(dp(2), dp(2), dp(2), dp(2))
                scaleType = ImageView.ScaleType.FIT_CENTER
                clearColorFilter()
                setImageDrawable(app.icon)
                isFocusable = true
                isSelected = isMoving
                setBackgroundResource(R.drawable.bg_tv_focus_item)
                contentDescription = app.label
                setOnClickListener {
                    val active = movingDockEntry
                    if (active != null) {
                        if (active != entry) {
                            moveDockFavoriteToTarget(active, entry)
                        }
                        exitEditMode(commit = true)
                    } else {
                        launchApp(app)
                    }
                }
                setOnLongClickListener {
                    triggerHaptic()
                    showDockItemMenu(entry, app.label, app)
                    true
                }
            }
            dockFavoritesContainer.addView(btn)
            if (isMoving) {
                btn.post { if (movingDockEntry == entry) btn.requestFocus() }
            }
        }

        focusDrawerButtonOnHome()
    }

    private fun normalize(input: String): String {
        val temp = Normalizer.normalize(input, Normalizer.Form.NFD)
        return temp.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase(Locale.ROOT)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class AppViewHolder(
        val iconView: ImageView,
        val labelView: TextView
    )

    inner class AppsAdapter : BaseAdapter() {
        override fun getCount(): Int = filteredApps.size
        override fun getItem(position: Int): AppItem = filteredApps[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: View
            val holder: AppViewHolder
            if (convertView == null) {
                view = LayoutInflater.from(this@MainActivity)
                    .inflate(R.layout.item_app, parent, false)
                holder = AppViewHolder(
                    view.findViewById(R.id.appIcon),
                    view.findViewById(R.id.appLabel)
                )
                view.tag = holder
            } else {
                view = convertView
                holder = convertView.tag as AppViewHolder
            }

            val app = getItem(position)
            val iconSizePx = dp(prefs.getInt("icon_size_dp", 48))
            val lp = holder.iconView.layoutParams
            if (lp.width != iconSizePx || lp.height != iconSizePx) {
                lp.width = iconSizePx
                lp.height = iconSizePx
                holder.iconView.layoutParams = lp
            }

            holder.iconView.setImageDrawable(app.icon)
            holder.labelView.text = app.label
            return view
        }
    }

    inner class SettingsAdapter : BaseAdapter() {
        override fun getCount(): Int = filteredSettings.size
        override fun getItem(position: Int): SettingShortcut = filteredSettings[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tv = (convertView as? TextView) ?: TextView(this@MainActivity).apply {
                setPadding(dp(12), dp(10), dp(12), dp(10))
                textSize = 14f
                setTextColor(Color.parseColor("#38BDF8"))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            tv.text = getString(R.string.setting_prefix_fmt, getItem(position).title)
            return tv
        }
    }
}
