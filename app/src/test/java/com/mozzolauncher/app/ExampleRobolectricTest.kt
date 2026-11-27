package com.mozzolauncher.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Mozzo Launcher", appName)
  }

  @Test
  fun `drawer button gets focus on start and cannot be removed`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val btnDrawer = activity.findViewById<ImageButton>(R.id.btnDrawerMozzo)
    assertEquals(View.VISIBLE, btnDrawer.visibility)
    assertTrue(btnDrawer.isFocusable)
    assertTrue(btnDrawer.hasFocus())

    assertTrue(btnDrawer.performLongClick())
    assertNull(ShadowAlertDialog.getLatestAlertDialog())
    assertEquals(View.VISIBLE, btnDrawer.visibility)
  }

  @Test
  fun `clock click opens date settings when no clock app exists`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val clockContainer = activity.findViewById<LinearLayout>(R.id.clockContainer)
    clockContainer.performClick()

    val nextIntent = Shadows.shadowOf(activity).nextStartedActivity
    assertNotNull(nextIntent)
    assertEquals(Settings.ACTION_DATE_SETTINGS, nextIntent.action)
    assertNull(ShadowToast.getLatestToast())
  }

  @Test
  fun `clock follows 12h and 24h system setting`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    val timeClock = activity.findViewById<TextClock>(R.id.clockTimeText)
    assertTrue(timeClock.is24HourModeEnabled)

    Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "12")
    controller.pause().resume()
    assertFalse(timeClock.is24HourModeEnabled)
    assertTrue(timeClock.format12Hour.toString().contains("a"))
  }

  @Test
  fun `bottom dock reaches screen edge and pads inner content`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("mozzo_launcher_prefs", Context.MODE_PRIVATE)
      .edit()
      .putString("dock_side", "bottom")
      .commit()

    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    val root = activity.findViewById<FrameLayout>(R.id.rootContainer)
    val dock = activity.findViewById<LinearLayout>(R.id.dockContainer)

    val insets = WindowInsetsCompat.Builder()
      .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 48, 0, 96))
      .build()
    ViewCompat.dispatchApplyWindowInsets(root, insets)
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val lp = dock.layoutParams as FrameLayout.LayoutParams
    assertEquals(0, lp.bottomMargin)
    assertTrue(dock.paddingBottom >= 96)
  }

  @Test
  fun `settings dialog opens once and closes on first close tap preserving changes`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    activity.onKeyDown(android.view.KeyEvent.KEYCODE_MENU, null)
    activity.onKeyDown(android.view.KeyEvent.KEYCODE_MENU, null)
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    assertEquals(1, ShadowAlertDialog.getShownDialogs().size)
    val dialog = ShadowAlertDialog.getLatestAlertDialog()
    assertTrue(dialog.isShowing)

    fun findTextView(view: View, text: String): TextView? {
      if (view is TextView && view.text?.toString() == text) return view
      if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
          findTextView(view.getChildAt(i), text)?.let { return it }
        }
      }
      return null
    }

    val decor = dialog.window!!.decorView
    val closeBtn = findTextView(decor, activity.getString(R.string.btn_close))!!
    assertTrue(closeBtn.hasFocus())
    findTextView(decor, "64")!!.performClick()
    closeBtn.performClick()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    assertFalse(dialog.isShowing)
    val prefs = activity.getSharedPreferences("mozzo_launcher_prefs", Context.MODE_PRIVATE)
    assertEquals(64, prefs.getInt("icon_size_dp", 48))
    assertTrue(activity.findViewById<ImageButton>(R.id.btnDrawerMozzo).hasFocus())
  }

  @Test
  fun `add widget button opens picker or shows message without crashing`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    activity.onKeyDown(android.view.KeyEvent.KEYCODE_MENU, null)
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val dialog = ShadowAlertDialog.getLatestAlertDialog()
    fun findTextView(view: View, text: String): TextView? {
      if (view is TextView && view.text?.toString() == text) return view
      if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
          findTextView(view.getChildAt(i), text)?.let { return it }
        }
      }
      return null
    }

    findTextView(dialog.window!!.decorView, activity.getString(R.string.btn_add_widget))!!.performClick()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    assertFalse(dialog.isShowing)
  }

  @Test
  fun `drawer grid has uniform item lines, no overscroll, and recycles views cleanly`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val drawerPanel = activity.findViewById<LinearLayout>(R.id.drawerPanel)
    val grid = activity.findViewById<android.widget.GridView>(R.id.appsGridView)
    assertTrue(drawerPanel.isClickable)
    assertEquals(View.OVER_SCROLL_NEVER, grid.overScrollMode)

    val adapter = grid.adapter
    assertTrue(adapter.count >= 1)
    val firstView = adapter.getView(0, null, grid)
    val label = firstView.findViewById<TextView>(R.id.appLabel)
    assertEquals(1, label.minLines)
    assertEquals(1, label.maxLines)

    val recycled = adapter.getView(0, firstView, grid)
    assertTrue(recycled === firstView)
  }

  @Test
  fun `closing settings while drawer is open keeps clock hidden`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val btnDrawer = activity.findViewById<ImageButton>(R.id.btnDrawerMozzo)
    val drawerPanel = activity.findViewById<LinearLayout>(R.id.drawerPanel)
    val clock = activity.findViewById<LinearLayout>(R.id.clockContainer)

    btnDrawer.performClick()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    assertEquals(View.VISIBLE, drawerPanel.visibility)
    assertEquals(View.INVISIBLE, clock.visibility)

    activity.onKeyDown(android.view.KeyEvent.KEYCODE_MENU, null)
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    val dialog = ShadowAlertDialog.getLatestAlertDialog()
    fun findTextView(view: View, text: String): TextView? {
      if (view is TextView && view.text?.toString() == text) return view
      if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
          findTextView(view.getChildAt(i), text)?.let { return it }
        }
      }
      return null
    }
    findTextView(dialog.window!!.decorView, activity.getString(R.string.btn_close))!!.performClick()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    assertEquals(View.VISIBLE, drawerPanel.visibility)
    assertEquals(View.INVISIBLE, clock.visibility)
  }

  @Test
  fun `home newIntent dismisses secondary dialog and orientation change updates drawer width`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val btnDrawer = activity.findViewById<ImageButton>(R.id.btnDrawerMozzo)
    btnDrawer.performClick()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val grid = activity.findViewById<android.widget.GridView>(R.id.appsGridView)
    val itemView = grid.adapter.getView(0, null, grid)
    grid.onItemLongClickListener!!.onItemLongClick(grid, itemView, 0, 0)
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val secondary = ShadowAlertDialog.getLatestAlertDialog()
    if (secondary != null) {
      controller.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      assertFalse(secondary.isShowing)
    }

    val drawer = activity.findViewById<LinearLayout>(R.id.drawerPanel)
    val beforeW = (drawer.layoutParams as FrameLayout.LayoutParams).width
    activity.resources.displayMetrics.widthPixels = 1920
    activity.resources.displayMetrics.heightPixels = 1080
    val cfg = android.content.res.Configuration(activity.resources.configuration).apply {
      orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
    }
    activity.onConfigurationChanged(cfg)
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val afterW = (drawer.layoutParams as FrameLayout.LayoutParams).width
    assertTrue(afterW > beforeW)
  }

  @Test
  fun `all supported locales resolve localized strings and format arguments`() {
    val baseContext = ApplicationProvider.getApplicationContext<Context>()
    val locales = listOf(
      java.util.Locale.ENGLISH,
      java.util.Locale.ITALIAN,
      java.util.Locale.forLanguageTag("es"),
      java.util.Locale.FRENCH,
      java.util.Locale.GERMAN,
      java.util.Locale.forLanguageTag("pt"),
      java.util.Locale.forLanguageTag("nl"),
      java.util.Locale.SIMPLIFIED_CHINESE
    )
    for (locale in locales) {
      val cfg = android.content.res.Configuration(baseContext.resources.configuration).apply {
        setLocale(locale)
      }
      val localized = baseContext.createConfigurationContext(cfg)
      assertTrue(localized.getString(R.string.settings_title).isNotBlank())
      assertTrue(localized.getString(R.string.kw_wifi).isNotBlank())
      assertTrue(localized.getString(R.string.kw_mozzo_settings).isNotBlank())
      assertTrue(localized.getString(R.string.sec_widgets_fmt, 2).contains("2"))
      assertTrue(localized.getString(R.string.widget_move).isNotBlank())
      assertTrue(localized.getString(R.string.widget_resize).isNotBlank())
      assertTrue(localized.getString(R.string.dock_move).isNotBlank())
      assertTrue(localized.getString(R.string.sec_set_default).isNotBlank())
      assertTrue(localized.getString(R.string.sec_backup).isNotBlank())
      assertTrue(localized.getString(R.string.btn_default_launcher).isNotBlank())
      assertTrue(localized.getString(R.string.btn_export).isNotBlank())
      assertTrue(localized.getString(R.string.btn_import).isNotBlank())
      assertTrue(localized.getString(R.string.btn_done).isNotBlank())
      assertTrue(localized.getString(R.string.banner_dock_move).isNotBlank())
      assertTrue(localized.getString(R.string.banner_widget_move).isNotBlank())
      assertTrue(localized.getString(R.string.banner_widget_resize_fmt, 3, 2).contains("3"))
      assertTrue(localized.getString(R.string.widget_cell_fmt, 2, 2).contains("2"))
      assertTrue(localized.getString(R.string.toast_shortcut_added, "Site").contains("Site"))
      assertTrue(localized.getString(R.string.toast_drawer_error_fmt, "Err").contains("Err"))
    }
  }

  @Test
  fun `settings dialog uses 1 column on narrow phone and 2 columns with capped width on TV`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    // Narrow phone portrait (~360dp wide)
    activity.resources.displayMetrics.density = 2f
    activity.resources.displayMetrics.widthPixels = 720
    activity.resources.displayMetrics.heightPixels = 1600

    activity.onKeyDown(android.view.KeyEvent.KEYCODE_MENU, null)
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    val phoneDialog = ShadowAlertDialog.getLatestAlertDialog()
    assertTrue(phoneDialog.isShowing)
    assertNull(phoneDialog.window!!.decorView.findViewWithTag<View>("settings_columns_row"))
    val phoneDialogW = phoneDialog.window!!.attributes.width
    assertTrue(phoneDialogW in 600..700)
    phoneDialog.dismiss()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    // Android TV 1080p (~960dp wide x 540dp tall at xhdpi)
    activity.resources.displayMetrics.widthPixels = 1920
    activity.resources.displayMetrics.heightPixels = 1080
    val cfg = android.content.res.Configuration(activity.resources.configuration).apply {
      orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
    }
    activity.onConfigurationChanged(cfg)
    activity.onKeyDown(android.view.KeyEvent.KEYCODE_MENU, null)
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val tvDialog = ShadowAlertDialog.getLatestAlertDialog()
    assertTrue(tvDialog.isShowing)
    assertNotNull(tvDialog.window!!.decorView.findViewWithTag<View>("settings_columns_row"))
    val tvHeaderRow = tvDialog.window!!.decorView.findViewWithTag<ViewGroup>("settings_header_row")
    assertNotNull(tvHeaderRow)
    val tvDialogW = tvDialog.window!!.attributes.width
    // Capped <= 700dp (1400px at 2f) and noticeably narrower than 1920px TV screen
    assertTrue(tvDialogW in 1080..1400)
    tvDialog.dismiss()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
  }

  @Test
  fun `settings dialog places Close in header with focus and fits short landscape phone`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    fun findCloseButton(view: View): TextView? {
      val label = activity.getString(R.string.btn_close)
      if (view is TextView && view.text?.toString() == label) return view
      if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
          findCloseButton(view.getChildAt(i))?.let { return it }
        }
      }
      return null
    }

    // 1) Narrow phone portrait still has Close inside header row
    activity.resources.displayMetrics.density = 2f
    activity.resources.displayMetrics.widthPixels = 720
    activity.resources.displayMetrics.heightPixels = 1600
    activity.onKeyDown(android.view.KeyEvent.KEYCODE_MENU, null)
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val portraitDlg = ShadowAlertDialog.getLatestAlertDialog()
    val portraitHeader = portraitDlg.window!!.decorView.findViewWithTag<ViewGroup>("settings_header_row")
    assertNotNull(portraitHeader)
    val portraitClose = findCloseButton(portraitHeader)
    assertNotNull(portraitClose)
    assertTrue(portraitClose!!.hasFocus())
    portraitDlg.dismiss()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    // 2) Short landscape phone (800dp x 360dp) fits Close cleanly without clipping
    activity.resources.displayMetrics.widthPixels = 1600
    activity.resources.displayMetrics.heightPixels = 720
    val cfg = android.content.res.Configuration(activity.resources.configuration).apply {
      orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
    }
    activity.onConfigurationChanged(cfg)
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    activity.onKeyDown(android.view.KeyEvent.KEYCODE_MENU, null)
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val landscapeDlg = ShadowAlertDialog.getLatestAlertDialog()
    val decor = landscapeDlg.window!!.decorView
    val landHeader = decor.findViewWithTag<ViewGroup>("settings_header_row")
    assertNotNull(landHeader)
    val landClose = findCloseButton(landHeader)
    assertNotNull(landClose)
    assertTrue(landClose!!.hasFocus())

    val dialogW = landscapeDlg.window!!.attributes.width
    val scroll = landHeader.parent.parent as android.widget.ScrollView
    scroll.measure(
      View.MeasureSpec.makeMeasureSpec(dialogW, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.AT_MOST)
    )
    assertTrue("Expected root height <= 720px on 360dp landscape, got ${scroll.getChildAt(0).measuredHeight}", scroll.getChildAt(0).measuredHeight <= 720)
  }

  private fun addLauncherActivity(
    spm: org.robolectric.shadows.ShadowPackageManager,
    pkg: String,
    cls: String,
    label: String,
    category: String? = Intent.CATEGORY_LAUNCHER
  ) {
    val cn = android.content.ComponentName(pkg, cls)
    val ai = spm.addActivityIfNotPresent(cn)
    ai.nonLocalizedLabel = label
    ai.exported = true
    ai.applicationInfo.nonLocalizedLabel = label
    spm.addOrUpdateActivity(ai)
    if (category != null) {
      spm.addIntentFilterForActivity(
        cn,
        android.content.IntentFilter(Intent.ACTION_MAIN).apply {
          addCategory(category)
        }
      )
    }
  }

  @Test
  fun `first install populates 4 dock icons on phone despite partial intent matches`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val spm = Shadows.shadowOf(context.packageManager)

    // Install 5 launchable apps, only 2 matching common phone intents (dialer + camera)
    addLauncherActivity(spm, "com.test.dialer", "com.test.dialer.Main", "Phone")
    addLauncherActivity(spm, "com.test.camera", "com.test.camera.Main", "Camera")
    addLauncherActivity(spm, "com.test.files", "com.test.files.Main", "Files")
    addLauncherActivity(spm, "com.test.music", "com.test.music.Main", "Music")
    addLauncherActivity(spm, "com.test.notes", "com.test.notes.Main", "Notes")

    spm.addIntentFilterForActivity(
      android.content.ComponentName("com.test.dialer", "com.test.dialer.Main"),
      android.content.IntentFilter(Intent.ACTION_DIAL).apply { addCategory(Intent.CATEGORY_DEFAULT) }
    )
    spm.addIntentFilterForActivity(
      android.content.ComponentName("com.test.camera", "com.test.camera.Main"),
      android.content.IntentFilter(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
      }
    )

    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val dockFavorites = activity.findViewById<LinearLayout>(R.id.dockFavoritesContainer)
    assertEquals(4, dockFavorites.childCount)
  }

  @Test
  fun `first install populates 4 visible dock icons on Android TV and ignores non-launchable stubs`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val spm = Shadows.shadowOf(context.packageManager)
    spm.setSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK, true)

    // Install a hidden non-launcher intent handler stub that could previously hijack dock_favorites
    val stubCn = android.content.ComponentName("com.tv.hiddenstub", "com.tv.hiddenstub.Stub")
    addLauncherActivity(spm, "com.tv.hiddenstub", "com.tv.hiddenstub.Stub", "Stub", category = null)
    spm.addIntentFilterForActivity(
      stubCn,
      android.content.IntentFilter(Intent.ACTION_DIAL).apply { addCategory(Intent.CATEGORY_DEFAULT) }
    )

    // Install 5 real Leanback TV apps
    addLauncherActivity(spm, "com.tv.youtube", "com.tv.youtube.Main", "YouTube", "android.intent.category.LEANBACK_LAUNCHER")
    addLauncherActivity(spm, "com.tv.netflix", "com.tv.netflix.Main", "Netflix", "android.intent.category.LEANBACK_LAUNCHER")
    addLauncherActivity(spm, "com.tv.vlc", "com.tv.vlc.Main", "VLC", "android.intent.category.LEANBACK_LAUNCHER")
    addLauncherActivity(spm, "com.tv.store", "com.tv.store.Main", "Play Store", "android.intent.category.LEANBACK_LAUNCHER")
    addLauncherActivity(spm, "com.tv.plex", "com.tv.plex.Main", "Plex", "android.intent.category.LEANBACK_LAUNCHER")

    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val dockFavorites = activity.findViewById<LinearLayout>(R.id.dockFavoritesContainer)
    assertEquals(4, dockFavorites.childCount)
  }

  @Test
  fun `legacy 2-item dock tops up to 4 on upgrade and preserves manual edits afterwards`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val spm = Shadows.shadowOf(context.packageManager)

    addLauncherActivity(spm, "com.test.dialer", "com.test.dialer.Main", "Phone")
    addLauncherActivity(spm, "com.test.camera", "com.test.camera.Main", "Camera")
    addLauncherActivity(spm, "com.test.browser", "com.test.browser.Main", "Browser")
    addLauncherActivity(spm, "com.test.messages", "com.test.messages.Main", "Messages")
    addLauncherActivity(spm, "com.test.files", "com.test.files.Main", "Files")

    // Simulate legacy install that locked only dialer+camera without dock_defaults_v2
    val prefs = context.getSharedPreferences("mozzo_launcher_prefs", Context.MODE_PRIVATE)
    prefs.edit()
      .putBoolean("dock_initialized", true)
      .putString("dock_favorites", "com.test.dialer,com.test.camera")
      .commit()

    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val dockFavorites = activity.findViewById<LinearLayout>(R.id.dockFavoritesContainer)
    assertEquals(4, dockFavorites.childCount)
    assertTrue(prefs.getBoolean("dock_defaults_v2", false))

    // If the user explicitly leaves only 1 item after v2 migration, it is preserved
    prefs.edit().putString("dock_favorites", "com.test.dialer").commit()
    val controller2 = Robolectric.buildActivity(MainActivity::class.java).setup()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    assertEquals(1, controller2.get().findViewById<LinearLayout>(R.id.dockFavoritesContainer).childCount)
  }

  @Test
  fun `SettingsBackupHelper exports imports clamps values and rejects invalid payloads`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sourcePrefs = context.getSharedPreferences("backup_src", Context.MODE_PRIVATE)
    sourcePrefs.edit()
      .putInt("icon_size_dp", 72)
      .putString("dock_side", "bottom")
      .putBoolean("show_clock", false)
      .putInt("panel_opacity_pct", 50)
      .putString("dock_favorites", "com.a,com.b,com.c")
      .putString("web_shortcuts_json", "[{\"id\":\"s1\",\"name\":\"Docs\",\"uri\":\"https://example.com\"}]")
      .commit()

    val exported = SettingsBackupHelper.exportToJson(sourcePrefs)

    val destPrefs = context.getSharedPreferences("backup_dst", Context.MODE_PRIVATE)
    assertTrue(SettingsBackupHelper.importFromJson(exported, destPrefs))
    assertEquals(72, destPrefs.getInt("icon_size_dp", 0))
    assertEquals("bottom", destPrefs.getString("dock_side", ""))
    assertFalse(destPrefs.getBoolean("show_clock", true))
    assertEquals(50, destPrefs.getInt("panel_opacity_pct", 0))
    assertEquals("com.a,com.b,com.c", destPrefs.getString("dock_favorites", ""))
    assertTrue(destPrefs.getBoolean("dock_initialized", false))
    assertTrue(destPrefs.getBoolean("dock_defaults_v2", false))
    assertTrue(destPrefs.getString("web_shortcuts_json", "")!!.contains("Docs"))

    // Out-of-range numeric values get clamped; invalid dock_side is ignored
    val clampedPayload = """
      {
        "app": "mozzo_launcher_backup",
        "icon_size_dp": 200,
        "panel_opacity_pct": -10,
        "dock_side": "top"
      }
    """.trimIndent()
    assertTrue(SettingsBackupHelper.importFromJson(clampedPayload, destPrefs))
    assertEquals(80, destPrefs.getInt("icon_size_dp", 0))
    assertEquals(0, destPrefs.getInt("panel_opacity_pct", -1))
    assertEquals("bottom", destPrefs.getString("dock_side", ""))

    // Invalid JSON or missing app marker fails cleanly
    assertFalse(SettingsBackupHelper.importFromJson("{not-json", destPrefs))
    assertFalse(SettingsBackupHelper.importFromJson("{\"app\":\"other\"}", destPrefs))
  }

  @Test
  fun `settings system and backup buttons trigger home settings export and import`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    fun findTextView(view: View, text: String): TextView? {
      if (view is TextView && view.text?.toString() == text) return view
      if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
          findTextView(view.getChildAt(i), text)?.let { return it }
        }
      }
      return null
    }

    activity.onKeyDown(android.view.KeyEvent.KEYCODE_MENU, null)
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    var dialog = ShadowAlertDialog.getLatestAlertDialog()
    findTextView(dialog.window!!.decorView, activity.getString(R.string.btn_default_launcher))!!.performClick()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    val defaultLauncherIntent = Shadows.shadowOf(activity).nextStartedActivity
    assertNotNull(defaultLauncherIntent)
    assertEquals(Settings.ACTION_HOME_SETTINGS, defaultLauncherIntent.action)

    activity.onKeyDown(android.view.KeyEvent.KEYCODE_MENU, null)
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    dialog = ShadowAlertDialog.getLatestAlertDialog()
    findTextView(dialog.window!!.decorView, activity.getString(R.string.btn_export))!!.performClick()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    val exportIntent = Shadows.shadowOf(activity).nextStartedActivityForResult
    assertNotNull(exportIntent)
    assertEquals(Intent.ACTION_CREATE_DOCUMENT, exportIntent.intent.action)

    activity.onKeyDown(android.view.KeyEvent.KEYCODE_MENU, null)
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    dialog = ShadowAlertDialog.getLatestAlertDialog()
    findTextView(dialog.window!!.decorView, activity.getString(R.string.btn_import))!!.performClick()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    val importIntent = Shadows.shadowOf(activity).nextStartedActivityForResult
    assertNotNull(importIntent)
    assertEquals(Intent.ACTION_OPEN_DOCUMENT, importIntent.intent.action)
  }

  @Test
  fun `dock continuous move mode supports dpad arrows, ok commit, back cancel, and tap target`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val spm = Shadows.shadowOf(context.packageManager)
    addLauncherActivity(spm, "com.test.one", "com.test.one.Main", "Alpha")
    addLauncherActivity(spm, "com.test.two", "com.test.two.Main", "Beta")
    addLauncherActivity(spm, "com.test.three", "com.test.three.Main", "Gamma")
    addLauncherActivity(spm, "com.test.four", "com.test.four.Main", "Delta")

    val prefs = context.getSharedPreferences("mozzo_launcher_prefs", Context.MODE_PRIVATE)
    prefs.edit()
      .putBoolean("dock_initialized", true)
      .putBoolean("dock_defaults_v2", true)
      .putString("dock_favorites", "com.test.one,com.test.two,com.test.three,com.test.four")
      .commit()

    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val dockFavorites = activity.findViewById<LinearLayout>(R.id.dockFavoritesContainer)
    val banner = activity.findViewById<LinearLayout>(R.id.editModeBanner)
    assertEquals(4, dockFavorites.childCount)
    assertEquals(View.GONE, banner.visibility)

    // Long-click first item and verify menu contains "Move" (no Change Icon)
    dockFavorites.getChildAt(0).performLongClick()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    val menuDialog = ShadowAlertDialog.getLatestAlertDialog()
    assertNotNull(menuDialog)
    val listView = menuDialog.listView
    assertEquals(3, listView.adapter.count)
    assertEquals(activity.getString(R.string.dock_move), listView.adapter.getItem(0).toString())

    // Enter continuous move mode
    Shadows.shadowOf(menuDialog).clickOnItem(0)
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    assertEquals(View.VISIBLE, banner.visibility)

    // Move down twice with D-Pad then cancel with Back -> reverts order
    activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_DOWN))
    activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_DOWN))
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    assertEquals("com.test.two,com.test.three,com.test.one,com.test.four", prefs.getString("dock_favorites", ""))

    activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK))
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    assertEquals(View.GONE, banner.visibility)
    assertEquals("com.test.one,com.test.two,com.test.three,com.test.four", prefs.getString("dock_favorites", ""))

    // Re-enter Move on first item, move down once, commit with D-Pad Center
    dockFavorites.getChildAt(0).performLongClick()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(0)
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_DOWN))
    activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_CENTER))
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    assertEquals(View.GONE, banner.visibility)
    assertEquals("com.test.two,com.test.one,com.test.three,com.test.four", prefs.getString("dock_favorites", ""))

    // Re-enter Move on index 0 ("com.test.two"), tap index 3 ("com.test.four") directly
    dockFavorites.getChildAt(0).performLongClick()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(0)
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    dockFavorites.getChildAt(3).performClick()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    assertEquals(View.GONE, banner.visibility)
    assertEquals("com.test.one,com.test.three,com.test.four,com.test.two", prefs.getString("dock_favorites", ""))
  }

  @Test
  fun `phone uses system wallpaper window mode while TV falls back to wallpaperView`() {
    val phoneController = Robolectric.buildActivity(MainActivity::class.java).setup()
    val phoneActivity = phoneController.get()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val phoneFlags = phoneActivity.window.attributes.flags
    assertTrue((phoneFlags and android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER) != 0)
    assertEquals(View.GONE, phoneActivity.findViewById<View>(R.id.wallpaperView).visibility)

    val context = ApplicationProvider.getApplicationContext<Context>()
    Shadows.shadowOf(context.packageManager).setSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK, true)
    val tvController = Robolectric.buildActivity(MainActivity::class.java).setup()
    val tvActivity = tvController.get()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val tvFlags = tvActivity.window.attributes.flags
    assertEquals(0, tvFlags and android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
    assertEquals(View.VISIBLE, tvActivity.findViewById<View>(R.id.wallpaperView).visibility)
  }

  @Test
  fun `stress test rapid drawer search edit cancellation orientation and newIntent stability`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val spm = Shadows.shadowOf(context.packageManager)
    for (i in 1..12) {
      addLauncherActivity(spm, "com.stress.app$i", "com.stress.app$i.Main", "Stress App $i")
    }

    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val btnDrawer = activity.findViewById<ImageButton>(R.id.btnDrawerMozzo)
    val drawerPanel = activity.findViewById<LinearLayout>(R.id.drawerPanel)
    val searchInput = activity.findViewById<android.widget.EditText>(R.id.searchInput)
    val grid = activity.findViewById<android.widget.GridView>(R.id.appsGridView)
    val widgetsGrid = activity.findViewById<android.widget.GridLayout>(R.id.widgetsContainer)
    assertNotNull(widgetsGrid)

    // Rapid open -> type -> clear -> close cycles
    val queries = listOf("wifi", "Stress App 1", "zzznomatch", "")
    repeat(8) { cycle ->
      btnDrawer.performClick()
      searchInput.setText(queries[cycle % queries.size])
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      assertEquals(View.VISIBLE, drawerPanel.visibility)
      activity.onBackPressed()
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      assertEquals(View.GONE, drawerPanel.visibility)
    }

    // Enter dock move mode, rotate screen, then hit HOME newIntent cleanly
    val dockFavorites = activity.findViewById<LinearLayout>(R.id.dockFavoritesContainer)
    dockFavorites.getChildAt(0).performLongClick()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(0)
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val cfg = android.content.res.Configuration(activity.resources.configuration).apply {
      orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
    }
    activity.resources.displayMetrics.widthPixels = 1920
    activity.resources.displayMetrics.heightPixels = 1080
    activity.onConfigurationChanged(cfg)
    controller.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    assertEquals(View.GONE, activity.findViewById<View>(R.id.editModeBanner).visibility)
    assertEquals(View.GONE, drawerPanel.visibility)
    assertTrue(grid.adapter.count >= 12)
    assertTrue(btnDrawer.hasFocus())
  }

  @Test
  fun `widget grid switches between 4 portrait columns and 6 landscape columns`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val widgetsGrid = activity.findViewById<android.widget.GridLayout>(R.id.widgetsContainer)
    assertEquals(4, widgetsGrid.columnCount)

    activity.resources.displayMetrics.widthPixels = 1920
    activity.resources.displayMetrics.heightPixels = 1080
    val cfg = android.content.res.Configuration(activity.resources.configuration).apply {
      orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
    }
    activity.onConfigurationChanged(cfg)
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    assertEquals(6, widgetsGrid.columnCount)
  }

  @Test
  fun `right dock pads system bar inset like bottom dock and landscape widget column uses full available width`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("mozzo_launcher_prefs", Context.MODE_PRIVATE)
      .edit()
      .putString("dock_side", "right")
      .commit()

    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    activity.resources.displayMetrics.density = 2f
    activity.resources.displayMetrics.widthPixels = 1600
    activity.resources.displayMetrics.heightPixels = 720
    val cfg = android.content.res.Configuration(activity.resources.configuration).apply {
      orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
    }
    activity.onConfigurationChanged(cfg)
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val root = activity.findViewById<FrameLayout>(R.id.rootContainer)
    val dock = activity.findViewById<LinearLayout>(R.id.dockContainer)
    val homeCol = activity.findViewById<LinearLayout>(R.id.homeCenterColumn)

    val insets = WindowInsetsCompat.Builder()
      .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 48, 96, 0))
      .build()
    ViewCompat.dispatchApplyWindowInsets(root, insets)
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val dockLp = dock.layoutParams as FrameLayout.LayoutParams
    assertEquals(0, dockLp.rightMargin)
    assertTrue(dock.paddingRight >= 96)

    // Full width minus right dock + insets + margins (~1600 - 132 - 96 - 24 = 1348), not squeezed to 65%
    val homeLp = homeCol.layoutParams as FrameLayout.LayoutParams
    assertTrue(homeLp.width >= 1280)
  }

  @Test
  fun `edit mode banner stacks label above action row so Done button is never clipped`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val spm = Shadows.shadowOf(context.packageManager)
    addLauncherActivity(spm, "com.b.one", "com.b.one.Main", "One")
    addLauncherActivity(spm, "com.b.two", "com.b.two.Main", "Two")
    val prefs = context.getSharedPreferences("mozzo_launcher_prefs", Context.MODE_PRIVATE)
    prefs.edit()
      .putBoolean("dock_initialized", true)
      .putBoolean("dock_defaults_v2", true)
      .putString("dock_favorites", "com.b.one,com.b.two")
      .commit()

    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val dockFavorites = activity.findViewById<LinearLayout>(R.id.dockFavoritesContainer)
    dockFavorites.getChildAt(0).performLongClick()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(0)
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val banner = activity.findViewById<LinearLayout>(R.id.editModeBanner)
    assertEquals(View.VISIBLE, banner.visibility)
    assertEquals(LinearLayout.VERTICAL, banner.orientation)
    assertEquals(2, banner.childCount)

    val doneBtn = banner.findViewWithTag<View>("edit_mode_done_btn")
    assertNotNull(doneBtn)
    assertTrue(doneBtn.isFocusable)
    doneBtn.performClick()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    assertEquals(View.GONE, banner.visibility)
  }

  @Test
  fun `widget resize 4 buttons receive focus support OK and directional navigation cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val awm = android.appwidget.AppWidgetManager.getInstance(context)
    val sawm = Shadows.shadowOf(awm)
    val widgetId = 77
    val info = android.appwidget.AppWidgetProviderInfo().apply {
      provider = android.content.ComponentName("com.test.w", "com.test.w.ClockWidget")
      minWidth = 140
      minHeight = 140
      label = "Clock"
    }
    sawm.addBoundWidget(widgetId, info)

    val prefs = context.getSharedPreferences("mozzo_launcher_prefs", Context.MODE_PRIVATE)
    prefs.edit()
      .putString("widgets_list_json", "[{\"id\":$widgetId,\"spanX\":2,\"spanY\":2}]")
      .commit()

    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val widgetsGrid = activity.findViewById<android.widget.GridLayout>(R.id.widgetsContainer)
    assertEquals(1, widgetsGrid.childCount)

    // Open widget context menu -> select "Resize" (index 1)
    widgetsGrid.getChildAt(0).performLongClick()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(1)
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val banner = activity.findViewById<LinearLayout>(R.id.editModeBanner)
    assertEquals(View.VISIBLE, banner.visibility)

    val btnWMinus = banner.findViewWithTag<TextView>("edit_btn_w_minus")
    val btnWPlus = banner.findViewWithTag<TextView>("edit_btn_w_plus")
    val btnHMinus = banner.findViewWithTag<TextView>("edit_btn_h_minus")
    val btnHPlus = banner.findViewWithTag<TextView>("edit_btn_h_plus")
    val btnDone = banner.findViewWithTag<TextView>("edit_mode_done_btn")
    val buttons = listOf(btnWMinus, btnWPlus, btnHMinus, btnHPlus, btnDone)
    assertTrue(buttons.all { it != null && it.isFocusable && it.isClickable })

    // Initial focus starts on W-
    assertTrue(btnWMinus.hasFocus())

    // Focus navigation right across all 4 resize buttons and Done
    assertEquals(btnWPlus.id, btnWMinus.nextFocusRightId)
    assertEquals(btnHMinus.id, btnWPlus.nextFocusRightId)
    assertEquals(btnHPlus.id, btnHMinus.nextFocusRightId)
    assertEquals(btnDone.id, btnHPlus.nextFocusRightId)

    assertTrue(btnWPlus.requestFocus())
    assertTrue(btnWPlus.hasFocus())
    btnWPlus.performClick()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    // Resizing keeps focus on the active button and updates span in prefs
    assertTrue(btnWPlus.hasFocus())
    assertTrue(prefs.getString("widgets_list_json", "")!!.contains("\"spanX\":3"))

    assertTrue(btnHPlus.requestFocus())
    assertTrue(btnHPlus.hasFocus())
    btnHPlus.performClick()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    assertTrue(btnHPlus.hasFocus())
    assertTrue(prefs.getString("widgets_list_json", "")!!.contains("\"spanY\":3"))

    // Done exits edit mode
    assertTrue(btnDone.requestFocus())
    btnDone.performClick()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    assertEquals(View.GONE, banner.visibility)
  }
}
