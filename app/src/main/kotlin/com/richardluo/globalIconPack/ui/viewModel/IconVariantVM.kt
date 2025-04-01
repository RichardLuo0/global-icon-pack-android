package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.iconPack.IconPackConfig
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.iconPack.database.IconPackDB
import com.richardluo.globalIconPack.utils.ContextVM
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.flowTrigger
import com.richardluo.globalIconPack.utils.getBlob
import com.richardluo.globalIconPack.utils.getFirstRow
import com.richardluo.globalIconPack.utils.getInstance
import com.richardluo.globalIconPack.utils.getOrPutNullable
import com.richardluo.globalIconPack.utils.getString
import com.richardluo.globalIconPack.utils.tryEmit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IconVariantVM(app: Application) : ContextVM(app) {
  private val iconPackCache by getInstance { IconPackCache(app) }
  private val iconCache = IconCache(app) { iconPackCache.getIconPack(it) }
  private val iconPackDB by getInstance { IconPackDB(app) }

  val basePack = WorldPreference.getPrefInApp(app).get(Pref.ICON_PACK)
  private val iconPackConfig = IconPackConfig(WorldPreference.getPrefInApp(app))

  val expandSearchBar = mutableStateOf(false)

  val filterAppsVM = FilterAppsVM(context)
  private val changedIcons = mutableStateMapOf<AppIconInfo, IconEntryWithPack?>()
  private val cachedIcons: MutableMap<AppIconInfo, IconEntryWithPack?> = mutableMapOf()
  val filteredIcons =
    combineTransform(filterAppsVM.filteredApps, snapshotFlow { changedIcons.toMap() }) { apps, icons
      ->
      if (apps == null) emit(null)
      else
        emit(
          withContext(Dispatchers.Default) {
            apps.map {
              it to
                (if (icons.containsKey(it)) icons[it]
                else cachedIcons.getOrPutNullable(it) { getUpdatedEntryWithPack(it.componentName) })
            }
          }
        )
    }

  val chooseIconVM =
    ChooseIconVM(
      basePack,
      { iconPackCache.getIconPack(it) },
      { pack, info ->
        iconPackCache.getIconPack(pack).getIconEntry(info.componentName, iconPackConfig)
      },
      { loadIcon(it) },
    )

  private val modifiedChangeTrigger = flowTrigger()
  val modified =
    combine(snapshotFlow { changedIcons.toMap() }, modifiedChangeTrigger) { _, _ ->
        return@combine withContext(Dispatchers.IO) { iconPackDB.isPackModified(basePack) }
      }
      .stateIn(viewModelScope, SharingStarted.Lazily, false)

  private fun getUpdatedEntryWithPack(cn: ComponentName) =
    iconPackDB.getIcon(basePack, cn, iconPackConfig.iconPackAsFallback).getFirstRow {
      val entry = IconEntry.from(it.getBlob("entry"))
      val pack = it.getString("pack").ifEmpty { basePack }
      IconEntryWithPack(entry, iconPackCache.getIconPack(pack))
    }

  suspend fun loadIcon(pair: Pair<AppIconInfo, IconEntryWithPack?>) =
    iconCache.loadIcon(pair.first, pair.second, basePack, iconPackConfig)

  fun restoreDefault() {
    if (basePack.isEmpty()) return
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        iconPackDB.resetPack(basePack)
        changedIcons.clear()
      }
    }
  }

  fun flipModified() {
    if (basePack.isEmpty()) return
    viewModelScope.launch {
      withContext(Dispatchers.IO) { iconPackDB.setPackModified(basePack, !modified.value) }
      modifiedChangeTrigger.tryEmit()
    }
  }

  fun replaceIcon(icon: VariantIcon) {
    val info = chooseIconVM.selectedApp.value ?: return
    val cn = info.componentName
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        when (icon) {
          is OriginalIcon -> iconPackDB.deleteIcon(basePack, cn.packageName)
          is VariantPackIcon ->
            when (info) {
              is ShortcutIconInfo ->
                iconPackDB.insertOrUpdateShortcutIcon(basePack, cn, icon.entry, icon.pack.pack)
              else -> iconPackDB.insertOrUpdateAppIcon(basePack, cn, icon.entry, icon.pack.pack)
            }
        }
        changedIcons[info] = getUpdatedEntryWithPack(cn)
      }
    }
  }
}
