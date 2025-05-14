package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.PackageManager
import android.os.Process
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.ui.model.ActivityIconInfo
import com.richardluo.globalIconPack.ui.model.AppIconInfo
import com.richardluo.globalIconPack.ui.model.IconEntryWithPack
import com.richardluo.globalIconPack.ui.model.IconInfo
import com.richardluo.globalIconPack.ui.model.ShortcutIconInfo
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.debounceInput
import com.richardluo.globalIconPack.utils.filter
import com.richardluo.globalIconPack.utils.runCatchingToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform

class AppIconListVM(
  context: Application,
  scope: CoroutineScope,
  updateFlow: Flow<*>,
  getIconEntry: (cnList: List<ComponentName>) -> List<IconEntryWithPack?>,
) {
  var appIconInfo by mutableStateOf<AppIconInfo?>(null)
    private set

  val searchText = mutableStateOf("")

  val activityIcons =
    createFilteredIconsFlow(scope, SharingStarted.Lazily, updateFlow, getIconEntry) {
      context.packageManager.getPackageInfo(it, PackageManager.GET_ACTIVITIES).activities?.map {
        ActivityIconInfo(context, it)
      } ?: listOf()
    }

  val shortcutIcons =
    createFilteredIconsFlow(scope, SharingStarted.WhileSubscribed(), updateFlow, getIconEntry) {
      runCatchingToast(context, { context.getString(R.string.requiresHookSystem) }) {
          context
            .getSystemService(Context.LAUNCHER_APPS_SERVICE)
            .asType<LauncherApps>()!!
            .getShortcuts(
              ShortcutQuery()
                .setPackage(it)
                .setQueryFlags(
                  ShortcutQuery.FLAG_MATCH_MANIFEST or
                    ShortcutQuery.FLAG_MATCH_PINNED_BY_ANY_LAUNCHER or
                    ShortcutQuery.FLAG_MATCH_DYNAMIC
                ),
              Process.myUserHandle(),
            )
        }
        .getOrNull()
        ?.map { info -> ShortcutIconInfo(info) } ?: listOf()
    }

  fun setup(appIconInfo: AppIconInfo) {
    this.appIconInfo = appIconInfo
  }

  val appIconEntry =
    snapshotFlow { appIconInfo }
      .combine(updateFlow) { info, _ ->
        info ?: return@combine null
        return@combine getIconEntry(listOf(info.componentName)).getOrNull(0)
      }
      .stateIn(scope, SharingStarted.Lazily, null)

  private fun createFilteredIconsFlow(
    scope: CoroutineScope,
    started: SharingStarted,
    updateFlow: Flow<*>,
    getIconEntry: (cnList: List<ComponentName>) -> List<IconEntryWithPack?>,
    getIconInfos: suspend (String) -> List<IconInfo>,
  ): Flow<List<Pair<IconInfo, IconEntryWithPack?>>?> {
    val iconInfoList =
      snapshotFlow { appIconInfo?.componentName?.packageName }
        .transform { packageName ->
          emit(null)
          emit(getIconInfos(packageName ?: return@transform))
        }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, started, null)
    val icons =
      iconInfoList
        .combine(updateFlow) { activityList, _ ->
          activityList?.let { it.zip(getIconEntry(it.map { it.componentName })) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(), null)
    return icons
      .filter(snapshotFlow { searchText.value }.debounceInput()) { (info), text ->
        info.componentName.className.contains(text, ignoreCase = true) ||
          info.label.contains(text, ignoreCase = true)
      }
      .stateIn(scope, SharingStarted.WhileSubscribed(), null)
  }
}
