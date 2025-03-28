package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.iconPack.CopyableIconPack
import com.richardluo.globalIconPack.utils.ContextVM
import com.richardluo.globalIconPack.utils.IconPackCreator
import com.richardluo.globalIconPack.utils.IconPackCreator.IconEntryWithPack
import com.richardluo.globalIconPack.utils.getInstance
import com.richardluo.globalIconPack.utils.getOrPutNullable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MergerVM(app: Application) : ContextVM(app) {
  private val iconCache by getInstance { IconCache(app) }

  private val basePackState = mutableStateOf("")
  var basePack
    get() = basePackState.value
    set(value) {
      if (basePackState.value == value) return
      basePackState.value = value
      if (value.isNotEmpty()) chooseIconVM.variantPack.value = value
      cachedIcons.clear()
      changedIcons.clear()
    }

  val expandSearchBar = mutableStateOf(false)

  val filterAppsVM = FilterAppsVM(context)
  private val changedIcons = mutableStateMapOf<AppIconInfo, IconEntryWithPack?>()
  private val cachedIcons = mutableMapOf<AppIconInfo, IconEntryWithPack?>()

  private fun getIcon(iconPack: CopyableIconPack, info: AppIconInfo) =
    if (changedIcons.containsKey(info)) changedIcons[info]
    else
      cachedIcons.getOrPutNullable(info) {
        iconPack.getIconEntry(info.componentName)?.let { IconEntryWithPack(it, iconPack) }
      }

  val filteredIcons =
    combineTransform(
      snapshotFlow { basePack },
      filterAppsVM.filteredApps,
      snapshotFlow { changedIcons.toMap() },
    ) { basePack, apps, _ ->
      when {
        basePack.isEmpty() -> emit(listOf())
        apps == null -> emit(null)
        else -> {
          if (cachedIcons.isEmpty()) emit(null)
          emit(
            withContext(Dispatchers.Default) {
              val iconPack = iconCache.getIconPack(basePack)
              apps.map { info -> info to getIcon(iconPack, info) }
            }
          )
        }
      }
    }

  val chooseIconVM = ChooseIconVM(iconCache, this::basePack)

  var newPackName by mutableStateOf("Merged Icon Pack")
  var newPackPackage by mutableStateOf("com.dummy.iconPack")
  var installedAppsOnly by mutableStateOf(true)

  val warningDialogState = mutableStateOf(false)
  val instructionDialogState = mutableStateOf(false)

  var isCreatingApk by mutableStateOf(false)
    private set

  suspend fun loadIcon(pair: Pair<AppIconInfo, IconEntryWithPack?>) =
    iconCache.loadIcon(pair.first, pair.second, basePack)

  fun openWarningDialog() {
    if (basePack.isNotEmpty()) warningDialogState.value = true
  }

  fun saveNewIcon(icon: VariantIcon) {
    val info = chooseIconVM.selectedApp.value ?: return
    changedIcons[info] =
      when (icon) {
        is VariantPackIcon -> IconEntryWithPack(icon.entry, icon.pack)
        else -> null
      }
  }

  fun createIconPack(uri: Uri) {
    if (basePack.isEmpty()) return
    viewModelScope.launch {
      isCreatingApk = true
      val pack = iconCache.getIconPack(basePack)
      try {
        withContext(Dispatchers.Default) {
          IconPackCreator.createIconPack(
            context,
            uri,
            newPackName,
            newPackPackage,
            pack,
            filterAppsVM.getAllApps().associate { info ->
              info.componentName to getIcon(pack, info)
            },
            installedAppsOnly,
          )
        }
        instructionDialogState.value = true
      } catch (e: IconPackCreator.FolderNotEmptyException) {
        Toast.makeText(context, context.getString(R.string.requiresEmptyFolder), Toast.LENGTH_LONG)
          .show()
      } finally {
        isCreatingApk = false
      }
    }
  }
}
