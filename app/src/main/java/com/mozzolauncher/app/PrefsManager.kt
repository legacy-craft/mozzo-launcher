package com.mozzolauncher.app

import android.content.Context

class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences("mozzo_prefs", Context.MODE_PRIVATE)

    var dockSide: String
        get() = prefs.getString("dock_side", "LEFT") ?: "LEFT"
        set(value) = prefs.edit().putString("dock_side", value).apply()

    var showRecents: Boolean
        get() = prefs.getBoolean("show_recents", true)
        set(value) = prefs.edit().putBoolean("show_recents", value).apply()

    var iconSizeDp: Int
        get() = prefs.getInt("icon_size_dp", 46)
        set(value) = prefs.edit().putInt("icon_size_dp", value).apply()

    var overlayMode: String
        get() = prefs.getString("overlay_mode", "HIDDEN") ?: "HIDDEN"
        set(value) = prefs.edit().putString("overlay_mode", value).apply()

    var wallpaperUri: String?
        get() = prefs.getString("wallpaper_uri", null)
        set(value) = prefs.edit().putString("wallpaper_uri", value).apply()

    var isDockInitialized: Boolean
        get() = prefs.getBoolean("dock_initialized", false)
        set(value) = prefs.edit().putBoolean("dock_initialized", value).apply()

    fun getPinnedApps(): MutableList<String> {
        val raw = prefs.getString("pinned_apps", "") ?: ""
        if (raw.isBlank()) return mutableListOf()
        return raw.split("|").filter { it.isNotBlank() }.toMutableList()
    }

    fun setPinnedApps(list: List<String>) {
        prefs.edit().putString("pinned_apps", list.distinct().joinToString("|")).apply()
    }

    fun togglePinned(pkg: String): Boolean {
        val list = getPinnedApps()
        val added = if (list.contains(pkg)) {
            list.remove(pkg)
            false
        } else {
            list.add(pkg)
            true
        }
        setPinnedApps(list)
        return added
    }

    fun movePinned(from: Int, to: Int) {
        val list = getPinnedApps()
        if (from in list.indices && to in list.indices) {
            val item = list.removeAt(from)
            list.add(to, item)
            setPinnedApps(list)
        }
    }

    fun recordLaunchedApp(pkg: String) {
        val list = getLocalRecents()
        list.remove(pkg)
        list.add(0, pkg)
        prefs.edit().putString("local_recents", list.take(15).joinToString("|")).apply()
    }

    fun getLocalRecents(): MutableList<String> {
        val raw = prefs.getString("local_recents", "") ?: ""
        if (raw.isBlank()) return mutableListOf()
        return raw.split("|").filter { it.isNotBlank() }.toMutableList()
    }
}
