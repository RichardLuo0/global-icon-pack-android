package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.PackageManager
import android.os.Build
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
import com.richardluo.globalIconPack.utils.emit
import com.richardluo.globalIconPack.utils.filter
import com.richardluo.globalIconPack.utils.runCatchingToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingCommand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch

class AppIconListVM(
  context: Application,
  val scope: CoroutineScope,
  updateFlow: Flow<*>,
  getIconEntry: (cnList: List<ComponentName>) -> List<IconEntryWithPack?>,
) {
  var appIcon by mutableStateOf<Pair<AppIconInfo, IconEntryWithPack?>?>(null)
    private set

  val searchText = mutableStateOf("")

  private val startedRestartable = StartedRestartable()

  val activityIcons =
    createFilteredIconsFlow(updateFlow, getIconEntry) {
      context.packageManager.getPackageInfo(it, PackageManager.GET_ACTIVITIES).activities?.map {
        ActivityIconInfo(context, it)
      } ?: listOf()
    }

  val shortcutIcons =
    createFilteredIconsFlow(updateFlow, getIconEntry) {
      runCatchingToast(context, { context.getString(R.string.requiresHookSystem) }) {
          context
            .getSystemService(Context.LAUNCHER_APPS_SERVICE)
            .asType<LauncherApps>()!!
            .getShortcuts(
              ShortcutQuery()
                .setPackage(it)
                .setQueryFlags(
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    ShortcutQuery.FLAG_MATCH_MANIFEST or
                      ShortcutQuery.FLAG_MATCH_PINNED_BY_ANY_LAUNCHER or
                      ShortcutQuery.FLAG_MATCH_DYNAMIC
                  else ShortcutQuery.FLAG_MATCH_MANIFEST or ShortcutQuery.FLAG_MATCH_DYNAMIC
                ),
              Process.myUserHandle(),
            )
        }
        .getOrNull()
        ?.map { info -> ShortcutIconInfo(info) } ?: listOf()
    }

  fun setup(appIcon: Pair<AppIconInfo, IconEntryWithPack?>) {
    scope.launch {
      startedRestartable.restart()
      this@AppIconListVM.appIcon = appIcon
    }
  }

  private fun createFilteredIconsFlow(
    updateFlow: Flow<*>,
    getIconEntry: (cnList: List<ComponentName>) -> List<IconEntryWithPack?>,
    getIconInfos: suspend (String) -> List<IconInfo>,
  ) =
    snapshotFlow { appIcon?.first?.componentName?.packageName }
      .transform {
        emit(null)
        emit(getIconInfos(it ?: return@transform))
      }
      .flowOn(Dispatchers.IO)
      .stateIn(scope, SharingStarted.WhileSubscribed(), null)
      .combine(updateFlow) { iconInfos, _ ->
        iconInfos?.let { it.zip(getIconEntry(it.map { it.componentName })) }
      }
      .flowOn(Dispatchers.Default)
      .stateIn(scope, SharingStarted.WhileSubscribed(), null)
      .filter(snapshotFlow { searchText.value }) { (info), text ->
        info.componentName.className.contains(text, ignoreCase = true) ||
          info.label.contains(text, ignoreCase = true)
      }
      .stateIn(scope, startedRestartable, null)
}

private class StartedRestartable() : SharingStarted {
  private val restartFlow = MutableSharedFlow<Unit>()

  override fun command(subscriptionCount: StateFlow<Int>) = flow {
    var started = false
    restartFlow.collect {
      if (started) {
        if (subscriptionCount.value > 0) return@collect
        // Stop first
        started = false
        emit(SharingCommand.STOP)
      }
      // Lazy start
      subscriptionCount.first { it > 0 }
      started = true
      emit(SharingCommand.START)
    }
  }

  suspend fun restart() = restartFlow.emit()
}
