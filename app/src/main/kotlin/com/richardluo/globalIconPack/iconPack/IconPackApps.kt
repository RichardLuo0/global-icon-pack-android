package com.richardluo.globalIconPack.iconPack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

data class IconPackApp(val label: String, val icon: Drawable)

private fun MutableMap<String, IconPackApp>.put(pm: PackageManager, resolveInfo: ResolveInfo) =
  resolveInfo.activityInfo.applicationInfo.let {
    put(it.packageName, IconPackApp(it.loadLabel(pm).toString(), it.loadIcon(pm)))
  }

object IconPackApps {
  private var packAppMapFlow: Flow<Map<String, IconPackApp>>? = null
  private var packAppMap = WeakReference<Map<String, IconPackApp>>(null)

  private fun getIconAppMap(context: Context) =
    mutableMapOf<String, IconPackApp>()
      .apply {
        val pm = context.packageManager
        listOf(
            "app.lawnchair.icons.THEMED_ICON",
            "org.adw.ActivityStarter.THEMES",
            "com.novalauncher.THEME",
          )
          .forEach { action -> pm.queryIntentActivities(Intent(action), 0).forEach { put(pm, it) } }
      }
      .also { packAppMap = WeakReference(it) }

  suspend fun get(context: Context) =
    packAppMap.get() ?: withContext(Dispatchers.IO) { getIconAppMap(context) }

  @Composable
  fun get(): Map<String, IconPackApp> {
    val app = LocalContext.current.applicationContext
    val flow =
      packAppMapFlow
        ?: run {
          val flow =
            callbackFlow<Map<String, IconPackApp>> {
              val packageChangeReceiver =
                object : BroadcastReceiver() {

                  override fun onReceive(context: Context, intent: Intent?) {
                    trySend(getIconAppMap(context))
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
          packAppMapFlow = flow
          flow
        }
    return flow.collectAsStateWithLifecycle(packAppMap.get() ?: mapOf()).value
  }
}
