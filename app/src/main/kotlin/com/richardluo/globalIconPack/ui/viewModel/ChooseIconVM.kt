package com.richardluo.globalIconPack.ui.viewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import com.richardluo.globalIconPack.iconPack.CopyableIconPack
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.iconPack.database.NormalIconEntry
import com.richardluo.globalIconPack.utils.debounceInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext

interface VariantIcon

class VariantPackIcon(val pack: CopyableIconPack, val entry: IconEntry) : VariantIcon

class OriginalIcon : VariantIcon

class ChooseIconVM(private val iconCache: IconCache, private val getBasePack: () -> String) {
  val variantPack = mutableStateOf(getBasePack())
  val variantIcons =
    snapshotFlow { variantPack.value }
      .transform { pack ->
        if (variantPack.value.isEmpty()) return@transform
        emit(null)
        emit(
          withContext(Dispatchers.IO) {
            val iconPack = iconCache.getIconPack(pack)
            iconPack.drawables.map { VariantPackIcon(iconPack, NormalIconEntry(it)) }
          }
        )
      }

  var variantSheet by mutableStateOf(false)
  val selectedApp = MutableStateFlow<AppIconInfo?>(null)
  val variantSearchText = mutableStateOf("")
  val suggestVariantIcons =
    combineTransform(
        variantIcons,
        selectedApp,
        snapshotFlow { variantSearchText.value }.debounceInput(),
      ) { icons, app, text ->
        emit(null)
        icons ?: return@combineTransform
        app ?: return@combineTransform
        emit(
          mutableListOf<VariantIcon>(OriginalIcon()).apply {
            addAll(
              withContext(Dispatchers.Default) {
                val iconEntry =
                  iconCache.getIconPack(variantPack.value).getIconEntry(app.componentName)
                if (text.isEmpty())
                  if (iconEntry != null) icons.filter { it.entry.name.startsWith(iconEntry.name) }
                  else listOf()
                else icons.filter { it.entry.name.contains(text, ignoreCase = true) }
              }
            )
          }
        )
      }
      .conflate()

  suspend fun loadIconForSelectedApp(icon: VariantIcon) =
    when (icon) {
      is OriginalIcon ->
        getBasePack()
          .takeIf { it.isNotEmpty() }
          ?.let { basePack ->
            selectedApp.value?.let {
              when (it) {
                is ShortcutIconInfo -> iconCache.loadIcon(it, basePack)
                else -> iconCache.loadIcon(it, basePack)
              }
            }
          }
      is VariantPackIcon -> iconCache.loadIcon(icon.entry, icon.pack)
      else -> null
    } ?: ImageBitmap(1, 1)

  fun openVariantSheet(entry: AppIconInfo) {
    selectedApp.value = entry
    variantSheet = true
  }
}
