package com.richardluo.globalIconPack.iconPack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class IconPackApp(val label: String, val icon: Drawable)

object IconPackApps {
  private var sharedFlow: SharedFlow<Map<String, IconPackApp>>? = null

  private suspend fun getIconAppMap(context: Context) =
    withContext(Dispatchers.IO) {
      val pm = context.packageManager
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

  suspend fun get(context: Context) =
    getFlow(context).replayCache.getOrElse(0) { getIconAppMap(context) }

  @OptIn(DelicateCoroutinesApi::class)
  fun getFlow(context: Context): SharedFlow<Map<String, IconPackApp>> {
    val app = context.applicationContext
    return sharedFlow
      ?: callbackFlow<Map<String, IconPackApp>> {
          val packageChangeReceiver =
            object : BroadcastReceiver() {
              override fun onReceive(context: Context, intent: Intent?) {
                GlobalScope.launch { trySend(getIconAppMap(context)) }
              }
            }
          app.registerReceiver(
            packageChangeReceiver,
            IntentFilter().apply {
              addAction(Intent.ACTION_PACKAGE_ADDED)
              addAction(Intent.ACTION_PACKAGE_REMOVED)
              addDataScheme("package")
            },
          )
          packageChangeReceiver.onReceive(app, null)
          awaitClose { app.unregisterReceiver(packageChangeReceiver) }
        }
        .shareIn(GlobalScope, started = SharingStarted.WhileSubscribed(), replay = 1)
        .also { sharedFlow = it }
  }
}
