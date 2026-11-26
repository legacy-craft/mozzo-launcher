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
}
