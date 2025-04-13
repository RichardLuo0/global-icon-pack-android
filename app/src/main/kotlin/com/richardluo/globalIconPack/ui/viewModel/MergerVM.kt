package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.viewModelScope
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.iconPack.IconPackConfig
import com.richardluo.globalIconPack.iconPack.defaultIconPackConfig
import com.richardluo.globalIconPack.ui.model.IconEntryWithPack
import com.richardluo.globalIconPack.ui.model.IconInfo
import com.richardluo.globalIconPack.ui.model.IconPack
import com.richardluo.globalIconPack.ui.model.VariantIcon
import com.richardluo.globalIconPack.ui.model.VariantPackIcon
import com.richardluo.globalIconPack.utils.ContextVM
import com.richardluo.globalIconPack.utils.IconPackCreator
import com.richardluo.globalIconPack.utils.InstanceManager.get
import com.richardluo.globalIconPack.utils.MapPreferences
import com.richardluo.globalIconPack.utils.debounceInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.compose.preference.Preferences

@OptIn(FlowPreview::class)
class MergerVM(context: Application) : ContextVM(context), IAppsFilter by AppsFilter(context) {
  private val iconPackCache by get { IconPackCache(context) }
  private val iconCache by get { IconCache(context) }
  private val fallbackIconCache = IconCache(context, 1.0 / 16)

  var baseIconPack by mutableStateOf<IconPack?>(null)
    private set

  var basePack
    get() = baseIconPack?.pack
    set(value) {
      if (value == null || baseIconPack?.pack == value) return
      baseIconPack = iconPackCache.get(value)
      changedIcons.clear()
    }

  val expandSearchBar = mutableStateOf(false)
  val searchText = mutableStateOf("")

  var iconCacheToken by mutableLongStateOf(System.currentTimeMillis())
  val optionsFlow = MutableStateFlow<Preferences>(MapPreferences())
  private val iconPackConfigFlow =
    optionsFlow
      .drop(1)
      .debounce(300L)
      .map { IconPackConfig(it) }
      .distinctUntilChanged()
      .onEach {
        fallbackIconCache.clear()
        iconCacheToken = System.currentTimeMillis()
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, defaultIconPackConfig)

  private val changedIcons = mutableStateMapOf<IconInfo, IconEntryWithPack?>()

  private fun getIconEntry(iconPack: IconPack, info: IconInfo) =
    if (changedIcons.containsKey(info)) changedIcons[info]
    else
      iconPack.getIconEntry(info.componentName, iconPackConfigFlow.value)?.let {
        IconEntryWithPack(it, iconPack)
      }

  private val iconsByType =
    combineTransform(
        snapshotFlow { baseIconPack },
        appsByType,
        snapshotFlow { changedIcons.toMap() },
      ) { iconPack, apps, _ ->
        iconPack ?: return@combineTransform emit(listOf())
        emit(apps.map { info -> info to getIconEntry(iconPack, info) })
      }
      .flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val filteredIcons =
    combineTransform(iconsByType, snapshotFlow { searchText.value }.debounceInput()) { apps, text ->
        emit(null)
        apps ?: return@combineTransform
        emit(
          if (text.isEmpty()) apps
          else apps.filter { (info) -> info.label.contains(text, ignoreCase = true) }
        )
      }
      .conflate()
      .flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  var newPackName by mutableStateOf("Merged Icon Pack")
  var newPackPackage by mutableStateOf("com.dummy.iconPack")
  var installedAppsOnly by mutableStateOf(true)

  val warningDialogState = mutableStateOf(false)
  val instructionDialogState = mutableStateOf(false)

  var isCreatingApk by mutableStateOf(false)
    private set

  suspend fun loadIcon(pair: Pair<IconInfo, IconEntryWithPack?>): ImageBitmap {
    return (if (pair.second != null) iconCache else fallbackIconCache).loadIcon(
      pair.first,
      pair.second,
      baseIconPack ?: return emptyImageBitmap,
      iconPackConfigFlow.value,
    )
  }

  fun openWarningDialog() {
    if (baseIconPack != null) warningDialogState.value = true
  }

  fun saveNewIcon(info: IconInfo, icon: VariantIcon) {
    changedIcons[info] =
      when (icon) {
        is VariantPackIcon -> IconEntryWithPack(icon.entry, icon.pack)
        else -> null
      }
  }

  fun createIconPack(uri: Uri) {
    val iconPack = baseIconPack
    iconPack ?: return
    viewModelScope.launch(Dispatchers.Default) {
      isCreatingApk = true
      try {
        IconPackCreator.createIconPack(
          context,
          uri,
          newPackName,
          newPackPackage,
          iconPack,
          getAllApps().associate { info -> info.componentName to getIconEntry(iconPack, info) },
          installedAppsOnly,
        )
        instructionDialogState.value = true
      } catch (_: IconPackCreator.FolderNotEmptyException) {
        withContext(Dispatchers.Main) {
          Toast.makeText(context, R.string.requiresEmptyFolder, Toast.LENGTH_LONG).show()
        }
      } finally {
        isCreatingApk = false
      }
    }
  }
}
