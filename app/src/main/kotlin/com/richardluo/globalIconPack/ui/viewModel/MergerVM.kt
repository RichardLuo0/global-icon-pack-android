package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.content.ComponentName
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

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

  val expandSearchBar = mutableStateOf(false)

  val filterAppsVM = FilterAppsVM(snapshotFlow { icons.toMap() })
  val chooseIconVM = ChooseIconVM(this::basePack, iconCache)

  var newPackName by mutableStateOf("Merged Icon Pack")
  var newPackPackage by mutableStateOf("com.dummy.iconPack")
  var installedAppsOnly by mutableStateOf(true)

  val warningDialogState = mutableStateOf(false)
  val instructionDialogState = mutableStateOf(false)

  init {
    basePackFlow
      .onEach { pack ->
        if (pack.isEmpty()) return@onEach
        isLoading = true
        chooseIconVM.variantPack.value = pack
        icons.clear()
        withContext(Dispatchers.Default) {
          val iconPack = iconCache.getIconPack(pack)
          icons.putAll(
            filterAppsVM.loadApps(context) { cn ->
              iconPack.getIconEntry(cn)?.let { IconEntryWithPack(it, iconPack) }
            }
          )
        }
        isLoading = false
      }
      .launchIn(viewModelScope)
  }

  suspend fun loadIcon(info: AppIconInfo) = iconCache.loadIcon(info.entry, info.app, basePack)

  fun openWarningDialog() {
    if (basePack.isNotEmpty()) warningDialogState.value = true
  }

  fun openPackDialog(cn: ComponentName) {
    chooseIconVM.selectedApp.value = cn
    chooseIconVM.variantSheet = true
  }

  fun saveNewIcon(icon: VariantIcon) {
    val app = chooseIconVM.selectedApp.value ?: return
    val entry =
      when (icon) {
        is VariantPackIcon -> IconEntryWithPack(icon.entry, icon.pack)
        else -> null
      }
    icons[app] = icons[app]?.copy(entry = entry) ?: return
  }

  suspend fun createIconPack(uri: Uri) {
    if (basePack.isEmpty()) return
    isLoading = true
    try {
      withContext(Dispatchers.Default) {
        IconPackCreator.createIconPack(
          context,
          uri,
          newPackName,
          newPackPackage,
          iconCache.getIconPack(basePack),
          icons.mapValues { it.value.entry },
          installedAppsOnly,
        )
      }
      instructionDialogState.value = true
    } catch (e: IconPackCreator.FolderNotEmptyException) {
      Toast.makeText(context, context.getString(R.string.requiresEmptyFolder), Toast.LENGTH_LONG)
        .show()
    } finally {
      isLoading = false
    }
  }
}
