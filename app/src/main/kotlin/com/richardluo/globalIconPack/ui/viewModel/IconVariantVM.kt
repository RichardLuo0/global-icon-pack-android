package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.util.Xml
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.iconPack.IconFallback
import com.richardluo.globalIconPack.iconPack.IconPackConfig
import com.richardluo.globalIconPack.iconPack.database.CalendarIconEntry
import com.richardluo.globalIconPack.iconPack.database.FallbackSettings
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.iconPack.database.IconPackDB
import com.richardluo.globalIconPack.iconPack.database.NormalIconEntry
import com.richardluo.globalIconPack.ui.model.IconEntryWithPack
import com.richardluo.globalIconPack.ui.model.IconInfo
import com.richardluo.globalIconPack.ui.model.OriginalIcon
import com.richardluo.globalIconPack.ui.model.VariantIcon
import com.richardluo.globalIconPack.ui.model.VariantPackIcon
import com.richardluo.globalIconPack.utils.ContextVM
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.get
import com.richardluo.globalIconPack.utils.getBlob
import com.richardluo.globalIconPack.utils.getInstance
import com.richardluo.globalIconPack.utils.getString
import com.richardluo.globalIconPack.utils.ifNotEmpty
import com.richardluo.globalIconPack.utils.runCatchingToast
import com.richardluo.globalIconPack.utils.useFirstRow
import java.io.OutputStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class IconVariantVM(context: Application) : ContextVM(context), IFilterApps by FilterApps(context) {
  private val iconPackCache by getInstance { IconPackCache(context) }
  private val iconCache by getInstance { IconCache(context) }
  private val fallbackIconCache = IconCache(context, 1.0 / 16)
  private val iconPackDB by getInstance { IconPackDB(context) }

  val iconPack = iconPackCache.get(WorldPreference.getPrefInApp(context).get(Pref.ICON_PACK))
  private val pack
    get() = iconPack.pack

  private val iconFallback =
    iconPackDB
      .getFallbackSettings(pack)
      .useFirstRow { FallbackSettings.from(it.getBlob("fallback")) }
      ?.let { IconFallback(it, iconPack::getIcon, null, Pref.SCALE_ONLY_FOREGROUND.second) }
      ?.orNullIfEmpty()
  private val iconPackConfig = IconPackConfig(WorldPreference.getPrefInApp(context))

  val expandSearchBar = mutableStateOf(false)

  val filteredIcons =
    combineTransform(filteredApps, iconPackDB.iconsUpdateFlow) { apps, _ ->
        if (apps == null) emit(null) else emit(apps.map { it to getIconEntry(it.componentName) })
      }
      .flowOn(Dispatchers.IO)
      .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val modified =
    iconPackDB.modifiedUpdateFlow
      .map { iconPackDB.isPackModified(pack) }
      .flowOn(Dispatchers.IO)
      .stateIn(viewModelScope, SharingStarted.Lazily, false)

  private fun getIconEntry(cn: ComponentName) =
    iconPackDB.getIcon(pack, cn, iconPackConfig.iconPackAsFallback).useFirstRow {
      val entry = IconEntry.from(it.getBlob("entry"))
      val pack = it.getString("pack").ifEmpty { pack }
      val iconPack = iconPackCache.get(pack)
      iconPack.makeValidEntry(entry)?.let { entry -> IconEntryWithPack(entry, iconPack) }
    }

  suspend fun loadIcon(pair: Pair<IconInfo, IconEntryWithPack?>) =
    if (pair.second != null) iconCache.loadIcon(pair.first, pair.second, iconPack, iconPackConfig)
    else fallbackIconCache.loadIcon(pair.first, iconFallback, iconPack, iconPackConfig)

  fun restoreDefault() =
    viewModelScope.launch(Dispatchers.IO) {
      runCatchingToast(context) { iconPackDB.resetPack(iconPack) }
    }

  fun flipModified() =
    viewModelScope.launch(Dispatchers.IO) {
      runCatchingToast(context) { iconPackDB.setPackModified(pack, !modified.value) }
    }

  fun replaceIcon(info: IconInfo, icon: VariantIcon) {
    val pack = pack
    val cn = info.componentName
    viewModelScope.launch(Dispatchers.IO) {
      runCatchingToast(context) {
        when (icon) {
          is OriginalIcon -> iconPackDB.deleteIcon(pack, cn)
          is VariantPackIcon -> iconPackDB.insertOrUpdateIcon(pack, cn, icon.entry, icon.pack.pack)
        }
      }
    }
  }

  fun export(uri: Uri) {
    viewModelScope.launch(Dispatchers.Default) {
      runCatchingToast(context) {
        val xml = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><resources>\n")
        getAllApps().forEach {
          getIconEntry(it.componentName)?.let { entry ->
            val cn = it.componentName.flattenToString()
            val name = entry.entry.name
            val pack = if (entry.pack.pack == pack) "" else entry.pack.pack
            when (entry.entry.type) {
              IconEntry.Type.Normal ->
                xml.append(
                  "<item component=\"$cn\" drawable=\"$name\"${pack.ifNotEmpty { " pack=\"$it\"" }}/>\n"
                )
              IconEntry.Type.Calendar ->
                xml.append(
                  "<calendar component=\"$cn\" prefix=\"$name\"${pack.ifNotEmpty { " pack=\"$it\"" }}/>\n"
                )
              // Can not handle entries of other type
              else -> {}
            }
          }
        }
        OutputStreamWriter(context.contentResolver.openOutputStream(uri, "wt")).use {
          it.write(xml.append("</resources>\n").toString())
        }
        withContext(Dispatchers.Main) {
          Toast.makeText(context, R.string.exportedIconPack, Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  fun import(uri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatchingToast(context) {
        context.contentResolver.openInputStream(uri).use {
          val parser =
            XmlPullParserFactory.newInstance().newPullParser().apply {
              setInput(it, Xml.Encoding.UTF_8.toString())
            }
          iconPackDB.transaction {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
              if (parser.eventType != XmlPullParser.START_TAG) continue
              var cn =
                parser["component"]?.let { ComponentName.unflattenFromString(it) } ?: continue
              val entry =
                when (parser.name) {
                  "item" -> NormalIconEntry(parser["drawable"] ?: continue)
                  "calendar" -> CalendarIconEntry(parser["prefix"] ?: continue)
                  else -> continue
                }
              insertOrUpdateIcon(pack, cn, entry, parser["pack"] ?: "")
            }
          }
        }
        withContext(Dispatchers.Main) {
          Toast.makeText(context, R.string.importedIconPack, Toast.LENGTH_LONG).show()
        }
      }
    }
  }
}
