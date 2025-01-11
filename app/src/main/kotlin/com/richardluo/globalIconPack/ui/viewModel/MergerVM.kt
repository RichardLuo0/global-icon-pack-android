package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.richardluo.globalIconPack.iconPack.IconPackApps
import com.richardluo.globalIconPack.utils.IconPackCreator
import com.richardluo.globalIconPack.utils.IconPackCreator.IconEntryWithPack
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.getInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

data class NewAppIconInfo(val app: String, val packLabel: String, val entry: IconEntryWithPack?)

class MergerVM(app: Application) : AndroidViewModel(app) {
  private val context: Context
    get() = getApplication()

  private val iconCache by getInstance { IconCache(app) }
  var isLoading by mutableStateOf(false)
    private set

  val basePackFlow = MutableStateFlow("")
  private var basePack
    get() = basePackFlow.value
    set(value) {
      basePackFlow.value = value
    }

  val icons = mutableStateMapOf<ComponentName, AppIconInfo>()

  private var selectedApp by mutableStateOf<ComponentName?>(null)
  val packDialogState = mutableStateOf(false)
  val warningDialogState = mutableStateOf(false)
  val instructionDialogState = mutableStateOf(false)

  var newPackName by mutableStateOf("Merged Icon Pack")
  var newPackPackage by mutableStateOf("com.dummy.iconPack")
  var installedAppsOnly by mutableStateOf(true)

  init {
    basePackFlow
      .onEach { pack ->
        if (pack.isEmpty()) return@onEach
        isLoading = true
        icons.clear()
        withContext(Dispatchers.Default) {
          val iconPack = iconCache.getIconPack(pack)
          icons.putAll(
            context
              .getSystemService(Context.LAUNCHER_APPS_SERVICE)
              .asType<LauncherApps>()
              .getActivityList(null, Process.myUserHandle())
              .associate { info ->
                val cn = info.componentName
                Pair(
                  cn,
                  AppIconInfo(
                    cn.packageName,
                    info.label.toString(),
                    iconPack.getIconEntry(cn)?.let { IconEntryWithPack(it, pack) },
                  ),
                )
              }
          )
        }
        isLoading = false
      }
      .launchIn(viewModelScope)
  }

  suspend fun loadIcon(info: NewAppIconInfo) = iconCache.loadIcon(info.entry, info.app, basePack)

  suspend fun loadIcon(info: AppIconInfo) = iconCache.loadIcon(info.entry, info.app, basePack)

  fun openWarningDialog() {
    if (basePack.isNotEmpty()) warningDialogState.value = true
  }

  fun openPackDialog(cn: ComponentName) {
    selectedApp = cn
    packDialogState.value = true
  }

  suspend fun getIconForSelectedApp(): List<NewAppIconInfo> {
    val app = selectedApp ?: return listOf()
    return IconPackApps.get(context)
      .map { (pack, packApp) ->
        iconCache.getIconPack(pack).getIconEntry(app)?.let {
          NewAppIconInfo(app.packageName, packApp.label, IconEntryWithPack(it, pack))
        }
      }
      .filterNotNull()
      .plus(NewAppIconInfo(app.packageName, "", null))
  }

  fun saveNewIcon(entry: IconEntryWithPack?) {
    val app = selectedApp ?: return
    icons[app] = icons[app]?.copy(entry = entry) ?: return
  }

  suspend fun createIconPack(uri: Uri) {
    if (basePack.isEmpty()) return
    isLoading = true
    withContext(Dispatchers.Default) {
      IconPackCreator.createIconPack(
        context,
        uri,
        newPackName,
        newPackPackage,
        basePack,
        icons.mapValues { it.value.entry },
        installedAppsOnly,
        iconCache::getIconPack,
      )
    }
    instructionDialogState.value = true
    isLoading = false
  }
}
