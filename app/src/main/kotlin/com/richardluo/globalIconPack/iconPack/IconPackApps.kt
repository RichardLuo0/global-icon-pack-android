package com.richardluo.globalIconPack.iconPack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.utils.ReAssignable

data class IconPackApp(val label: String, val icon: Drawable)

private fun MutableMap<String, IconPackApp>.put(pm: PackageManager, resolveInfo: ResolveInfo) =
  resolveInfo.activityInfo.applicationInfo.let {
    put(it.packageName, IconPackApp(it.loadLabel(pm).toString(), it.loadIcon(pm)))
  }

object IconPackApps {
  private val packAppMap = ReAssignable<Map<String, IconPackApp>>()

  private val packageChangeReceiver =
    object : BroadcastReceiver() {

      override fun onReceive(context: Context, intent: Intent?) {
        // Invalidate cache
        packAppMap.reset()
        context.unregisterReceiver(this)
      }
    }

  fun load(context: Context): Map<String, IconPackApp> {
    return packAppMap.orAssign {
      context.registerReceiver(
        packageChangeReceiver,
        IntentFilter().apply {
          addAction(Intent.ACTION_PACKAGE_ADDED)
          addAction(Intent.ACTION_PACKAGE_REMOVED)
        },
      )
      mutableMapOf<String, IconPackApp>().apply {
        val pm = context.packageManager
        listOf(
            "app.lawnchair.icons.THEMED_ICON",
            "org.adw.ActivityStarter.THEMES",
            "com.novalauncher.THEME",
          )
          .forEach { action -> pm.queryIntentActivities(Intent(action), 0).forEach { put(pm, it) } }
      }
    }
  }
}
