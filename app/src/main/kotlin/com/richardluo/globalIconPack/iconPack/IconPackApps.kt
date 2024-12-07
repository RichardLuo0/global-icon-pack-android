package com.richardluo.globalIconPack.iconPack

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable

data class IconPackApp(val label: String, val icon: Drawable)

private fun MutableMap<String, IconPackApp>.put(pm: PackageManager, resolveInfo: ResolveInfo) =
  resolveInfo.activityInfo.applicationInfo.let {
    put(it.packageName, IconPackApp(it.loadLabel(pm).toString(), it.loadIcon(pm)))
  }

object IconPackApps {
  private lateinit var packAppMap: Map<String, IconPackApp>

  fun load(context: Context): Map<String, IconPackApp> {
    if (!::packAppMap.isInitialized)
      packAppMap =
        mutableMapOf<String, IconPackApp>().apply {
          clear()
          val pm = context.packageManager
          listOf(
              "app.lawnchair.icons.THEMED_ICON",
              "org.adw.ActivityStarter.THEMES",
              "com.novalauncher.THEME",
            )
            .forEach { action ->
              pm.queryIntentActivities(Intent(action), 0).forEach { put(pm, it) }
            }
        }
    return packAppMap
  }
}
