package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.serialization.saved
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import com.richardluo.globalIconPack.iconPack.model.CalendarIconEntry
import com.richardluo.globalIconPack.ui.model.CompInfo
import com.richardluo.globalIconPack.ui.model.IconPack
import com.richardluo.globalIconPack.ui.model.OriginalIcon
import com.richardluo.globalIconPack.ui.model.VariantIcon
import com.richardluo.globalIconPack.ui.model.VariantPackIcon
import com.richardluo.globalIconPack.utils.ContextVM
import com.richardluo.globalIconPack.utils.SingletonManager.get
import com.richardluo.globalIconPack.utils.filter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform

@OptIn(SavedStateHandleSaveableApi::class)
class IconChooserVM(context: Application, savedStateHandle: SavedStateHandle) : ContextVM(context) {
  private val iconPackCache by get { IconPackCache(context) }
  private val iconCache by get { IconCache(context) }

  var variantSheet by savedStateHandle.saveable { mutableStateOf(false) }
  var iconPack by savedStateHandle.saveable { mutableStateOf<IconPack?>(null) }
    private set

  var compInfo by savedStateHandle.saveable { mutableStateOf<CompInfo?>(null) }
    private set

  private var suggestHint by savedStateHandle.saved { "" }

  val icons =
    snapshotFlow { iconPack }
      .transform { iconPack ->
        iconPack ?: return@transform
        emit(null)
        emit(iconPack.drawables.map { VariantPackIcon(iconPack, it) })
      }
      .flowOn(Dispatchers.IO)
      .stateIn(viewModelScope, SharingStarted.Lazily, null)

  val suggestIcons =
    combineTransform(icons, snapshotFlow { compInfo }) { icons, iconInfo ->
        emit(null)
        icons ?: return@combineTransform
        iconInfo ?: return@combineTransform

        val keyword =
          suggestHint.takeIf { it.isNotEmpty() }?.substringBeforeLast("_")
            ?: iconInfo.componentName.packageName.let {
              it.substringAfterLast(".").takeIf { it.length > 3 } ?: it
            }

        emit(
          buildList {
            add(OriginalIcon())
            icons.filterTo(this) { it.entry.name.contains(keyword, ignoreCase = true) }
          }
        )
      }
      .flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.Lazily, null)

  val searchText = mutableStateOf("")
  val filteredIcons =
    icons
      .filter(snapshotFlow { searchText.value }) { icon, text ->
        icon.entry.name.contains(text, ignoreCase = true)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

  suspend fun loadIcon(icon: VariantPackIcon) = iconCache.loadIcon(icon.entry, icon.pack)

  fun setPack(pack: String) {
    this.iconPack = iconPackCache[pack]
  }

  fun open(compInfo: CompInfo, iconPack: IconPack, suggestHint: String? = null) {
    this.compInfo = compInfo
    this.iconPack = iconPack
    this.suggestHint = suggestHint.orEmpty()
    variantSheet = true
  }

  class NotCalendarEntryException : Exception("Not a calendar entry")

  fun asCalendarEntry(icon: VariantIcon): VariantPackIcon {
    val iconPack = iconPack ?: throw NotCalendarEntryException()
    icon as? VariantPackIcon ?: throw NotCalendarEntryException()
    val name = icon.entry.name

    // Detect if it is in icon pack, 15 is just a random number
    if (iconPack.getIcon("${name}_15") != null)
      return VariantPackIcon(icon.pack, CalendarIconEntry("${name}_"))

    val calendarPrefix = name.removeSuffix(name.takeLastWhile { it.isDigit() })
    if (iconPack.getIcon("${calendarPrefix}15") != null)
      return VariantPackIcon(icon.pack, CalendarIconEntry(calendarPrefix))

    throw NotCalendarEntryException()
  }
}
