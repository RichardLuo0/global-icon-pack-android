package com.richardluo.globalIconPack.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import androidx.collection.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import com.richardluo.globalIconPack.iconPack.CopyableIconPack
import com.richardluo.globalIconPack.iconPack.IconPackApps
import com.richardluo.globalIconPack.utils.IconPackCreator
import com.richardluo.globalIconPack.utils.IconPackCreator.IconEntryWithPack
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.asType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NewAppIconInfo(val app: String, val packLabel: String, val entry: IconEntryWithPack?)

data class AppIconInfo(val app: String, val label: String, val entry: IconEntryWithPack?)

class MergerVM(app: Application) : AndroidViewModel(app) {
  private val context: Context
    get() = getApplication()

  var basePack by mutableStateOf("")
  var icons = mutableStateMapOf<ComponentName, AppIconInfo>()

  private var selectedApp by mutableStateOf<ComponentName?>(null)
  val packDialogState = mutableStateOf(false)
  val warningDialogState = mutableStateOf(false)
  val instructionDialogState = mutableStateOf(false)

  var isLoading by mutableStateOf(false)
    private set

  var newPackName by mutableStateOf("Merged Icon Pack")
  var newPackPackage by mutableStateOf("com.dummy.iconPack")
  var installedAppsOnly by mutableStateOf(true)

  private val iconPackCache = mutableMapOf<String, CopyableIconPack>()

  private fun getIconPack(pack: String) =
    iconPackCache.getOrPut(pack) {
      CopyableIconPack(
        WorldPreference.getPrefInApp(context),
        pack,
        context.packageManager.getResourcesForApplication(pack),
      )
    }

  suspend fun loadIcons() {
    if (basePack.isEmpty()) return
    isLoading = true
    icons.clear()
    withContext(Dispatchers.Default) {
      val iconPack = getIconPack(basePack)
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
                iconPack.getIconEntry(cn)?.let { IconEntryWithPack(it, basePack) },
              ),
            )
          }
      )
    }
    isLoading = false
  }

  fun openPackDialog(cn: ComponentName) {
    selectedApp = cn
    packDialogState.value = true
  }

  private val imageCache = LruCache<String, ImageBitmap>(4 * 1024 * 1024)

  fun getIcon(info: NewAppIconInfo) = getIcon(info.entry, info.app)

  fun getIcon(info: AppIconInfo) = getIcon(info.entry, info.app)

  private fun getIcon(entry: IconEntryWithPack?, app: String) =
    if (entry != null)
      imageCache.getOrPut("${entry.pack}/$app") {
        (getIconPack(entry.pack).getIcon(entry.entry, 0)
            ?: context.packageManager.getApplicationIcon(app))
          .toBitmap()
          .asImageBitmap()
      }
    else
      imageCache.getOrPut("$basePack/fallback/$app") {
        getIconPack(basePack)
          .genIconFrom(context.packageManager.getApplicationIcon(app))
          .toBitmap()
          .asImageBitmap()
      }

  suspend fun loadIconForSelectedApp(): List<NewAppIconInfo> {
    val app = selectedApp ?: return listOf()
    return IconPackApps.load(context)
      .map { (pack, packApp) ->
        getIconPack(pack).getIconEntry(app)?.let {
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
      ) {
        getIconPack(it)
      }
    }
    instructionDialogState.value = true
    isLoading = false
  }
}

private fun <K : Any, V : Any> LruCache<K, V>.getOrPut(key: K, defaultValue: () -> V): V {
  val value = get(key)
  return if (value == null) {
    val answer = defaultValue()
    put(key, answer)
    answer
  } else {
    value
  }
}
