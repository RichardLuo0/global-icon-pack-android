package com.richardluo.globalIconPack.ui.viewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import com.richardluo.globalIconPack.iconPack.database.NormalIconEntry
import com.richardluo.globalIconPack.ui.model.AppIconInfo
import com.richardluo.globalIconPack.ui.model.IconEntryWithPack
import com.richardluo.globalIconPack.ui.model.IconPack
import com.richardluo.globalIconPack.ui.model.OriginalIcon
import com.richardluo.globalIconPack.ui.model.VariantIcon
import com.richardluo.globalIconPack.ui.model.VariantPackIcon
import com.richardluo.globalIconPack.utils.debounceInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext

class ChooseIconVM(
  basePack: String,
  getIconPack: (String) -> IconPack,
  private val loadIcon: suspend (Pair<AppIconInfo, IconEntryWithPack?>) -> ImageBitmap,
) {
  val pack = mutableStateOf(basePack)
  val icons =
    snapshotFlow { pack.value }
      .transform { pack ->
        if (pack.isEmpty()) return@transform
        emit(null)
        emit(
          withContext(Dispatchers.IO) {
            val iconPack = getIconPack(pack)
            iconPack.drawables.map { VariantPackIcon(iconPack, NormalIconEntry(it)) }
          }
        )
      }

  var variantSheet by mutableStateOf(false)
  val selectedApp = MutableStateFlow<AppIconInfo?>(null)
  val searchText = mutableStateOf("")
  val suggestIcons =
    combineTransform(icons, selectedApp, snapshotFlow { searchText.value }.debounceInput()) {
        icons,
        app,
        text ->
        emit(null)
        icons ?: return@combineTransform
        app ?: return@combineTransform
        emit(
          mutableListOf<VariantIcon>(OriginalIcon()).apply {
            withContext(Dispatchers.Default) {
              val iconEntry = getIconPack(pack.value).getIconEntry(app.componentName, true)
              addAll(
                if (text.isEmpty())
                  if (iconEntry != null) icons.filter { it.entry.name.startsWith(iconEntry.name) }
                  else listOf()
                else icons.filter { it.entry.name.contains(text, ignoreCase = true) }
              )
            }
          }
        )
      }
      .conflate()

  suspend fun loadIconForSelectedApp(icon: VariantIcon) =
    when (icon) {
      is OriginalIcon -> selectedApp.value?.let { loadIcon(it to null) }
      is VariantPackIcon ->
        selectedApp.value?.let { loadIcon(it to IconEntryWithPack(icon.entry, icon.pack)) }
      else -> null
    } ?: ImageBitmap(1, 1)

  fun openVariantSheet(entry: AppIconInfo) {
    selectedApp.value = entry
    variantSheet = true
  }
}
