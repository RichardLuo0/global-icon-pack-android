package com.richardluo.globalIconPack.ui.viewModel

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import com.richardluo.globalIconPack.ui.MyApplication
import com.richardluo.globalIconPack.ui.model.AppIconInfo
import com.richardluo.globalIconPack.ui.viewModel.IAppsFilter.Type
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext

@OptIn(DelicateCoroutinesApi::class)
private val appsFlow =
  InstalledAppsMonitor.flow
    .map {
      val app = MyApplication.context

      val userApps = mutableListOf<AppIconInfo>()
      val systemApps = mutableListOf<AppIconInfo>()

      app.packageManager.getInstalledApplications(0).forEach { info ->
        if ((info.flags and ApplicationInfo.FLAG_SYSTEM) == ApplicationInfo.FLAG_SYSTEM)
          systemApps.add(AppIconInfo(app, info))
        else userApps.add(AppIconInfo(app, info))
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

interface IAppsFilter {
  enum class Type {
    User,
    System,
  }

  val filterType: MutableState<Type>
  val appsByType: Flow<List<AppIconInfo>>

  suspend fun getAllAppsAndShortcuts(): List<String>
}

class AppsFilter : IAppsFilter {
  override val filterType = mutableStateOf(Type.User)

  override suspend fun getAllAppsAndShortcuts(): List<String> =
    withContext(Dispatchers.Default) {
      val apps = appsFlow.first()
      val packageNames = (apps[0] + apps[1]).map { it.componentName.packageName }
      // Plus shortcuts
      packageNames + packageNames.map { "$it@" }
    }

  override val appsByType =
    snapshotFlow { filterType.value }.combine(appsFlow) { type, apps -> apps[type.ordinal] }
}
