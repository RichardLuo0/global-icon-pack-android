package com.richardluo.globalIconPack.ui.viewModel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.os.Process
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.ui.MyApplication
import com.richardluo.globalIconPack.ui.model.AppIconInfo
import com.richardluo.globalIconPack.ui.model.IconInfo
import com.richardluo.globalIconPack.ui.model.ShortcutIconInfo
import com.richardluo.globalIconPack.ui.viewModel.IAppsFilter.Type
import com.richardluo.globalIconPack.utils.Weak
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.runCatchingToast
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext

@OptIn(DelicateCoroutinesApi::class)
private val appsCache =
  InstalledAppsMonitor.flow
    .map {
      val app = MyApplication.context

      val userApps = mutableListOf<IconInfo>()
      val systemApps = mutableListOf<IconInfo>()

      fun add(flags: Int, iconInfo: IconInfo) =
        if ((flags and ApplicationInfo.FLAG_SYSTEM) == ApplicationInfo.FLAG_SYSTEM)
          systemApps.add(iconInfo)
        else userApps.add(iconInfo)

      val launcherApps =
        app
          .getSystemService(Context.LAUNCHER_APPS_SERVICE)
          .asType<LauncherApps>()!!
          .getActivityList(null, Process.myUserHandle())
          .map { info ->
            add(info.applicationInfo.flags, AppIconInfo(info))
            info.componentName.packageName
          }
          .toSet()
      app.packageManager
        .getInstalledApplications(0)
        .filter { !launcherApps.contains(it.packageName) }
        .forEach { info -> add(info.flags, AppIconInfo(app, info)) }

      arrayOf(
        userApps.distinct().sortedBy { it.label },
        systemApps.distinct().sortedBy { it.label },
      )
    }
    .shareIn(GlobalScope, started = SharingStarted.WhileSubscribed(), replay = 1)
private val shortcutsCache =
  Weak<List<IconInfo>, Unit> {
    val shortcuts =
      MyApplication.context
        .getSystemService(Context.LAUNCHER_APPS_SERVICE)
        .asType<LauncherApps>()!!
        .getShortcuts(
          ShortcutQuery()
            .setQueryFlags(
              ShortcutQuery.FLAG_MATCH_MANIFEST or
                ShortcutQuery.FLAG_MATCH_PINNED_BY_ANY_LAUNCHER or
                ShortcutQuery.FLAG_MATCH_DYNAMIC
            ),
          Process.myUserHandle(),
        ) ?: return@Weak listOf()
    shortcuts.map { info -> ShortcutIconInfo(info) }.distinct().sortedBy { it.componentName }
  }

interface IAppsFilter {
  enum class Type {
    User,
    System,
    Shortcut,
  }

  val filterType: MutableState<Type>
  val appsByType: Flow<List<IconInfo>>

  suspend fun getAllApps(): List<IconInfo>
}

class AppsFilter(context: Context) : IAppsFilter {
  override val filterType = mutableStateOf(Type.User)

  private val shortcuts by lazy { shortcutsCache.get(Unit) }

  override suspend fun getAllApps(): List<IconInfo> =
    withContext(Dispatchers.Default) {
      mutableListOf<IconInfo>().apply {
        appsCache.replayCache.getOrNull(0)?.forEach { addAll(it) }
        runCatching { addAll(shortcuts) }
      }
    }

  override val appsByType =
    snapshotFlow { filterType.value }
      .combine(appsCache) { type, apps ->
        when (type) {
          Type.User,
          Type.System -> apps[type.ordinal]
          Type.Shortcut ->
            runCatchingToast(context, { context.getString(R.string.requiresHookSystem) }) {
              shortcuts
            } ?: listOf()
        }
      }
      .flowOn(Dispatchers.IO)
}
