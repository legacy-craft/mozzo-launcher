package com.mozzolauncher.app

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val isPinned: Boolean = false,
    val isRecent: Boolean = false
)

class AppRepository(private val context: Context) {

    fun loadAllLaunchableApps(): List<AppInfo> {
        val pm = context.packageManager
        val map = LinkedHashMap<String, AppInfo>()

        val intents = listOf(
            Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
            Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) }
        )

        for (intent in intents) {
            val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            for (ri in resolved) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg == context.packageName) continue
                if (!map.containsKey(pkg)) {
                    val label = ri.loadLabel(pm)?.toString()?.ifBlank { pkg } ?: pkg
                    val icon = ri.loadIcon(pm)
                    map[pkg] = AppInfo(label = label, packageName = pkg, icon = icon)
                }
            }
        }
        return map.values.sortedBy { it.label.lowercase() }
    }

    fun getRecentPackages(prefs: PrefsManager, limit: Int = 5): List<String> {
        val combined = LinkedHashSet<String>()
        combined.addAll(prefs.getLocalRecents())

        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm?.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000L * 3600 * 24, now)
            if (!stats.isNullOrEmpty()) {
                stats.filter { it.lastTimeUsed > 0 && it.packageName != context.packageName }
                    .sortedByDescending { it.lastTimeUsed }
                    .forEach { combined.add(it.packageName) }
            }
        } catch (_: Exception) { }

        return combined.take(limit)
    }

    fun launchApp(pkg: String, prefs: PrefsManager): Boolean {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(pkg)
            ?: pm.getLeanbackLaunchIntentForPackage(pkg)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            prefs.recordLaunchedApp(pkg)
            context.startActivity(launchIntent)
            true
        } else {
            false
        }
    }
}
