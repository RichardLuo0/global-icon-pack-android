package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.viewModelScope
import com.richardluo.globalIconPack.iconPack.IconPackConfig
import com.richardluo.globalIconPack.iconPack.database.NormalIconEntry
import com.richardluo.globalIconPack.ui.model.AppIconInfo
import com.richardluo.globalIconPack.ui.model.IconEntryWithPack
import com.richardluo.globalIconPack.ui.model.OriginalIcon
import com.richardluo.globalIconPack.ui.model.VariantIcon
import com.richardluo.globalIconPack.ui.model.VariantPackIcon
import com.richardluo.globalIconPack.utils.ContextVM
import com.richardluo.globalIconPack.utils.debounceInput
import com.richardluo.globalIconPack.utils.getInstance
import kotlin.getValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform

class IconChooserVM(app: Application) : ContextVM(app) {
  private val iconPackCache by getInstance { IconPackCache(app) }
  private val iconCache = IconCache(app) { iconPackCache.getIconPack(it) }

  var variantSheet by mutableStateOf(false)
  var pack by mutableStateOf("")
  var appInfo by mutableStateOf<AppIconInfo?>(null)
    private set

  val icons =
    snapshotFlow { pack }
      .transform { pack ->
        if (pack.isEmpty()) return@transform
        emit(null)
        val iconPack = iconPackCache.getIconPack(pack)
        emit(iconPack.drawables.map { VariantPackIcon(iconPack, NormalIconEntry(it)) })
      }
      .flowOn(Dispatchers.IO)
      .stateIn(viewModelScope, SharingStarted.Lazily, null)

  val searchText = mutableStateOf("")
  val suggestIcons =
    combineTransform(
        icons,
        snapshotFlow { appInfo },
        snapshotFlow { searchText.value }.debounceInput(),
      ) { icons, app, text ->
        emit(null)
        icons ?: return@combineTransform
        app ?: return@combineTransform
        emit(
          mutableListOf<VariantIcon>(OriginalIcon()).apply {
            val iconEntry = iconPackCache.getIconPack(pack).getIconEntry(app.componentName, true)
            addAll(
              if (text.isEmpty())
                if (iconEntry != null) icons.filter { it.entry.name.startsWith(iconEntry.name) }
                else listOf()
              else icons.filter { it.entry.name.contains(text, ignoreCase = true) }
            )
          }
        )
      }
      .conflate()
      .flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.Lazily, null)

  suspend fun loadIcon(pair: Pair<AppIconInfo, IconEntryWithPack?>) =
    iconCache.loadIcon(pair.first, pair.second, pack, defaultIconPackConfig)

  suspend fun loadIconForSelectedApp(icon: VariantIcon): ImageBitmap {
    val appInfo = appInfo ?: return ImageBitmap(1, 1)
    return when (icon) {
      is OriginalIcon -> loadIcon(appInfo to null)
      is VariantPackIcon -> loadIcon(appInfo to IconEntryWithPack(icon.entry, icon.pack))
      else -> null
    } ?: ImageBitmap(1, 1)
  }

  fun open(appInfo: AppIconInfo, pack: String) {
    this.appInfo = appInfo
    this.pack = pack
    variantSheet = true
  }
}
