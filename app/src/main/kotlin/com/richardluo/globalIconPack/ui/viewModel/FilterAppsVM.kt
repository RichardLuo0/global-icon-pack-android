package com.richardluo.globalIconPack.ui.viewModel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.os.Process
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.iconPack.getComponentName
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.debounceInput
import com.richardluo.globalIconPack.utils.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

class FilterAppsVM(context: Context) {
  val searchText = mutableStateOf("")

  enum class Type {
    User,
    System,
    Shortcut,
  }

  val type = mutableStateOf(Type.User)

  private val apps = lazy {
    val userApps = mutableListOf<AppIconInfo>()
    val systemApps = mutableListOf<AppIconInfo>()

    fun add(flags: Int, appIconInfo: AppIconInfo) =
      if ((flags and ApplicationInfo.FLAG_SYSTEM) == ApplicationInfo.FLAG_SYSTEM)
        systemApps.add(appIconInfo)
      else userApps.add(appIconInfo)

    val launcherApps =
      context
        .getSystemService(Context.LAUNCHER_APPS_SERVICE)
        .asType<LauncherApps>()
        .getActivityList(null, Process.myUserHandle())
        .map { info ->
          val cn = info.componentName
          add(info.applicationInfo.flags, AppIconInfo(cn, info.label.toString()))
          cn.packageName
        }
        .toSet()
    context.packageManager
      .getInstalledApplications(0)
      .filter { !launcherApps.contains(it.packageName) }
      .forEach { info ->
        val cn = getComponentName(info.packageName)
        add(info.flags, AppIconInfo(cn, info.loadLabel(context.packageManager).toString()))
      }
    arrayOf(
      userApps.distinct().sortedBy { it.componentName },
      systemApps.distinct().sortedBy { it.componentName },
    )
  }

  private val shortcuts = lazy {
    val shortcuts =
      context
        .getSystemService(Context.LAUNCHER_APPS_SERVICE)
        .asType<LauncherApps>()
        .getShortcuts(
          ShortcutQuery()
            .setQueryFlags(
              ShortcutQuery.FLAG_MATCH_MANIFEST or ShortcutQuery.FLAG_MATCH_PINNED_BY_ANY_LAUNCHER
            ),
          Process.myUserHandle(),
        ) ?: return@lazy listOf()
    shortcuts
      .map { info -> ShortcutIconInfo(getComponentName(info), info) }
      .distinct()
      .sortedBy { it.componentName }
  }

  suspend fun getAllApps(): List<AppIconInfo> =
    withContext(Dispatchers.Default) {
      mutableListOf<AppIconInfo>().apply {
        apps.value.forEach { addAll(it) }
        runCatching { addAll(shortcuts.value) }
      }
    }

  private suspend fun getCurrentApps(context: Context) =
    runCatching {
        withContext(Dispatchers.IO) {
          when (type.value) {
            Type.User,
            Type.System -> apps.value[type.value.ordinal]
            Type.Shortcut -> shortcuts.value
          }
        }
      }
      .getOrElse {
        log(it)
        withContext(Dispatchers.Main) {
          Toast.makeText(context, context.getString(R.string.requiresHookSystem), Toast.LENGTH_LONG)
            .show()
        }
        listOf()
      }

  val filteredApps =
    combineTransform(
        snapshotFlow { type.value },
        snapshotFlow { searchText.value }.debounceInput(),
      ) { _, text ->
        emit(null)
        val apps = getCurrentApps(context)
        emit(
          if (text.isEmpty()) apps
          else
            withContext(Dispatchers.Default) {
              apps.filter { info -> info.label.contains(text, ignoreCase = true) }
            }
        )
      }
      .conflate()
}
