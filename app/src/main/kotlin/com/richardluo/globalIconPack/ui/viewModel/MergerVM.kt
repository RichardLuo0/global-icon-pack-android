package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.utils.IconPackCreator
import com.richardluo.globalIconPack.utils.IconPackCreator.IconEntryWithPack
import com.richardluo.globalIconPack.utils.getInstance
import com.richardluo.globalIconPack.utils.getOrPutNullable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MergerVM(app: Application) : AndroidViewModel(app) {
  private val context: Context
    get() = getApplication()

  private val iconCache by getInstance { IconCache(app) }

  val basePackFlow = MutableStateFlow("")
  private var basePack
    get() = basePackFlow.value
    set(value) {
      basePackFlow.value = value
    }

  val expandSearchBar = mutableStateOf(false)

  val filterAppsVM = FilterAppsVM(context)
  private val changedIcons = mutableStateMapOf<AppIconInfo, IconEntryWithPack?>()
  private val cachedIcons: MutableMap<AppIconInfo, IconEntryWithPack?> = mutableMapOf()
  val filteredIcons =
    combineTransform(
      basePackFlow,
      filterAppsVM.filteredApps,
      snapshotFlow { changedIcons.toMap() },
    ) { basePack, apps, icons ->
      if (basePack.isEmpty()) emit(listOf())
      else if (apps == null) emit(null)
      else
        emit(
          withContext(Dispatchers.Default) {
            val iconPack = iconCache.getIconPack(basePack)
            apps.map { info ->
              info to
                (if (icons.containsKey(info)) icons[info]
                else
                  cachedIcons.getOrPutNullable(info) {
                    iconPack.getIconEntry(info.componentName)?.let {
                      IconEntryWithPack(it, iconPack)
                    }
                  })
            }
          }
        )
    }

  val chooseIconVM = ChooseIconVM(iconCache, this::basePack)

  var newPackName by mutableStateOf("Merged Icon Pack")
  var newPackPackage by mutableStateOf("com.dummy.iconPack")
  var installedAppsOnly by mutableStateOf(true)

  val warningDialogState = mutableStateOf(false)
  val instructionDialogState = mutableStateOf(false)

  var isCreatingApk by mutableStateOf(false)
    private set

  init {
    basePackFlow
      .onEach { pack ->
        if (pack.isNotEmpty()) chooseIconVM.variantPack.value = pack
        cachedIcons.clear()
        changedIcons.clear()
      }
      .launchIn(viewModelScope)
  }

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
              val cn = info.componentName
              cn to
                if (changedIcons.containsKey(info)) changedIcons[info]
                else pack.getIconEntry(cn)?.let { IconEntryWithPack(it, pack) }
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
