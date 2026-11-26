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
    findTextView(decor, "64")!!.performClick()
    findTextView(decor, activity.getString(R.string.btn_close))!!.performClick()
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
    assertEquals(2, label.minLines)
    assertEquals(2, label.maxLines)

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
      assertTrue(localized.getString(R.string.dock_pick_icon_title, "Test").contains("Test"))
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
    val tvDialogW = tvDialog.window!!.attributes.width
    // Capped <= 700dp (1400px at 2f) and noticeably narrower than 1920px TV screen
    assertTrue(tvDialogW in 1080..1400)
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
}
