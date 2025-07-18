package com.richardluo.globalIconPack.ui.viewModel

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import com.richardluo.globalIconPack.ui.MyApplication
import com.richardluo.globalIconPack.ui.model.AppIconInfo
import com.richardluo.globalIconPack.ui.viewModel.IAppsFilter.Type
import com.richardluo.globalIconPack.utils.debounceInput
import com.richardluo.globalIconPack.utils.filter
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

  val searchText: MutableState<String>
  val filterType: MutableState<Type>
  val apps: Flow<Array<List<AppIconInfo>>>

  fun <T> createFilteredIconsFlow(icons: Flow<Array<List<Pair<AppIconInfo, T>>>?>) =
    snapshotFlow { filterType.value }
      .combine(icons) { type, icons ->
        icons ?: return@combine null
        icons[type.ordinal]
      }
      .filter(snapshotFlow { searchText.value }.debounceInput()) { (info), text ->
        info.label.contains(text, ignoreCase = true)
      }

  suspend fun getAllApps() =
    withContext(Dispatchers.Default) {
      val apps = appsFlow.first()
      apps[0] + apps[1]
    }

  suspend fun getAllAppsAndShortcuts() =
    getAllApps().map { it.componentName.packageName }.let { it + it.map { "$it@" } }
}

class AppsFilter : IAppsFilter {
  override val searchText = mutableStateOf("")
  override val filterType = mutableStateOf(Type.User)
  override val apps = appsFlow
}
