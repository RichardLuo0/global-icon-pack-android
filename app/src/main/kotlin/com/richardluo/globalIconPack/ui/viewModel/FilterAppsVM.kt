package com.richardluo.globalIconPack.ui.viewModel

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import com.richardluo.globalIconPack.utils.IconPackCreator.IconEntryWithPack
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.debounceInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

class FilterAppsVM(val apps: Flow<Map<ComponentName, AppIconInfo>>) {
  val searchText = mutableStateOf("")
  val systemOnly = mutableStateOf(false)
  val filteredApps =
    combineTransform(
        apps,
        snapshotFlow { searchText.value }.debounceInput(),
        snapshotFlow { systemOnly.value },
      ) { icons, text, systemOnly ->
        val preIcons = icons.filter { if (systemOnly) it.value.isSystem else !it.value.isSystem }
        if (text.isEmpty()) emit(preIcons)
        else {
          emit(null)
          emit(
            withContext(Dispatchers.Default) {
              preIcons.filter { (_, value) -> value.label.contains(text, ignoreCase = true) }
            }
          )
        }
      }
      .conflate()

  fun loadApps(
    context: Context,
    getEntry: (ComponentName) -> IconEntryWithPack?,
  ): Map<ComponentName, AppIconInfo> {
    val packageMap =
      mutableMapOf<String, ApplicationInfo>().apply {
        putAll(
          context.packageManager
            .getInstalledPackages(PackageManager.GET_META_DATA)
            .filter { it.applicationInfo != null }
            .associate { info -> info.packageName to info.applicationInfo!! }
        )
      }
    return context
      .getSystemService(Context.LAUNCHER_APPS_SERVICE)
      .asType<LauncherApps>()
      .getActivityList(null, Process.myUserHandle())
      .associate { info ->
        val cn = info.componentName
        packageMap.remove(cn.packageName)
        cn to AppIconInfo(cn.packageName, info.label.toString(), false, getEntry(cn))
      }
      .plus(
        packageMap.entries.associate { (_, info) ->
          val cn = ComponentName(info.packageName, "")
          cn to
            AppIconInfo(
              info.packageName,
              info.loadLabel(context.packageManager).toString(),
              (info.flags and ApplicationInfo.FLAG_SYSTEM) == ApplicationInfo.FLAG_SYSTEM,
              getEntry(cn),
            )
        }
      )
  }
}
