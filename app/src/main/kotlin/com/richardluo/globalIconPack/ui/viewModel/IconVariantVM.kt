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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.richardluo.globalIconPack.PrefDef
import com.richardluo.globalIconPack.PrefKey
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.iconPack.database.IconPackDB
import com.richardluo.globalIconPack.iconPack.database.NormalIconEntry
import com.richardluo.globalIconPack.utils.IconPackCreator.IconEntryWithPack
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.debounceInput
import com.richardluo.globalIconPack.utils.getBlob
import com.richardluo.globalIconPack.utils.getFirstRow
import com.richardluo.globalIconPack.utils.getInstance
import com.richardluo.globalIconPack.utils.getString
import kotlin.collections.set
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface VariantIcon

class VariantPackIcon(val pack: String, val name: String) : VariantIcon

class OriginalIcon : VariantIcon

class IconVariantVM(app: Application) : AndroidViewModel(app) {
  private val context: Context
    get() = getApplication()

  private val iconCache by getInstance { IconCache(app) }
  private val iconPackDB by getInstance { IconPackDB(app) }
  var isLoading by mutableStateOf(false)
    private set

  val basePack =
    WorldPreference.getPrefInApp(context).getString(PrefKey.ICON_PACK, PrefDef.ICON_PACK)!!
  private val baseIconPack by lazy { iconCache.getIconPack(basePack) }
  private val iconPackAsFallback =
    WorldPreference.getPrefInApp(context)
      .getBoolean(PrefKey.ICON_PACK_AS_FALLBACK, PrefDef.ICON_PACK_AS_FALLBACK)
  var icons = mutableStateMapOf<ComponentName, AppIconInfo>()
    private set

  val expandSearchBar = mutableStateOf(false)
  val searchText = mutableStateOf("")
  val filteredIcons =
    combineTransform(
        snapshotFlow { icons.toMap() },
        snapshotFlow { searchText.value }.debounceInput(),
      ) { icons, text ->
        if (text.isEmpty()) emit(icons)
        else {
          emit(null)
          emit(
            withContext(Dispatchers.Default) {
              icons.filter { (_, value) -> value.label.contains(text, ignoreCase = true) }
            }
          )
        }
      }
      .conflate()

  val variantPack = mutableStateOf(basePack)
  val variantIcons =
    snapshotFlow { variantPack.value }
      .transform { pack ->
        emit(null)
        emit(
          withContext(Dispatchers.IO) {
            iconCache.getIconPack(pack).drawables.map { VariantPackIcon(pack, it) }
          }
        )
      }

  var variantSheet by mutableStateOf(false)
  private val selectedApp = MutableStateFlow<ComponentName?>(null)
  val variantSearchText = mutableStateOf("")
  val suggestVariantIcons =
    combineTransform(
        variantIcons,
        selectedApp,
        snapshotFlow { variantSearchText.value }.debounceInput(),
      ) { icons, cn, text ->
        emit(null)
        icons ?: return@combineTransform
        cn ?: return@combineTransform
        emit(
          mutableListOf<VariantIcon>(OriginalIcon()).apply {
            addAll(
              withContext(Dispatchers.Default) {
                val entry = iconCache.getIconPack(variantPack.value).getIconEntry(cn)
                if (text.isEmpty())
                  if (entry != null) icons.filter { it.name.startsWith(entry.name) } else listOf()
                else icons.filter { it.name.contains(text) }
              }
            )
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
    withContext(Dispatchers.IO) {
      val newIcons = mutableMapOf<ComponentName, AppIconInfo>()
      context
        .getSystemService(Context.LAUNCHER_APPS_SERVICE)
        .asType<LauncherApps>()
        .getActivityList(null, Process.myUserHandle())
        .forEach { info ->
          val cn = info.componentName
          newIcons[cn] =
            AppIconInfo(cn.packageName, info.label.toString(), getUpdatedEntryWithPack(cn))
        }
      icons.putAll(newIcons)
    }
    isLoading = false
  }

  private fun getUpdatedEntryWithPack(cn: ComponentName) =
    iconPackDB.getIcon(basePack, cn, iconPackAsFallback).getFirstRow {
      val entry = IconEntry.from(it.getBlob("entry"))
      val pack = it.getString("pack").ifEmpty { basePack }
      IconEntryWithPack(entry, pack)
    }

  suspend fun loadIcon(info: AppIconInfo) = iconCache.loadIcon(info.entry, info.app, basePack)

  suspend fun loadIconForSelectedApp(icon: VariantIcon) =
    when (icon) {
      is OriginalIcon ->
        selectedApp.value?.packageName?.let { iconCache.loadIcon(null, it, basePack) }
          ?: ImageBitmap(1, 1)
      is VariantPackIcon -> iconCache.loadIcon(icon.name, icon.pack)
      else -> ImageBitmap(1, 1)
    }

  fun openVariantSheet(cn: ComponentName) {
    selectedApp.value = cn
    variantSheet = true
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

  suspend fun replaceIcon(icon: VariantIcon) {
    val cn = selectedApp.value ?: return
    isLoading = true
    withContext(Dispatchers.IO) {
      when (icon) {
        is OriginalIcon -> iconPackDB.deleteIcon(basePack, cn.packageName)
        is VariantPackIcon ->
          iconPackDB.insertOrUpdatePackageIcon(basePack, cn, NormalIconEntry(icon.name), icon.pack)
      }
      icons[cn]?.copy(entry = getUpdatedEntryWithPack(cn))?.let { icons[cn] = it }
    }
    isLoading = false
    variantSheet = false
  }
}
