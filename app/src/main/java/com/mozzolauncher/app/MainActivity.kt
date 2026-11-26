package com.mozzolauncher.app

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
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
        var heightDp: Int
    )

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
    private lateinit var widgetsContainer: LinearLayout
    private lateinit var dockContainer: LinearLayout
    private lateinit var dockFavoritesContainer: LinearLayout
    private lateinit var btnDrawerMozzo: ImageButton
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

    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost
    private val widgetEntries = mutableListOf<WidgetEntry>()
    private var pendingWidgetId: Int = -1
    private var settingsDialog: AlertDialog? = null
    private var insetTop = 0
    private var insetBottom = 0
    private var insetLeft = 0
    private var insetRight = 0

    private val REQ_PICK_WALLPAPER = 1001
    private val REQ_PICK_WIDGET = 1002
    private val REQ_CREATE_WIDGET = 1003
    private val REQ_BIND_WIDGET = 1004
    private val APPWIDGET_HOST_ID = 2048

    private val systemDockIcons = listOf(
        "default" to 0,
        "settings" to android.R.drawable.ic_menu_preferences,
        "manage" to android.R.drawable.ic_menu_manage,
        "call" to android.R.drawable.ic_menu_call,
        "compass" to android.R.drawable.ic_menu_compass,
        "camera" to android.R.drawable.ic_menu_camera,
        "gallery" to android.R.drawable.ic_menu_gallery,
        "search" to android.R.drawable.ic_menu_search,
        "share" to android.R.drawable.ic_menu_share,
        "send" to android.R.drawable.ic_menu_send,
        "info" to android.R.drawable.ic_menu_info_details,
        "map" to android.R.drawable.ic_dialog_map,
        "email" to android.R.drawable.ic_dialog_email,
        "dialer" to android.R.drawable.ic_dialog_dialer,
        "star" to android.R.drawable.btn_star_big_on,
        "play" to android.R.drawable.ic_media_play,
        "folder" to android.R.drawable.ic_menu_agenda,
        "edit" to android.R.drawable.ic_menu_edit,
        "directions" to android.R.drawable.ic_menu_directions,
        "today" to android.R.drawable.ic_menu_today
    )

    private val settingShortcuts by lazy {
        listOf(
            SettingShortcut(getString(R.string.set_wifi), "wifi internet network wireless connection rete connessione", Settings.ACTION_WIFI_SETTINGS),
            SettingShortcut(getString(R.string.set_tethering), "hotspot tethering router usb wifi share connection condividi rete", Settings.ACTION_WIRELESS_SETTINGS),
            SettingShortcut(getString(R.string.set_bluetooth), "bluetooth devices headphones dispositivi cuffie", Settings.ACTION_BLUETOOTH_SETTINGS),
            SettingShortcut(getString(R.string.set_display), "display screen brightness font text schermo luminosita carattere", Settings.ACTION_DISPLAY_SETTINGS),
            SettingShortcut(getString(R.string.set_sound), "sound volume ringtone vibration audio suono suoneria vibrazione", Settings.ACTION_SOUND_SETTINGS),
            SettingShortcut(getString(R.string.set_battery), "battery power saving charging batteria risparmio energetico ricarica", Intent.ACTION_POWER_USAGE_SUMMARY),
            SettingShortcut(getString(R.string.set_apps), "apps applications manage uninstall applicazioni gestisci disinstalla", Settings.ACTION_APPLICATION_SETTINGS),
            SettingShortcut(getString(R.string.set_storage), "storage memory space archiviazione memoria spazio", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
            SettingShortcut(getString(R.string.set_location), "location gps position posizione localizzazione", Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            SettingShortcut(getString(R.string.set_security), "security fingerprint pin password lock sicurezza impronta sblocco", Settings.ACTION_SECURITY_SETTINGS),
            SettingShortcut(getString(R.string.set_date), "date time timezone data ora fuso orario", Settings.ACTION_DATE_SETTINGS),
            SettingShortcut(getString(R.string.set_keyboard), "language keyboard input lingua tastiera immissione", Settings.ACTION_INPUT_METHOD_SETTINGS),
            SettingShortcut(getString(R.string.set_about), "about phone device info system android info telefono sistema versione", Settings.ACTION_DEVICE_INFO_SETTINGS),
            SettingShortcut(getString(R.string.set_system), "system settings impostazioni sistema", Settings.ACTION_SETTINGS)
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
        setupTransparentSystemBars()
        setContentView(R.layout.activity_main)

        bindViews()
        setupWidgetHost()
        setupAdapters()
        setupListeners()

        applySavedWallpaper()
        loadInstalledApps()
        applyDockSide(prefs.getString("dock_side", "left") ?: "left")
        applyPanelOpacity(prefs.getInt("panel_opacity_pct", 75))
        applyClockVisibility(prefs.getBoolean("show_clock", true))
        refreshClockFormat()
        restoreAllWidgets()
        handleInstallShortcutIntent(intent)
        focusDrawerButtonOnHome()

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
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupTransparentSystemBars()
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
        settingsDialog?.dismiss()
        if (drawerPanel.visibility == View.VISIBLE) {
            closeDrawer()
        } else {
            focusDrawerButtonOnHome()
        }
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
            appWidgetHost.startListening()
        } catch (_: Exception) {
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            appWidgetHost.stopListening()
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        settingsDialog = null
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
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
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
        drawerPanel = findViewById(R.id.drawerPanel)
        searchInput = findViewById(R.id.searchInput)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        settingsResultsList = findViewById(R.id.settingsResultsList)
        appsGridView = findViewById(R.id.appsGridView)
    }

    private fun setupWidgetHost() {
        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)
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
        if (drawerPanel.visibility != View.VISIBLE && settingsDialog?.isShowing != true) {
            btnDrawerMozzo.visibility = View.VISIBLE
            btnDrawerMozzo.isFocusable = true
            btnDrawerMozzo.post {
                if (drawerPanel.visibility != View.VISIBLE && settingsDialog?.isShowing != true) {
                    btnDrawerMozzo.requestFocus()
                }
            }
        }
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

        wallpaperView.setOnTouchListener { _, event ->
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
            launchApp(filteredApps[position])
            closeDrawer()
        }

        appsGridView.setOnItemLongClickListener { _, _, position, _ ->
            triggerHaptic()
            showAppOptionsDialog(filteredApps[position])
            true
        }

        settingsResultsList.setOnItemClickListener { _, _, position, _ ->
            val setting = filteredSettings[position]
            openSettingAction(setting.action)
            closeDrawer()
        }

        settingsResultsList.setOnItemLongClickListener { _, _, position, _ ->
            triggerHaptic()
            val setting = filteredSettings[position]
            val token = "setting:${setting.title}|${setting.action}"
            val favorites = getDockFavorites()
            val isFav = favorites.contains(token)
            val label = if (isFav) getString(R.string.dock_remove) else getString(R.string.dock_add)
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(getString(R.string.setting_dialog_fmt, setting.title))
                .setItems(arrayOf(label)) { _, _ ->
                    toggleDockFavorite(token)
                }
                .show()
            true
        }
    }

    private fun saveWidgetEntries() {
        val arr = JSONArray()
        for (entry in widgetEntries) {
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("height", entry.heightDp)
            arr.put(obj)
        }
        prefs.edit().putString("widgets_list_json", arr.toString()).apply()
    }

    private fun restoreAllWidgets() {
        widgetEntries.clear()
        val raw = prefs.getString("widgets_list_json", null)
        if (!raw.isNullOrEmpty()) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.getInt("id")
                    val h = obj.optInt("height", 150)
                    if (appWidgetManager.getAppWidgetInfo(id) != null) {
                        widgetEntries.add(WidgetEntry(id, h))
                    } else {
                        try { appWidgetHost.deleteAppWidgetId(id) } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        } else {
            val legacyId = prefs.getInt("appwidget_id", -1)
            if (legacyId != -1 && appWidgetManager.getAppWidgetInfo(legacyId) != null) {
                widgetEntries.add(WidgetEntry(legacyId, 160))
                prefs.edit().remove("appwidget_id").apply()
                saveWidgetEntries()
            }
        }
        rebuildWidgetsUI()
    }

    private fun rebuildWidgetsUI() {
        widgetsContainer.removeAllViews()
        val dockSide = prefs.getString("dock_side", "left") ?: "left"
        for (index in widgetEntries.indices) {
            val entry = widgetEntries[index]
            val info = appWidgetManager.getAppWidgetInfo(entry.id) ?: continue
            val hostView = try {
                appWidgetHost.createView(this, entry.id, info).apply {
                    setAppWidget(entry.id, info)
                }
            } catch (_: Exception) {
                continue
            }

            val cellWrapper = object : FrameLayout(this) {
                private var downX = 0f
                private var downY = 0f
                private var triggered = false
                private val slop = ViewConfiguration.get(context).scaledTouchSlop
                private val longPressTask = Runnable {
                    triggered = true
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    showWidgetMenuDialog(entry)
                }

                override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
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

            val cellLp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(entry.heightDp)
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(6)
                bottomMargin = dp(6)
            }
            cellWrapper.layoutParams = cellLp
            cellWrapper.isFocusable = true
            cellWrapper.setBackgroundResource(R.drawable.bg_tv_focus_item)
            if (dockSide == "left") {
                cellWrapper.nextFocusLeftId = R.id.btnDrawerMozzo
            } else if (dockSide == "right") {
                cellWrapper.nextFocusRightId = R.id.btnDrawerMozzo
            }
            cellWrapper.setOnLongClickListener {
                triggerHaptic()
                showWidgetMenuDialog(entry)
                true
            }

            val hostLp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
            cellWrapper.addView(hostView, hostLp)
            widgetsContainer.addView(cellWrapper)
        }
    }

    private fun showWidgetMenuDialog(entry: WidgetEntry) {
        val index = widgetEntries.indexOfFirst { it.id == entry.id }
        if (index == -1) return

        val options = arrayOf(
            getString(R.string.widget_increase),
            getString(R.string.widget_decrease),
            getString(R.string.widget_move_up),
            getString(R.string.widget_move_down),
            getString(R.string.widget_remove)
        )

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(getString(R.string.widget_menu_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        entry.heightDp = (entry.heightDp + 40).coerceAtMost(680)
                        saveWidgetEntries()
                        rebuildWidgetsUI()
                    }
                    1 -> {
                        entry.heightDp = (entry.heightDp - 40).coerceAtLeast(60)
                        saveWidgetEntries()
                        rebuildWidgetsUI()
                    }
                    2 -> {
                        if (index > 0) {
                            val item = widgetEntries.removeAt(index)
                            widgetEntries.add(index - 1, item)
                            saveWidgetEntries()
                            rebuildWidgetsUI()
                        } else {
                            Toast.makeText(this, getString(R.string.toast_widget_top), Toast.LENGTH_SHORT).show()
                        }
                    }
                    3 -> {
                        if (index < widgetEntries.size - 1) {
                            val item = widgetEntries.removeAt(index)
                            widgetEntries.add(index + 1, item)
                            saveWidgetEntries()
                            rebuildWidgetsUI()
                        } else {
                            Toast.makeText(this, getString(R.string.toast_widget_bottom), Toast.LENGTH_SHORT).show()
                        }
                    }
                    4 -> {
                        widgetEntries.removeAt(index)
                        try { appWidgetHost.deleteAppWidgetId(entry.id) } catch (_: Exception) {}
                        saveWidgetEntries()
                        rebuildWidgetsUI()
                        focusDrawerButtonOnHome()
                        Toast.makeText(this, getString(R.string.toast_widget_removed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun cleanupPendingWidget() {
        if (pendingWidgetId != -1) {
            try { appWidgetHost.deleteAppWidgetId(pendingWidgetId) } catch (_: Exception) {}
            pendingWidgetId = -1
        }
    }

    private fun pickNewWidget() {
        val providers = try {
            appWidgetManager.installedProviders
        } catch (_: Exception) {
            emptyList()
        }
        if (providers.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.toast_no_widgets), Toast.LENGTH_SHORT).show()
            return
        }

        data class ProviderEntry(
            val info: AppWidgetProviderInfo,
            val title: String,
            val appName: String,
            val icon: Drawable?
        )

        val sorted = providers.mapNotNull { info ->
            try {
                val widgetLabel = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                val appLabel = try {
                    val appInfo = packageManager.getApplicationInfo(info.provider.packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    info.provider.packageName
                }
                val icon = try {
                    info.loadIcon(this, resources.displayMetrics.densityDpi)
                } catch (_: Exception) {
                    try {
                        packageManager.getApplicationIcon(info.provider.packageName)
                    } catch (_: Exception) {
                        null
                    }
                }
                ProviderEntry(
                    info = info,
                    title = widgetLabel.ifEmpty { appLabel },
                    appName = appLabel,
                    icon = icon
                )
            } catch (_: Exception) {
                null
            }
        }.sortedWith(compareBy({ it.appName.lowercase(Locale.ROOT) }, { it.title.lowercase(Locale.ROOT) }))

        if (sorted.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_no_widgets), Toast.LENGTH_SHORT).show()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val titleView = TextView(this).apply {
            text = getString(R.string.widget_picker_title)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F8FAFC"))
            setPadding(dp(4), 0, dp(4), dp(10))
        }
        container.addView(titleView)

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(Color.argb(235, 9, 13, 20))
                setStroke(dp(1), Color.argb(90, 148, 163, 184))
            }
        }

        var pickerDialog: AlertDialog? = null

        for (item in sorted) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isClickable = true
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundResource(R.drawable.bg_tv_focus_item)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(4)
                }
            }
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                    marginEnd = dp(12)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                if (item.icon != null) {
                    setImageDrawable(item.icon)
                } else {
                    setImageResource(android.R.drawable.ic_menu_add)
                }
            }
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val tvTitle = TextView(this).apply {
                text = item.title
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#F8FAFC"))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val tvSub = TextView(this).apply {
                text = item.appName
                textSize = 12f
                setTextColor(Color.parseColor("#94A3B8"))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            textCol.addView(tvTitle)
            textCol.addView(tvSub)
            row.addView(iv)
            row.addView(textCol)
            row.setOnClickListener {
                pickerDialog?.dismiss()
                startBindSelectedWidget(item.info)
            }
            listContainer.addView(row)
        }

        container.addView(listContainer)
        scroll.addView(container)

        setHomeFocusBlocked(true)
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(scroll)
            .setCancelable(true)
            .setOnDismissListener {
                if (drawerPanel.visibility != View.VISIBLE && settingsDialog?.isShowing != true) {
                    setHomeFocusBlocked(false)
                    focusDrawerButtonOnHome()
                }
            }
            .create()
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        pickerDialog = dialog
        dialog.show()
    }

    private fun startBindSelectedWidget(info: AppWidgetProviderInfo) {
        try {
            val appWidgetId = appWidgetHost.allocateAppWidgetId()
            pendingWidgetId = appWidgetId
            val bound = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.profile, info.provider, null)
            } else {
                appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)
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
                startActivityForResult(bindIntent, REQ_BIND_WIDGET)
            }
        } catch (_: Exception) {
            cleanupPendingWidget()
            Toast.makeText(this, getString(R.string.toast_widget_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun configureOrAddWidget(appWidgetId: Int) {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (info == null) {
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
                appWidgetHost.startAppWidgetConfigureActivityForResult(
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
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        val initialHeight = if (info != null && info.minHeight > 0) {
            ((info.minHeight / resources.displayMetrics.density).toInt() + 50).coerceIn(190, 440)
        } else {
            280
        }
        if (widgetEntries.none { it.id == appWidgetId }) {
            widgetEntries.add(WidgetEntry(appWidgetId, initialHeight))
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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }

        val headerTitle = TextView(this).apply {
            text = getString(R.string.settings_title)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F8FAFC"))
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(headerTitle)

        fun makeSectionLabel(text: String) = TextView(this).apply {
            this.text = text
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, dp(10), 0, dp(6))
        }

        root.addView(makeSectionLabel(getString(R.string.sec_icon_size)))
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
        root.addView(sizeRow)

        root.addView(makeSectionLabel(getString(R.string.show_home_clock)))
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
        root.addView(clockRow)
        root.addView(makeSectionLabel(getString(R.string.sec_dock_pos)))
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
        root.addView(dockRow)

        root.addView(makeSectionLabel(getString(R.string.sec_opacity)))
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
        root.addView(opRow)

        root.addView(makeSectionLabel(getString(R.string.sec_widgets_fmt, widgetEntries.size)))
        val btnAddWidget = makeActionButton(getString(R.string.btn_add_widget))
        root.addView(btnAddWidget)

        root.addView(makeSectionLabel(getString(R.string.sec_wallpaper)))
        val wallRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnPickWall = makeActionButton(getString(R.string.btn_pick_photo)).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
        }
        val btnResetWall = makeActionButton(getString(R.string.btn_default)).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        wallRow.addView(btnPickWall)
        wallRow.addView(btnResetWall)
        root.addView(wallRow)

        val btnClose = makeActionButton(getString(R.string.btn_close), isPrimary = true).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(18)
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
                applyClockVisibility(prefs.getBoolean("show_clock", true))
                applyDockSide(prefs.getString("dock_side", "left") ?: "left")
                applyPanelOpacity(prefs.getInt("panel_opacity_pct", 75))
                appsAdapter.notifyDataSetChanged()
                if (drawerPanel.visibility != View.VISIBLE) {
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
            startActivityForResult(intent, REQ_PICK_WALLPAPER)
        }
        btnResetWall.setOnClickListener {
            File(filesDir, "custom_wallpaper.img").delete()
            prefs.edit().putBoolean("has_custom_wallpaper", false).apply()
            wallpaperView.setImageBitmap(createMozzoWaveBitmap())
            Toast.makeText(this, getString(R.string.toast_wall_reset), Toast.LENGTH_SHORT).show()
        }
        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        btnClose.post {
            btnClose.requestFocus()
        }
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
            applySavedWallpaper()
            Toast.makeText(this, getString(R.string.toast_wall_updated), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.toast_wall_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun createMozzoWaveBitmap(): Bitmap {
        val w = 1080
        val h = 1920
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

    private fun applySavedWallpaper() {
        val destFile = File(filesDir, "custom_wallpaper.img")
        if (prefs.getBoolean("has_custom_wallpaper", false) && destFile.exists()) {
            wallpaperView.setImageURI(null)
            wallpaperView.setImageURI(Uri.fromFile(destFile))
        } else {
            wallpaperView.setImageBitmap(createMozzoWaveBitmap())
        }
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
            dockContainer.setPadding(dp(10), dp(8), dp(10), dp(10) + insetBottom)
            dockLp.width = ViewGroup.LayoutParams.WRAP_CONTENT
            dockLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            dockLp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            dockLp.setMargins(
                dp(16) + insetLeft,
                0,
                dp(16) + insetRight,
                0
            )
        } else {
            dockContainer.orientation = LinearLayout.VERTICAL
            dockContainer.setPadding(dp(8), dp(10), dp(10), dp(10))
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
        val dPad = dp(12)
        drawerLp.width = screenW - (dPad * 2) - insetLeft - insetRight
        drawerLp.height = ViewGroup.LayoutParams.MATCH_PARENT
        drawerLp.gravity = Gravity.CENTER
        drawerLp.setMargins(
            dPad + insetLeft,
            dp(8) + insetTop,
            dPad + insetRight,
            dp(8) + insetBottom
        )
        drawerPanel.setPadding(dp(14), dp(14), dp(14), dp(14))

        val searchBar = searchInput.parent as View
        (searchBar.layoutParams as LinearLayout.LayoutParams).apply {
            width = minOf(drawerLp.width - dp(28), dp(360))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val baseTop = if (prefs.getBoolean("show_clock", true)) dp(112) else dp(24)
        val topClockSpace = insetTop + baseTop

        if (isBottom) {
            val sPad = dp(20)
            homeLp.width = if (isWide) {
                (screenW * 0.54f).toInt()
            } else {
                screenW - sPad * 2 - insetLeft - insetRight
            }
            homeCenterColumn.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            homeLp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            val bottomDockReserve = dp(prefs.getInt("icon_size_dp", 48) + 34) + insetBottom
            homeLp.setMargins(
                sPad + insetLeft,
                topClockSpace,
                sPad + insetRight,
                bottomDockReserve
            )
        } else {
            val dockSpace = dp(prefs.getInt("icon_size_dp", 48) + 34)
            val outerEdge = dp(16)
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
        appsGridView.columnWidth = dp(prefs.getInt("icon_size_dp", 48) + 34)

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
            Toast.makeText(this, "Errore drawer: ${e.message}", Toast.LENGTH_LONG).show()
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
        val myPkg = packageName
        val loaded = mutableListOf<AppItem>()

        fun queryCategory(cat: String) {
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
                val act = ri.activityInfo.name
                val key = "$pkg/$act"
                if (!seenKeys.add(key)) continue
                val label = ri.loadLabel(packageManager)?.toString() ?: pkg
                val icon = ri.loadIcon(packageManager)
                loaded.add(AppItem(pkg, label, normalize(label), pkg, act, icon))
            }
        }

        queryCategory(Intent.CATEGORY_LAUNCHER)
        queryCategory("android.intent.category.LEANBACK_LAUNCHER")
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
                normalizedLabel = "mozzo settings launcher wallpaper dock icons impostazioni sfondo icone",
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

    private fun showAppOptionsDialog(app: AppItem) {
        if (app.isLauncherSettings) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
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

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(app.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> toggleDockFavorite(app.packageName)
                    1 -> {
                        if (app.shortcutIntentUri != null) {
                            removeWebShortcut(app.packageName)
                        } else {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${app.packageName}")
                            }
                            startActivity(intent)
                        }
                    }
                }
            }
            .show()
    }

    private fun getDockFavorites(): MutableList<String> {
        val raw = prefs.getString("dock_favorites", null)
        if (raw == null || (raw.isEmpty() && !prefs.getBoolean("dock_initialized", false))) {
            val defaults = mutableListOf<String>()
            val commonIntents = listOf(
                Intent(Intent.ACTION_DIAL),
                Intent.createChooser(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING), ""),
                Intent.createChooser(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER), ""),
                Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            )
            for (i in commonIntents) {
                val res = packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
                val pkg = res?.activityInfo?.packageName
                if (pkg != null && pkg != "android" && pkg != packageName && !defaults.contains(pkg)) {
                    defaults.add(pkg)
                }
            }
            if (defaults.size < 4 && allApps.isNotEmpty()) {
                for (app in allApps) {
                    if (app.isLauncherSettings) continue
                    if (!defaults.contains(app.packageName)) {
                        defaults.add(app.packageName)
                    }
                    if (defaults.size >= 4) break
                }
            }
            if (defaults.isNotEmpty()) {
                prefs.edit().putBoolean("dock_initialized", true).apply()
                saveDockFavorites(defaults)
            }
            return defaults
        }
        return if (raw.isEmpty()) mutableListOf() else raw.split(",").toMutableList()
    }

    private fun saveDockFavorites(list: List<String>) {
        prefs.edit()
            .putBoolean("dock_initialized", true)
            .putString("dock_favorites", list.joinToString(","))
            .apply()
    }

    private fun toggleDockFavorite(pkg: String) {
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
        val newIdx = idx + delta
        if (newIdx < 0 || newIdx >= list.size) return
        list.removeAt(idx)
        list.add(newIdx, entry)
        saveDockFavorites(list)
        renderDockFavorites()
    }

    private fun getDockCustomIconRes(entry: String): Int {
        val savedId = prefs.getInt("dock_icon_res_$entry", 0)
        return if (systemDockIcons.any { it.second == savedId }) savedId else 0
    }

    private fun showDockIconPicker(entry: String, title: String) {
        val grid = GridView(this).apply {
            numColumns = 4
            verticalSpacing = dp(10)
            horizontalSpacing = dp(10)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        var dialog: AlertDialog? = null
        grid.adapter = object : BaseAdapter() {
            override fun getCount(): Int = systemDockIcons.size
            override fun getItem(position: Int) = systemDockIcons[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val (label, resId) = systemDockIcons[position]
                val cell = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(6), dp(8), dp(6), dp(8))
                    setBackgroundResource(R.drawable.bg_tv_focus_item)
                }
                val iv = ImageView(this@MainActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageResource(if (resId == 0) android.R.drawable.ic_menu_revert else resId)
                    setColorFilter(if (resId == 0) Color.parseColor("#94A3B8") else Color.parseColor("#38BDF8"))
                }
                val tv = TextView(this@MainActivity).apply {
                    text = label
                    textSize = 11f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(Color.parseColor("#E2E8F0"))
                    setPadding(0, dp(4), 0, 0)
                }
                cell.addView(iv)
                cell.addView(tv)
                cell.setOnClickListener {
                    if (resId == 0) {
                        prefs.edit().remove("dock_icon_res_$entry").apply()
                    } else {
                        prefs.edit().putInt("dock_icon_res_$entry", resId).apply()
                    }
                    renderDockFavorites()
                    dialog?.dismiss()
                }
                return cell
            }
        }
        dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(getString(R.string.dock_pick_icon_title, title))
            .setView(grid)
            .setOnDismissListener {
                focusDrawerButtonOnHome()
            }
            .create()
        dialog.show()
    }

    private fun showDockItemMenu(entry: String, title: String, app: AppItem?) {
        val list = getDockFavorites()
        val idx = list.indexOf(entry)
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        if (idx > 0) {
            options.add(getString(R.string.dock_move_before))
            actions.add { moveDockFavorite(entry, -1) }
        }
        if (idx != -1 && idx < list.size - 1) {
            options.add(getString(R.string.dock_move_after))
            actions.add { moveDockFavorite(entry, 1) }
        }
        options.add(getString(R.string.dock_change_icon))
        actions.add { showDockIconPicker(entry, title) }

        options.add(getString(R.string.dock_remove))
        actions.add { toggleDockFavorite(entry) }

        if (app != null && !app.isLauncherSettings && app.shortcutIntentUri == null) {
            options.add(getString(R.string.app_info))
            actions.add {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${app.packageName}")
                }
                startActivity(intent)
            }
        }

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setItems(options.toTypedArray()) { _, which ->
                actions[which].invoke()
            }
            .show()
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
            drawerLp.setMargins(dp(6), 0, 0, 0)
        } else {
            drawerLp.setMargins(0, dp(6), 0, 0)
        }
        btnDrawerMozzo.layoutParams = drawerLp

        fun makeItemLp() = LinearLayout.LayoutParams(iconSizePx, iconSizePx).apply {
            if (isBottom) {
                leftMargin = dp(5)
                rightMargin = dp(5)
            } else {
                topMargin = dp(6)
                bottomMargin = dp(6)
            }
        }

        for (entry in favPkgs) {
            val customRes = getDockCustomIconRes(entry)

            if (entry.startsWith("setting:")) {
                val parts = entry.removePrefix("setting:").split("|", limit = 2)
                val title = parts.getOrElse(0) { getString(R.string.set_system) }
                val action = parts.getOrElse(1) { Settings.ACTION_SETTINGS }
                val btn = ImageButton(this).apply {
                    layoutParams = makeItemLp()
                    setPadding(dp(6), dp(6), dp(6), dp(6))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageResource(if (customRes != 0) customRes else android.R.drawable.ic_menu_manage)
                    setColorFilter(Color.parseColor("#38BDF8"))
                    isFocusable = true
                    setBackgroundResource(R.drawable.bg_tv_focus_item)
                    contentDescription = title
                    setOnClickListener { openSettingAction(action) }
                    setOnLongClickListener {
                        triggerHaptic()
                        showDockItemMenu(entry, title, null)
                        true
                    }
                }
                dockFavoritesContainer.addView(btn)
                continue
            }

            val app = appMap[entry] ?: continue
            val btn = ImageButton(this).apply {
                layoutParams = makeItemLp()
                setPadding(dp(4), dp(4), dp(4), dp(4))
                scaleType = ImageView.ScaleType.FIT_CENTER
                if (customRes != 0) {
                    setImageResource(customRes)
                    setColorFilter(Color.parseColor("#38BDF8"))
                } else {
                    clearColorFilter()
                    setImageDrawable(app.icon)
                }
                isFocusable = true
                setBackgroundResource(R.drawable.bg_tv_focus_item)
                contentDescription = app.label
                setOnClickListener { launchApp(app) }
                setOnLongClickListener {
                    triggerHaptic()
                    showDockItemMenu(entry, app.label, app)
                    true
                }
            }
            dockFavoritesContainer.addView(btn)
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
