package com.richardluo.globalIconPack.ui

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
import androidx.lifecycle.ViewModel
import com.richardluo.globalIconPack.iconPack.CopyableIconPack
import com.richardluo.globalIconPack.iconPack.IconPackApps
import com.richardluo.globalIconPack.utils.IconPackCreator
import com.richardluo.globalIconPack.utils.IconPackCreator.IconEntryWithPack
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.asType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NewAppIconInfo(val app: String, val packLabel: String, val entry: IconEntryWithPack)

data class AppIconInfo(val app: String, val label: String, val entry: IconEntryWithPack?)

class MergerVM : ViewModel() {
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

  private fun getIconPack(context: Context, pack: String) =
    iconPackCache.getOrPut(pack) {
      CopyableIconPack(
        WorldPreference.getPrefInApp(context),
        pack,
        context.packageManager.getResourcesForApplication(pack),
      )
    }

  suspend fun loadIcons(context: Context) {
    if (basePack.isEmpty()) return
    isLoading = true
    icons.clear()
    withContext(Dispatchers.Default) {
      val iconPack = getIconPack(context, basePack)
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

  fun getIcon(context: Context, info: NewAppIconInfo) = getIcon(context, info.entry, info.app)

  fun getIcon(context: Context, info: AppIconInfo) = getIcon(context, info.entry, info.app)

  private fun getIcon(context: Context, entry: IconEntryWithPack?, app: String) =
    if (entry != null)
      imageCache.getOrPut(entry.pack + "_" + app) {
        (getIconPack(context, entry.pack).getIcon(entry.entry, 0)
            ?: context.packageManager.getApplicationIcon(app))
          .toBitmap()
          .asImageBitmap()
      }
    else
      imageCache.getOrPut(basePack + "_" + app) {
        getIconPack(context, basePack)
          .genIconFrom(context.packageManager.getApplicationIcon(app))
          .toBitmap()
          .asImageBitmap()
      }

  suspend fun loadIconForSelectedApp(context: Context): List<NewAppIconInfo> {
    val app = selectedApp ?: return listOf()
    return IconPackApps.load(context)
      .map { (pack, packApp) ->
        getIconPack(context, pack).getIconEntry(app)?.let {
          NewAppIconInfo(app.packageName, packApp.label, IconEntryWithPack(it, pack))
        }
      }
      .filterNotNull()
  }

  fun saveNewIcon(entry: IconEntryWithPack) {
    val app = selectedApp ?: return
    icons[app] = icons[app]?.copy(entry = entry) ?: return
  }

  suspend fun createIconPack(context: Context, uri: Uri) {
    if (basePack.isEmpty()) return
    isLoading = true
    withContext(Dispatchers.Default) {
      IconPackCreator.createIconPack(
        context,
        uri,
        newPackName,
        newPackPackage,
        basePack,
        icons.filter { it.value.entry != null }.mapValues { it.value.entry!! },
        installedAppsOnly,
      ) {
        getIconPack(context, it)
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
