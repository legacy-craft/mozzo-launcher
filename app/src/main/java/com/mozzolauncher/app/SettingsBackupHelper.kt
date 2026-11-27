package com.mozzolauncher.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object SettingsBackupHelper {

    private const val BACKUP_FORMAT_MARKER = "mozzo_launcher_backup"
    private val VALID_DOCK_SIDES = setOf("left", "bottom", "right")

    fun exportToJson(prefs: SharedPreferences): String {
        val root = JSONObject().apply {
            put("app", BACKUP_FORMAT_MARKER)
            put("version", 1)
            put("icon_size_dp", prefs.getInt("icon_size_dp", 48))
            put("dock_side", prefs.getString("dock_side", "left") ?: "left")
            put("show_clock", prefs.getBoolean("show_clock", true))
            put("panel_opacity_pct", prefs.getInt("panel_opacity_pct", 75))
            put("dock_favorites", prefs.getString("dock_favorites", "") ?: "")
            val shortcutsRaw = prefs.getString("web_shortcuts_json", "[]") ?: "[]"
            val shortcutsArr = try {
                JSONArray(shortcutsRaw)
            } catch (_: Exception) {
                JSONArray()
            }
            put("web_shortcuts", shortcutsArr)
        }
        return root.toString(2)
    }

    fun importFromJson(jsonText: String, prefs: SharedPreferences): Boolean {
        val root = try {
            JSONObject(jsonText)
        } catch (_: Exception) {
            return false
        }
        if (root.optString("app") != BACKUP_FORMAT_MARKER) {
            return false
        }

        val editor = prefs.edit()
        if (root.has("icon_size_dp")) {
            val size = root.optInt("icon_size_dp", 48).coerceIn(48, 80)
            editor.putInt("icon_size_dp", size)
        }
        if (root.has("dock_side")) {
            val side = root.optString("dock_side", "left")
            if (side in VALID_DOCK_SIDES) {
                editor.putString("dock_side", side)
            }
        }
        if (root.has("show_clock")) {
            editor.putBoolean("show_clock", root.optBoolean("show_clock", true))
        }
        if (root.has("panel_opacity_pct")) {
            val op = root.optInt("panel_opacity_pct", 75).coerceIn(0, 100)
            editor.putInt("panel_opacity_pct", op)
        }
        if (root.has("dock_favorites")) {
            val favs = root.optString("dock_favorites", "")
            editor.putString("dock_favorites", favs)
            editor.putBoolean("dock_initialized", true)
            editor.putBoolean("dock_defaults_v2", true)
        }
        val shortcutsArr = root.optJSONArray("web_shortcuts")
        if (shortcutsArr != null) {
            editor.putString("web_shortcuts_json", shortcutsArr.toString())
        }
        return editor.commit()
    }
}
