package com.richardluo.globalIconPack.ui.repo

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.richardluo.globalIconPack.ui.MyApplication
import com.richardluo.globalIconPack.ui.model.AppCompInfo
import com.richardluo.globalIconPack.utils.Logger
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

object Apps {
  @OptIn(DelicateCoroutinesApi::class)
  val flow =
    InstalledAppsMonitor.flow
      .map {
        val app = MyApplication.context

        val userApps = mutableListOf<AppCompInfo>()
        val systemApps = mutableListOf<AppCompInfo>()

        val pm = app.packageManager
        // MATCH_ANY_USER is added to this query inside system server by
        // BypassCrossUserPermission, so when that bypass stops working the call throws
        // SecurityException and takes the whole process down from this flow. Retrying without
        // MATCH_UNINSTALLED_PACKAGES would fail the same way, since the flag is not added here,
        // so just degrade to an empty list.
        val installed: List<ApplicationInfo> =
          try {
            pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
          } catch (e: Exception) {
            Logger.logE(e)
            emptyList()
          }

        installed.forEach { info ->
          if ((info.flags and ApplicationInfo.FLAG_SYSTEM) == ApplicationInfo.FLAG_SYSTEM)
            systemApps.add(AppCompInfo(app, info))
          else userApps.add(AppCompInfo(app, info))
        }

        arrayOf(
          userApps.distinct().sortedBy { it.label },
          systemApps.distinct().sortedBy { it.label },
        )
      }
      .flowOn(Dispatchers.IO)
      .shareIn(
        GlobalScope,
        started =
          SharingStarted.WhileSubscribed(
            stopTimeoutMillis = 60 * 1000,
            replayExpirationMillis = 60 * 1000,
          ),
        replay = 1,
      )

  suspend fun getAll() = flow.first().let { it[0] + it[1] }

  suspend fun getAllWithShortcuts() =
    getAll().map { it.componentName.packageName }.let { it + it.map { "$it@" } }
}
