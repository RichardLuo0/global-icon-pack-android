package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.richardluo.globalIconPack.PrefDef
import com.richardluo.globalIconPack.PrefKey
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.iconPack.database.IconPackDB
import com.richardluo.globalIconPack.utils.IconPackCreator.IconEntryWithPack
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.getBlob
import com.richardluo.globalIconPack.utils.getFirstRow
import com.richardluo.globalIconPack.utils.getInstance
import com.richardluo.globalIconPack.utils.getString
import kotlin.collections.set
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IconVariantVM(app: Application) : AndroidViewModel(app) {
  private val context: Context
    get() = getApplication()

  private val iconCache by getInstance { IconCache(app) }
  private val iconPackDB by getInstance { IconPackDB(app) }
  var isLoading by mutableStateOf(false)
    private set

  val basePack =
    WorldPreference.getPrefInApp(context).getString(PrefKey.ICON_PACK, PrefDef.ICON_PACK)!!
  private val iconPackAsFallback =
    WorldPreference.getPrefInApp(context)
      .getBoolean(PrefKey.ICON_PACK_AS_FALLBACK, PrefDef.ICON_PACK_AS_FALLBACK)
  val icons = mutableStateMapOf<ComponentName, AppIconInfo>()
  private val iconsFlow = snapshotFlow { icons.toMap() }

  private val modifiedChangeFlow = MutableSharedFlow<Boolean>(1).apply { tryEmit(false) }
  val modified =
    combine(iconsFlow, modifiedChangeFlow) { _, _ ->
        return@combine withContext(Dispatchers.IO) { iconPackDB.isPackModified(basePack) }
      }
      .stateIn(viewModelScope, SharingStarted.Lazily, false)

  val expandSearchBar = mutableStateOf(false)

  val filterAppsVM = FilterAppsVM(iconsFlow)
  val chooseIconVM = ChooseIconVM(this::basePack, iconCache)

  init {
    viewModelScope.launch { loadIcons() }
  }

  private suspend fun loadIcons() {
    if (basePack.isEmpty()) return
    isLoading = true
    withContext(Dispatchers.IO) {
      icons.putAll(filterAppsVM.loadApps(context, ::getUpdatedEntryWithPack))
    }
    isLoading = false
  }

  private fun getUpdatedEntryWithPack(cn: ComponentName) =
    iconPackDB.getIcon(basePack, cn, iconPackAsFallback).getFirstRow {
      val entry = IconEntry.from(it.getBlob("entry"))
      val pack = it.getString("pack").ifEmpty { basePack }
      IconEntryWithPack(entry, iconCache.getIconPack(pack))
    }

  suspend fun loadIcon(info: AppIconInfo) = iconCache.loadIcon(info.entry, info.app, basePack)

  fun openVariantSheet(cn: ComponentName) {
    chooseIconVM.selectedApp.value = cn
    chooseIconVM.variantSheet = true
  }

  suspend fun restoreDefault() {
    if (basePack.isEmpty()) return
    isLoading = true
    withContext(Dispatchers.IO) {
      iconPackDB.resetPack(basePack)
      loadIcons()
    }
    isLoading = false
  }

  suspend fun flipModified() {
    if (basePack.isEmpty()) return
    withContext(Dispatchers.IO) { iconPackDB.setPackModified(basePack, !modified.value) }
    modifiedChangeFlow.emit(true)
  }

  suspend fun replaceIcon(icon: VariantIcon) {
    val cn = chooseIconVM.selectedApp.value ?: return
    isLoading = true
    withContext(Dispatchers.IO) {
      when (icon) {
        is OriginalIcon -> iconPackDB.deleteIcon(basePack, cn.packageName)
        is VariantPackIcon ->
          iconPackDB.insertOrUpdatePackageIcon(basePack, cn, icon.entry, icon.pack.pack)
      }
      icons[cn]?.copy(entry = getUpdatedEntryWithPack(cn))?.let { icons[cn] = it }
    }
    isLoading = false
  }
}
