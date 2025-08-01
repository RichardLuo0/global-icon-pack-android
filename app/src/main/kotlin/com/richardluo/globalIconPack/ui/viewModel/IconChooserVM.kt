package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.richardluo.globalIconPack.iconPack.model.CalendarIconEntry
import com.richardluo.globalIconPack.iconPack.model.NormalIconEntry
import com.richardluo.globalIconPack.ui.model.IconInfo
import com.richardluo.globalIconPack.ui.model.IconPack
import com.richardluo.globalIconPack.ui.model.OriginalIcon
import com.richardluo.globalIconPack.ui.model.VariantIcon
import com.richardluo.globalIconPack.ui.model.VariantPackIcon
import com.richardluo.globalIconPack.utils.ContextVM
import com.richardluo.globalIconPack.utils.InstanceManager.get
import com.richardluo.globalIconPack.utils.debounceInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform

class IconChooserVM(context: Application) : ContextVM(context) {
  private val iconPackCache by get { IconPackCache(context) }
  private val iconCache by get { IconCache(context) }

  var variantSheet by mutableStateOf(false)
  var iconPack by mutableStateOf<IconPack?>(null)
  var iconInfo by mutableStateOf<IconInfo?>(null)
    private set

  private var suggestHint: String? = null

  val icons =
    snapshotFlow { iconPack }
      .transform { iconPack ->
        iconPack ?: return@transform
        emit(null)
        emit(iconPack.drawables.map { VariantPackIcon(iconPack, NormalIconEntry(it)) })
      }
      .flowOn(Dispatchers.IO)
      .stateIn(viewModelScope, SharingStarted.Lazily, null)

  val searchText = mutableStateOf("")
  val suggestIcons =
    combineTransform(
        icons,
        snapshotFlow { iconInfo },
        snapshotFlow { searchText.value }.debounceInput(),
      ) { icons, appInfo, text ->
        emit(null)
        icons ?: return@combineTransform
        appInfo ?: return@combineTransform
        emit(
          mutableListOf<VariantIcon>(OriginalIcon()).apply {
            val keyword =
              text.ifEmpty {
                suggestHint?.substringBeforeLast("_")
                  ?: appInfo.componentName.packageName.let {
                    it.substringAfterLast(".").takeIf { it.length > 3 } ?: it
                  }
              }
            addAll(icons.filter { it.entry.name.contains(keyword, ignoreCase = true) })
          }
        )
      }
      .conflate()
      .flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.Lazily, null)

  suspend fun loadIcon(icon: VariantPackIcon) = iconCache.loadIcon(icon.entry, icon.pack)

  fun setPack(pack: String) {
    this.iconPack = iconPackCache[pack]
  }

  fun open(iconInfo: IconInfo, iconPack: IconPack, suggestHint: String? = null) {
    this.iconInfo = iconInfo
    this.iconPack = iconPack
    this.suggestHint = suggestHint
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
