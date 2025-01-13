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
import com.richardluo.globalIconPack.PrefKey
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.iconPack.database.IconPackDB
import com.richardluo.globalIconPack.iconPack.database.NormalIconEntry
import com.richardluo.globalIconPack.utils.IconPackCreator.IconEntryWithPack
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.debounceTextField
import com.richardluo.globalIconPack.utils.getBlob
import com.richardluo.globalIconPack.utils.getFirstRow
import com.richardluo.globalIconPack.utils.getInstance
import kotlin.collections.set
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IconVariantVM(app: Application) : AndroidViewModel(app) {
  private val context: Context
    get() = getApplication()

  private val iconCache by getInstance { IconCache(app) }
  private val iconPackDB by getInstance { IconPackDB(app) }
  var isLoading by mutableStateOf(false)
    private set

  private val basePack = WorldPreference.getPrefInApp(context).getString(PrefKey.ICON_PACK, "")!!
  private val baseIconPack by lazy { iconCache.getIconPack(basePack) }
  private val iconPackAsFallback =
    WorldPreference.getPrefInApp(context).getBoolean(PrefKey.ICON_PACK_AS_FALLBACK, false)
  var icons = mutableStateMapOf<ComponentName, AppIconInfo>()
    private set

  val expandSearchBar = mutableStateOf(false)
  val searchText = mutableStateOf("")
  val filteredIcons =
    combineTransform(
        snapshotFlow { icons.toMap() },
        snapshotFlow { searchText.value }.debounceTextField(),
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

  private val variantIcons = flow {
    emit(null)
    emit(withContext(Dispatchers.Default) { baseIconPack.getDrawables() })
  }

  var variantSheet by mutableStateOf(false)
  private val selectedApp = MutableStateFlow<ComponentName?>(null)
  val variantSearchText = mutableStateOf("")
  val suggestVariantIcons =
    combineTransform(
        variantIcons,
        selectedApp,
        snapshotFlow { variantSearchText.value }.debounceTextField(),
      ) { icons, cn, text ->
        emit(null)
        emit(
          mutableListOf("").apply {
            addAll(
              withContext(Dispatchers.Default) {
                icons ?: return@withContext listOf()
                cn ?: return@withContext listOf()
                val entry = baseIconPack.getIconEntry(cn)
                if (text.isEmpty())
                  if (entry != null) icons.filter { it.startsWith(entry.name) } else listOf()
                else icons.filter { it.contains(text) }
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
    iconPackDB
      .getIcon(basePack, cn, iconPackAsFallback)
      .getFirstRow { IconEntry.from(it.getBlob("entry")) }
      ?.let { IconEntryWithPack(it, basePack) }

  suspend fun loadIcon(info: AppIconInfo) = iconCache.loadIcon(info.entry, info.app, basePack)

  suspend fun loadIcon(iconName: String) =
    if (iconName.isEmpty())
      selectedApp.value?.packageName?.let { iconCache.loadIcon(null, it, basePack) }
        ?: ImageBitmap(1, 1)
    else iconCache.loadIcon(iconName, basePack)

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

  suspend fun replaceIcon(iconName: String) {
    val cn = selectedApp.value ?: return
    isLoading = true
    withContext(Dispatchers.IO) {
      val entry = NormalIconEntry(iconName)
      if (iconName.isEmpty()) iconPackDB.deleteIcon(basePack, cn.packageName)
      else iconPackDB.insertOrUpdateIcon(basePack, cn.packageName, entry)
      icons[cn]?.copy(entry = getUpdatedEntryWithPack(cn))?.let { icons[cn] = it }
    }
    isLoading = false
    variantSheet = false
  }
}
