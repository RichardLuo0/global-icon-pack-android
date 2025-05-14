package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.content.ComponentName
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
import com.richardluo.globalIconPack.iconPack.model.IconPackConfig
import com.richardluo.globalIconPack.iconPack.model.defaultIconPackConfig
import com.richardluo.globalIconPack.ui.IconsHolder
import com.richardluo.globalIconPack.ui.model.AppIconInfo
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
import com.richardluo.globalIconPack.utils.filter
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import me.zhanghai.compose.preference.Preferences

@OptIn(FlowPreview::class)
class MergerVM(context: Application) :
  ContextVM(context), IAppsFilter by AppsFilter(), IconsHolder {
  private val iconPackCache by get { IconPackCache(context) }
  private val iconCache by get { IconCache(context) }
  private val fallbackIconCache = IconCache(context, 1.0 / 16)

  var baseIconPack by mutableStateOf<IconPack?>(null)
    private set

  var basePack
    get() = baseIconPack?.pack
    set(value) {
      if (value == null || baseIconPack?.pack == value) return
      changedIcons.clear()
      baseIconPack = iconPackCache[value]
    }

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

  private val changedIcons = mutableStateMapOf<ComponentName, IconEntryWithPack?>()

  private fun getIconEntry(cn: ComponentName): IconEntryWithPack? {
    return if (changedIcons.containsKey(cn)) changedIcons[cn]
    else {
      val iconPack = baseIconPack ?: return null
      iconPack.getIconEntry(cn, iconPackConfigFlow.value)?.let { IconEntryWithPack(it, iconPack) }
    }
  }

  private val iconsByType =
    combineTransform(
        snapshotFlow { baseIconPack },
        appsByType,
        snapshotFlow { changedIcons.toMap() },
      ) { iconPack, apps, _ ->
        iconPack ?: return@combineTransform emit(listOf())
        emit(apps.map { info -> info to getIconEntry(info.componentName) })
      }
      .flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val filteredIcons =
    iconsByType
      .filter(snapshotFlow { searchText.value }.debounceInput()) { (info), text ->
        info.label.contains(text, ignoreCase = true)
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val appIconListVM =
    AppIconListVM(context, viewModelScope, snapshotFlow { changedIcons.toMap() }) {
      it.map(::getIconEntry)
    }

  override fun getCurrentIconPack() = baseIconPack

  fun setupActivityList(appIconInfo: AppIconInfo) {
    appIconListVM.setup(appIconInfo)
  }

  var newPackIcon by mutableStateOf<IconEntryWithPack?>(null)
  var newPackName by mutableStateOf("Merged Icon Pack")
  var newPackPackage by mutableStateOf("com.dummy.iconPack")
  var installedAppsOnly by mutableStateOf(true)

  override suspend fun loadIcon(pair: Pair<IconInfo, IconEntryWithPack?>): ImageBitmap {
    return (if (pair.second != null) iconCache else fallbackIconCache).loadIcon(
      pair.first,
      pair.second,
      baseIconPack ?: return emptyImageBitmap,
      iconPackConfigFlow.value,
    )
  }

  suspend fun loadIcon(entry: IconEntryWithPack) = iconCache.loadIcon(entry.entry, entry.pack)

  override fun saveIcon(info: IconInfo, icon: VariantIcon) {
    changedIcons[info.componentName] =
      when (icon) {
        is VariantPackIcon -> IconEntryWithPack(icon.entry, icon.pack)
        else -> null
      }
  }

  fun createIconPack(uri: Uri): Deferred<Unit>? {
    val iconPack = baseIconPack ?: return null
    val newPackName =
      newPackName.ifEmpty {
        return null
      }
    val newPackPackage =
      newPackPackage.ifEmpty {
        return null
      }

    return viewModelScope.async(Dispatchers.Default) {
      try {
        val packageNames = getAllAppsAndShortcuts().toSet()
        val newIcons =
          iconPack.iconEntryMap
            .filter { it.key.packageName in packageNames }
            .mapValues { IconEntryWithPack(it.value, iconPack) } + changedIcons

        IconPackCreator.createIconPack(
          context,
          uri,
          newPackIcon,
          newPackName,
          newPackPackage,
          iconPack,
          newIcons,
          installedAppsOnly,
        )
      } catch (e: IconPackCreator.FolderNotEmptyException) {
        withContext(Dispatchers.Main) {
          Toast.makeText(context, R.string.requiresEmptyFolder, Toast.LENGTH_LONG).show()
        }
        throw e
      }
    }
  }
}
