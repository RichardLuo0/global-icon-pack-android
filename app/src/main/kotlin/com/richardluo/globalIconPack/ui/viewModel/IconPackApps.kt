package com.richardluo.globalIconPack.ui.viewModel

import android.content.Intent
import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.ui.MyApplication
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext

data class IconPackApp(val label: String, val icon: Drawable)

object IconPackApps {
  @OptIn(DelicateCoroutinesApi::class)
  var flow =
    InstalledAppsMonitor.flow
      .map { getIconAppMap() }
      .shareIn(GlobalScope, started = SharingStarted.WhileSubscribed(), replay = 1)

  suspend fun get() = flow.replayCache.getOrElse(0) { getIconAppMap() }

  private suspend fun getIconAppMap() =
    withContext(Dispatchers.IO) {
      val pm = MyApplication.context.packageManager
      listOf(
          "app.lawnchair.icons.THEMED_ICON",
          "org.adw.ActivityStarter.THEMES",
          "com.novalauncher.THEME",
          "org.adw.launcher.THEMES",
        )
        .map { action -> pm.queryIntentActivities(Intent(action), 0) }
        .flatten()
        .associate { info ->
          val ai = info.activityInfo.applicationInfo
          ai.packageName to IconPackApp(ai.loadLabel(pm).toString(), ai.loadIcon(pm))
        }
    }
}
