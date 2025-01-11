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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.richardluo.globalIconPack.PrefKey
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.iconPack.database.IconPackDB
import com.richardluo.globalIconPack.iconPack.database.NormalIconEntry
import com.richardluo.globalIconPack.utils.IconPackCreator.IconEntryWithPack
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.getBlob
import com.richardluo.globalIconPack.utils.getFirstRow
import com.richardluo.globalIconPack.utils.getInstance
import kotlin.collections.set
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
class IconVariantVM(app: Application) : AndroidViewModel(app) {
  private val context: Context
    get() = getApplication()

  private val iconCache by getInstance { IconCache(app) }
  private val iconPackDB by getInstance { IconPackDB(app) }
  var isLoading by mutableStateOf(false)
    private set

  private val basePack = WorldPreference.getPrefInApp(context).getString(PrefKey.ICON_PACK, "")!!
  private val iconPackAsFallback =
    WorldPreference.getPrefInApp(context).getBoolean(PrefKey.ICON_PACK_AS_FALLBACK, false)
  var icons = mutableStateMapOf<ComponentName, AppIconInfo>()
    private set

  private val variantIcons = flow {
    emit(null)
    emit(withContext(Dispatchers.Default) { iconCache.getIconPack(basePack).getDrawables() })
  }

  var variantSheet by mutableStateOf(false)
  private val selectedApp = MutableStateFlow<ComponentName?>(null)
  val searchText = MutableStateFlow("")
  val suggestVariantIcons =
    combineTransform(
        variantIcons,
        selectedApp,
        searchText.debounce { if (it.isEmpty()) 0L else 300L },
      ) { icons, cn, text ->
        emit(null)
        emit(
          withContext(Dispatchers.Default) {
            icons ?: return@withContext listOf()
            cn ?: return@withContext listOf()
            val entry =
              iconCache.getIconPack(basePack).getIconEntry(cn) ?: return@withContext listOf()
            if (text.isEmpty()) icons.filter { it.startsWith(entry.name) }
            else icons.filter { it.contains(text) }
          }
        )
      }
      .conflate()

  init {
    viewModelScope.launch { loadIcons() }
  }

  private suspend fun loadIcons() {
    if (basePack.isEmpty()) return
    isLoading = true
    withContext(Dispatchers.Default) {
      val newIcons = mutableMapOf<ComponentName, AppIconInfo>()
      context
        .getSystemService(Context.LAUNCHER_APPS_SERVICE)
        .asType<LauncherApps>()
        .getActivityList(null, Process.myUserHandle())
        .forEach { info ->
          val cn = info.componentName
          getUpdatedEntryWithPack(cn)?.let {
            newIcons[cn] = AppIconInfo(cn.packageName, info.label.toString(), it)
          }
        }
      icons.putAll(newIcons)
    }
    isLoading = false
  }

  private fun getUpdatedEntryWithPack(cn: ComponentName): IconEntryWithPack? {
    val entry =
      iconPackDB.getIcon(basePack, cn, iconPackAsFallback).getFirstRow {
        IconEntry.from(it.getBlob("entry"))
      }
    return if (entry == null || icons[cn]?.entry?.entry?.name != entry.name)
      entry?.let { IconEntryWithPack(it, basePack) }
    else null
  }

  suspend fun loadIcon(info: AppIconInfo) = iconCache.loadIcon(info.entry, info.app, basePack)

  suspend fun loadIcon(iconName: String) = iconCache.loadIcon(iconName, basePack)

  fun openVariantSheet(cn: ComponentName) {
    selectedApp.value = cn
    variantSheet = true
  }

  suspend fun restoreDefault() {
    if (basePack.isEmpty()) return
    isLoading = true
    withContext(Dispatchers.Default) {
      iconPackDB.resetPack(basePack)
      loadIcons()
    }
    isLoading = false
  }

  suspend fun replaceIcon(iconName: String) {
    val cn = selectedApp.value ?: return
    isLoading = true
    withContext(Dispatchers.Default) {
      val entry = NormalIconEntry(iconName)
      iconPackDB.replaceIcon(basePack, cn, entry)
      getUpdatedEntryWithPack(cn)?.let { icons[cn]?.copy(entry = it) }?.let { icons[cn] = it }
    }
    isLoading = false
    variantSheet = false
  }
}
