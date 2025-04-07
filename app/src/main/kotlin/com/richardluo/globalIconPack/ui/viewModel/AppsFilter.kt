package com.richardluo.globalIconPack.ui.viewModel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.os.Process
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.ui.model.AppIconInfo
import com.richardluo.globalIconPack.ui.model.IconInfo
import com.richardluo.globalIconPack.ui.model.ShortcutIconInfo
import com.richardluo.globalIconPack.ui.viewModel.IFilterApps.Type
import com.richardluo.globalIconPack.utils.Weak
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.debounceInput
import com.richardluo.globalIconPack.utils.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val appsCache =
  Weak<Array<List<IconInfo>>, Context> { context ->
    val userApps = mutableListOf<IconInfo>()
    val systemApps = mutableListOf<IconInfo>()

    fun add(flags: Int, iconInfo: IconInfo) =
      if ((flags and ApplicationInfo.FLAG_SYSTEM) == ApplicationInfo.FLAG_SYSTEM)
        systemApps.add(iconInfo)
      else userApps.add(iconInfo)

    val launcherApps =
      context
        .getSystemService(Context.LAUNCHER_APPS_SERVICE)
        .asType<LauncherApps>()
        .getActivityList(null, Process.myUserHandle())
        .map { info ->
          add(info.applicationInfo.flags, AppIconInfo(info))
          info.componentName.packageName
        }
        .toSet()
    context.packageManager
      .getInstalledApplications(0)
      .filter { !launcherApps.contains(it.packageName) }
      .forEach { info -> add(info.flags, AppIconInfo(context, info)) }

    arrayOf(userApps.distinct().sortedBy { it.label }, systemApps.distinct().sortedBy { it.label })
  }
private val shortcutsCache =
  Weak<List<IconInfo>, Context> { context ->
    val shortcuts =
      context
        .getSystemService(Context.LAUNCHER_APPS_SERVICE)
        .asType<LauncherApps>()
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

interface IFilterApps {
  enum class Type {
    User,
    System,
    Shortcut,
  }

  val searchText: MutableState<String>
  val filterType: MutableState<Type>
  val filteredApps: Flow<List<IconInfo>?>

  suspend fun getAllApps(): List<IconInfo>
}

class FilterApps(context: Context) : IFilterApps {
  override val searchText = mutableStateOf("")
  override val filterType = mutableStateOf(Type.User)

  private val apps by lazy { appsCache.get(context) }
  private val shortcuts by lazy { shortcutsCache.get(context) }

  override suspend fun getAllApps(): List<IconInfo> =
    withContext(Dispatchers.Default) {
      mutableListOf<IconInfo>().apply {
        apps.forEach { addAll(it) }
        runCatching { addAll(shortcuts) }
      }
    }

  private val currentApps =
    snapshotFlow { filterType.value }
      .map { type ->
        when (type) {
          Type.User,
          Type.System -> apps[type.ordinal]
          Type.Shortcut ->
            runCatching { shortcuts }
              .getOrElse {
                log(it)
                withContext(Dispatchers.Main) {
                  Toast.makeText(context, R.string.requiresHookSystem, Toast.LENGTH_LONG).show()
                }
                listOf()
              }
        }
      }
      .flowOn(Dispatchers.IO)

  override val filteredApps =
    combineTransform(currentApps, snapshotFlow { searchText.value }.debounceInput()) { apps, text ->
        emit(null)
        emit(
          if (text.isEmpty()) apps
          else apps.filter { info -> info.label.contains(text, ignoreCase = true) }
        )
      }
      .conflate()
      .flowOn(Dispatchers.Default)
}
