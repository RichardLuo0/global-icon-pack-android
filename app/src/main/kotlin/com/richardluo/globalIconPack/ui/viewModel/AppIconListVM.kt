package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.ui.model.ActivityCompInfo
import com.richardluo.globalIconPack.ui.model.AnyCompIcon
import com.richardluo.globalIconPack.ui.model.AppCompIcon
import com.richardluo.globalIconPack.ui.model.AppCompInfo
import com.richardluo.globalIconPack.ui.model.CompInfo
import com.richardluo.globalIconPack.ui.model.IconEntryWithPack
import com.richardluo.globalIconPack.ui.model.IconPack
import com.richardluo.globalIconPack.ui.model.ShortcutCompInfo
import com.richardluo.globalIconPack.ui.model.VariantIcon
import com.richardluo.globalIconPack.ui.model.to
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.filter
import com.richardluo.globalIconPack.utils.runCatchingToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

interface IconsHolder {
  val updateFlow: Flow<*>

  fun getCurrentIconPack(): IconPack?

  suspend fun loadIcon(compIcon: AnyCompIcon): ImageBitmap

  fun mapIconEntry(cnList: List<ComponentName>): List<IconEntryWithPack?>

  fun saveIcon(info: CompInfo, icon: VariantIcon)

  fun restoreDefault(info: AppCompInfo)

  fun clearAll(info: AppCompInfo)
}

class AppIconListVM(context: Application, iconsHolder: IconsHolder, appIcon: AppCompIcon) :
  ViewModel() {
  val appIconEntry =
    iconsHolder.updateFlow
      .map {
        return@map iconsHolder.mapIconEntry(listOf(appIcon.info.componentName)).getOrNull(0)
      }
      .flowOn(Dispatchers.IO)
      .stateIn(viewModelScope, SharingStarted.Lazily, null)

  val searchText = mutableStateOf("")

  val activityIcons =
    createFilteredIconsFlow(iconsHolder) {
      context.packageManager
        .getPackageInfo(appIcon.info.componentName.packageName, PackageManager.GET_ACTIVITIES)
        .activities
        ?.map { ActivityCompInfo(context, it) } ?: listOf()
    }

  val shortcutIcons =
    createFilteredIconsFlow(iconsHolder) {
      runCatchingToast(context, { context.getString(R.string.requiresHookSystem) }) {
          context
            .getSystemService(Context.LAUNCHER_APPS_SERVICE)
            .asType<LauncherApps>()!!
            .getShortcuts(
              ShortcutQuery()
                .setPackage(appIcon.info.componentName.packageName)
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
        ?.map { info -> ShortcutCompInfo(info) } ?: listOf()
    }

  private fun createFilteredIconsFlow(
    iconsHolder: IconsHolder,
    getCompInfos: suspend () -> List<CompInfo>,
  ) =
    flow {
        emit(null)
        emit(getCompInfos().distinctBy { it.componentName.className })
      }
      .flowOn(Dispatchers.IO)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
      .combine(iconsHolder.updateFlow) { iconInfos, _ ->
        iconInfos
          ?.let {
            it.zip(iconsHolder.mapIconEntry(it.map { it.componentName })) { info, entry ->
              info to entry
            }
          }
          ?.sortedBy { it.entry == null }
      }
      .flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
      .filter(snapshotFlow { searchText.value }) { (info), text ->
        info.componentName.className.contains(text, ignoreCase = true) ||
          info.label.contains(text, ignoreCase = true)
      }
      .stateIn(viewModelScope, SharingStarted.Lazily, null)

  companion object {
    fun CreationExtras.initializer(iconsHolder: IconsHolder, appCompIcon: AppCompIcon) =
      AppIconListVM(this[APPLICATION_KEY]!!, iconsHolder, appCompIcon)
  }
}
