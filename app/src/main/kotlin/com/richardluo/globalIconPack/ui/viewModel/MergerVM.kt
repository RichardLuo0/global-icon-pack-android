package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.compose.runtime.MutableState
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
import com.richardluo.globalIconPack.utils.IconPackCreator.Progress
import com.richardluo.globalIconPack.utils.InstanceManager.get
import com.richardluo.globalIconPack.utils.MapPreferences
import com.richardluo.globalIconPack.utils.map
import com.richardluo.globalIconPack.utils.runCatchingToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
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

  private val icons =
    combine(snapshotFlow { baseIconPack }, Apps.flow, snapshotFlow { changedIcons.toMap() }) {
        iconPack,
        apps,
        _ ->
        return@combine if (iconPack == null) apps.map { emptyList() }
        else apps.map { it.map { info -> info to getIconEntry(info.componentName) } }
      }
      .flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val filteredIconsFlow =
    createFilteredIconsFlow(icons).stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

  suspend fun autoFill(packs: List<String>) {
    packs.ifEmpty {
      return
    }
    val iconPacks = packs.map { iconPackCache[it] }
    icons.first()?.forEach { icons ->
      icons.forEach { (info, entry) ->
        if (entry != null) return@forEach
        val cn = info.componentName
        iconPacks.firstNotNullOfOrNull { iconPack ->
          iconPack.getIconEntry(cn, iconPackConfigFlow.value)?.also {
            changedIcons[cn] = IconEntryWithPack(it, iconPack)
          }
        }
      }
    }
  }

  fun createIconPack(uri: Uri, progress: MutableState<Progress?>, onSuccess: () -> Unit) {
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
      progress.value = Progress(0, 0, "")
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

        @Suppress("UNCHECKED_CAST")
        IconPackCreator.createIconPack(
          context,
          uri,
          newPackIcon,
          newPackName,
          newPackPackage,
          iconPack,
          newIcons,
          installedAppsOnly,
          progress as MutableState<Progress>,
        )

        onSuccess()
      }
      progress.value = null
    }
  }
}
