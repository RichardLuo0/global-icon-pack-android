package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.richardluo.globalIconPack.iconPack.database.CalendarIconEntry
import com.richardluo.globalIconPack.iconPack.database.NormalIconEntry
import com.richardluo.globalIconPack.ui.model.IconInfo
import com.richardluo.globalIconPack.ui.model.IconPack
import com.richardluo.globalIconPack.ui.model.OriginalIcon
import com.richardluo.globalIconPack.ui.model.VariantIcon
import com.richardluo.globalIconPack.ui.model.VariantPackIcon
import com.richardluo.globalIconPack.utils.ContextVM
import com.richardluo.globalIconPack.utils.debounceInput
import com.richardluo.globalIconPack.utils.getInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform

class IconChooserVM(context: Application) : ContextVM(context) {
  private val iconPackCache by getInstance { IconPackCache(context) }
  private val iconCache by getInstance { IconCache(context) }

  var variantSheet by mutableStateOf(false)
  var iconPack by mutableStateOf<IconPack?>(null)
  var iconInfo by mutableStateOf<IconInfo?>(null)
    private set

  val icons =
    snapshotFlow { iconPack }
      .transform { pack ->
        val iconPack = iconPack
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
                appInfo.componentName.packageName.let {
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
    this.iconPack = iconPackCache.get(pack)
  }

  fun open(iconInfo: IconInfo, iconPack: IconPack) {
    this.iconInfo = iconInfo
    this.iconPack = iconPack
    variantSheet = true
  }

  class NotCalendarEntryException : Exception("Not a calendar entry")

  fun asCalendarEntry(icon: VariantIcon): VariantPackIcon {
    icon as? VariantPackIcon ?: throw NotCalendarEntryException()
    val name = icon.entry.name
    val calendarPrefix = name.removeSuffix(name.takeLastWhile { it.isDigit() })
    // Detect if it is in icon pack, 15 is just a random number
    iconPack?.getIcon("${calendarPrefix}15") ?: throw NotCalendarEntryException()
    return VariantPackIcon(icon.pack, CalendarIconEntry(calendarPrefix))
  }
}
