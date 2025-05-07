package com.richardluo.globalIconPack.ui.viewModel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.richardluo.globalIconPack.ui.MyApplication
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn

object InstalledAppsMonitor {
  @OptIn(DelicateCoroutinesApi::class)
  val flow =
    callbackFlow<Unit> {
        val app = MyApplication.context
        val packageChangeReceiver =
          object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
              trySend(Unit)
            }
          }
        app.registerReceiver(
          packageChangeReceiver,
          IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
          },
        )
        packageChangeReceiver.onReceive(app, null)
        awaitClose { app.unregisterReceiver(packageChangeReceiver) }
      }
      .shareIn(GlobalScope, started = SharingStarted.WhileSubscribed(), replay = 1)
}
