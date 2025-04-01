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
import androidx.lifecycle.viewModelScope
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.iconPack.IconPackConfig
import com.richardluo.globalIconPack.ui.model.IconPack
import com.richardluo.globalIconPack.utils.ContextVM
import com.richardluo.globalIconPack.utils.IconPackCreator
import com.richardluo.globalIconPack.utils.getInstance
import com.richardluo.globalIconPack.utils.getOrPutNullable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.compose.preference.MutablePreferences
import me.zhanghai.compose.preference.Preferences

@OptIn(FlowPreview::class)
class MergerVM(app: Application) : ContextVM(app) {
  private val iconPackCache by getInstance { IconPackCache(app) }
  private val iconCache = IconCache(app) { iconPackCache.getIconPack(it) }

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

  var iconCacheToken by mutableLongStateOf(System.currentTimeMillis())
  val optionsFlow = MutableStateFlow<Preferences>(MapPreferences())
  private val iconPackConfigFlow =
    optionsFlow
      .drop(1)
      .debounce(300L)
      .map { IconPackConfig(it) }
      .distinctUntilChanged()
      .onEach {
        iconCache.clearIcons()
        iconCacheToken = System.currentTimeMillis()
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, IconPackConfig())

  val filterAppsVM = FilterAppsVM(context)
  private val changedIcons = mutableStateMapOf<AppIconInfo, IconEntryWithPack?>()
  private val cachedIcons = mutableMapOf<AppIconInfo, IconEntryWithPack?>()

  private fun getIcon(iconPack: IconPack, info: AppIconInfo) =
    if (changedIcons.containsKey(info)) changedIcons[info]
    else
      cachedIcons.getOrPutNullable(info) {
        iconPack.getIconEntry(info.componentName, iconPackConfigFlow.value)?.let {
          IconEntryWithPack(it, iconPack)
        }
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
              val iconPack = iconPackCache.getIconPack(basePack)
              apps.map { info -> info to getIcon(iconPack, info) }
            }
          )
        }
      }
    }

  val chooseIconVM =
    ChooseIconVM(
      basePack,
      { iconPackCache.getIconPack(it) },
      { pack, info ->
        iconPackCache.getIconPack(pack).getIconEntry(info.componentName, iconPackConfigFlow.value)
      },
      { loadIcon(it) },
    )

  var newPackName by mutableStateOf("Merged Icon Pack")
  var newPackPackage by mutableStateOf("com.dummy.iconPack")
  var installedAppsOnly by mutableStateOf(true)

  val warningDialogState = mutableStateOf(false)
  val instructionDialogState = mutableStateOf(false)

  var isCreatingApk by mutableStateOf(false)
    private set

  suspend fun loadIcon(pair: Pair<AppIconInfo, IconEntryWithPack?>) =
    iconCache.loadIcon(pair.first, pair.second, basePack, iconPackConfigFlow.value)

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
      try {
        withContext(Dispatchers.Default) {
          val pack = iconPackCache.getIconPack(basePack)
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
      } catch (_: IconPackCreator.FolderNotEmptyException) {
        Toast.makeText(context, R.string.requiresEmptyFolder, Toast.LENGTH_LONG).show()
      } finally {
        isCreatingApk = false
      }
    }
  }
}

private class MapMutablePreferences(private val map: MutableMap<String, Any> = mutableMapOf()) :
  MutablePreferences {
  @Suppress("UNCHECKED_CAST") override fun <T> get(key: String): T? = map[key] as T?

  override fun asMap(): Map<String, Any> = map

  override fun toMutablePreferences(): MutablePreferences =
    MapMutablePreferences(map.toMutableMap())

  override fun <T> set(key: String, value: T?) {
    if (value != null) {
      map[key] = value
    } else {
      map -= key
    }
  }

  override fun clear() {
    map.clear()
  }
}

private class MapPreferences(private val map: Map<String, Any> = emptyMap()) : Preferences {
  @Suppress("UNCHECKED_CAST") override fun <T> get(key: String): T? = map[key] as T?

  override fun asMap(): Map<String, Any> = map

  override fun toMutablePreferences(): MutablePreferences =
    MapMutablePreferences(map.toMutableMap())
}
