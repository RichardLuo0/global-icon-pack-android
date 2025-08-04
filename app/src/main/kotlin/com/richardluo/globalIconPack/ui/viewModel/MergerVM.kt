package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.iconPack.model.IconPackConfig
import com.richardluo.globalIconPack.iconPack.model.defaultIconPackConfig
import com.richardluo.globalIconPack.iconPack.source.isSamePackage
import com.richardluo.globalIconPack.ui.model.AnyCompIcon
import com.richardluo.globalIconPack.ui.model.AppCompInfo
import com.richardluo.globalIconPack.ui.model.CompIcon
import com.richardluo.globalIconPack.ui.model.CompInfo
import com.richardluo.globalIconPack.ui.model.IconEntryWithPack
import com.richardluo.globalIconPack.ui.model.IconPack
import com.richardluo.globalIconPack.ui.model.VariantIcon
import com.richardluo.globalIconPack.ui.model.VariantPackIcon
import com.richardluo.globalIconPack.ui.repo.Apps
import com.richardluo.globalIconPack.utils.ContextVM
import com.richardluo.globalIconPack.utils.ILoadable
import com.richardluo.globalIconPack.utils.IconPackCreator
import com.richardluo.globalIconPack.utils.IconPackCreator.Progress
import com.richardluo.globalIconPack.utils.Loadable
import com.richardluo.globalIconPack.utils.MapPreferences
import com.richardluo.globalIconPack.utils.SingletonManager.get
import com.richardluo.globalIconPack.utils.map
import com.richardluo.globalIconPack.utils.mapSaver
import com.richardluo.globalIconPack.utils.runCatchingToast
import com.richardluo.globalIconPack.utils.toMutableStateMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.Preferences

@OptIn(SavedStateHandleSaveableApi::class)
class MergerVM(context: Application, savedStateHandle: SavedStateHandle) :
  ContextVM(context), IAppsFilter by AppsFilter(), IconsHolder, ILoadable by Loadable() {
  private val iconPackCache by get { IconPackCache(context) }
  private val iconCache by get { IconCache(context) }
  private val fallbackIconCache = IconCache(context)

  var baseIconPack by savedStateHandle.saveable { mutableStateOf<IconPack?>(null) }
    private set

  var basePack
    get() = baseIconPack?.pack
    set(value) {
      if (value == null || baseIconPack?.pack == value) return
      changedIcons.clear()
      baseIconPack = iconPackCache[value]
    }

  var iconCacheToken by mutableLongStateOf(System.currentTimeMillis())
  val optionsFlow = MutableStateFlow<Preferences>(MapPreferences())
  @OptIn(FlowPreview::class)
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

  private val changedIcons by
    savedStateHandle.saveable(saver = mapSaver { it.toMutableStateMap() }) {
      mutableStateMapOf<ComponentName, IconEntryWithPack?>()
    }

  override val updateFlow: Flow<*> = snapshotFlow { changedIcons.toMap() }

  private val icons =
    combine(snapshotFlow { baseIconPack }, Apps.flow, updateFlow) { iconPack, apps, _ ->
        return@combine if (iconPack == null) apps.map { emptyList() }
        else apps.map { it.map { info -> CompIcon(info, getIconEntry(info.componentName)) } }
      }
      .flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val filteredIconsFlow =
    createFilteredIconsFlow(icons).stateIn(viewModelScope, SharingStarted.Eagerly, null)

  override fun getCurrentIconPack() = baseIconPack

  private fun getIconEntry(cn: ComponentName): IconEntryWithPack? {
    return if (changedIcons.containsKey(cn)) changedIcons[cn]
    else {
      val iconPack = baseIconPack ?: return null
      iconPack.getIconEntry(cn)?.let { IconEntryWithPack(it, iconPack) }
    }
  }

  override fun mapIconEntry(cnList: List<ComponentName>) = cnList.map(::getIconEntry)

  override suspend fun loadIcon(compIcon: AnyCompIcon): ImageBitmap {
    return (if (compIcon.entry != null) iconCache else fallbackIconCache).loadIcon(
      compIcon.info,
      compIcon.entry,
      baseIconPack ?: return emptyImageBitmap,
      iconPackConfigFlow.value,
    )
  }

  suspend fun loadIcon(entry: IconEntryWithPack) = iconCache.loadIcon(entry.entry, entry.pack)

  override fun saveIcon(info: CompInfo, icon: VariantIcon) {
    changedIcons[info.componentName] =
      when (icon) {
        is VariantPackIcon -> IconEntryWithPack(icon.entry, icon.pack)
        else -> null
      }
  }

  fun restoreDefault() {
    changedIcons.clear()
  }

  override fun restoreDefault(info: AppCompInfo) {
    changedIcons.entries.removeIf { it.key.isSamePackage(info.componentName) }
  }

  override fun clearAll(info: AppCompInfo) {
    baseIconPack?.iconEntryMap?.forEach {
      if (it.key.isSamePackage(info.componentName)) changedIcons[it.key] = null
    }
  }

  fun autoFill(packs: List<String>) {
    packs.ifEmpty {
      return
    }
    launchLoading(viewModelScope) {
      val iconPacks = packs.map { iconPackCache[it] }
      icons.first()?.forEach { icons ->
        icons.forEach { (info, entry) ->
          if (entry != null) return@forEach
          val cn = info.componentName
          iconPacks.firstNotNullOfOrNull { iconPack ->
            iconPack.getIconEntry(cn)?.also { changedIcons[cn] = IconEntryWithPack(it, iconPack) }
          }
        }
      }
    }
  }

  var newPackIcon by savedStateHandle.saveable { mutableStateOf<IconEntryWithPack?>(null) }
  var newPackName by savedStateHandle.saveable { mutableStateOf("Merged Icon Pack") }
  var newPackPackage by savedStateHandle.saveable { mutableStateOf("com.dummy.iconPack") }
  var installedAppsOnly by savedStateHandle.saveable { mutableStateOf(true) }

  var creatingApkProgress by mutableStateOf<Progress?>(null)
    private set

  fun createIconPack(uri: Uri, onSuccess: () -> Unit) {
    val iconPack = baseIconPack ?: return
    val newPackName =
      newPackName.ifEmpty {
        return
      }
    val newPackPackage =
      newPackPackage.ifEmpty {
        return
      }

    viewModelScope.launch(Dispatchers.Default) {
      creatingApkProgress = Progress(info = "preparing")
      runCatchingToast(
        context,
        {
          if (it is IconPackCreator.FolderNotEmptyException)
            context.getString(R.string.requiresEmptyFolder)
          else it.toString()
        },
      ) {
        val packageNames = Apps.getAllWithShortcuts().toSet()
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
        ) {
          creatingApkProgress = it
        }

        onSuccess()
      }
      creatingApkProgress = null
    }
  }
}
