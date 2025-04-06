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
import com.richardluo.globalIconPack.iconPack.IconPackConfig
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.iconPack.database.IconPackDB
import com.richardluo.globalIconPack.iconPack.database.NormalIconEntry
import com.richardluo.globalIconPack.ui.model.AppIconInfo
import com.richardluo.globalIconPack.ui.model.IconEntryWithPack
import com.richardluo.globalIconPack.ui.model.OriginalIcon
import com.richardluo.globalIconPack.ui.model.ShortcutIconInfo
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

class IconVariantVM(app: Application) : ContextVM(app), IFilterApps by FilterApps(app) {
  private val iconPackCache by getInstance { IconPackCache(app) }
  private val iconCache = IconCache(app) { iconPackCache.getIconPack(it) }
  private val iconPackDB by getInstance { IconPackDB(app) }

  val basePack = WorldPreference.getPrefInApp(app).get(Pref.ICON_PACK)
  private val iconPackConfig = IconPackConfig(WorldPreference.getPrefInApp(app))

  val expandSearchBar = mutableStateOf(false)

  val filteredIcons =
    combineTransform(filteredApps, iconPackDB.iconsUpdateFlow) { apps, _ ->
        if (apps == null) emit(null) else emit(apps.map { it to getIconEntry(it.componentName) })
      }
      .flowOn(Dispatchers.IO)
      .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val modified =
    iconPackDB.modifiedUpdateFlow
      .map { iconPackDB.isPackModified(basePack) }
      .flowOn(Dispatchers.IO)
      .stateIn(viewModelScope, SharingStarted.Lazily, false)

  private fun getIconEntry(cn: ComponentName) =
    iconPackDB.getIcon(basePack, cn, iconPackConfig.iconPackAsFallback).useFirstRow {
      val entry = IconEntry.from(it.getBlob("entry"))
      val pack = it.getString("pack").ifEmpty { basePack }
      val iconPack = iconPackCache.getIconPack(pack)
      iconPack.makeValidEntry(entry)?.let { entry -> IconEntryWithPack(entry, iconPack) }
    }

  suspend fun loadIcon(pair: Pair<AppIconInfo, IconEntryWithPack?>) =
    iconCache.loadIcon(pair.first, pair.second, basePack, iconPackConfig)

  fun restoreDefault() {
    if (basePack.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
      runCatchingToast(context) { iconPackDB.resetPack(basePack) }
    }
  }

  fun flipModified() {
    if (basePack.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
      runCatchingToast(context) { iconPackDB.setPackModified(basePack, !modified.value) }
    }
  }

  fun replaceIcon(info: AppIconInfo, icon: VariantIcon) {
    val cn = info.componentName
    viewModelScope.launch(Dispatchers.IO) {
      runCatchingToast(context) {
        when (icon) {
          is OriginalIcon -> iconPackDB.deleteIcon(basePack, cn.packageName)
          is VariantPackIcon ->
            when (info) {
              is ShortcutIconInfo ->
                iconPackDB.insertOrUpdateShortcutIcon(basePack, cn, icon.entry, icon.pack.pack)
              else -> iconPackDB.insertOrUpdateAppIcon(basePack, cn, icon.entry, icon.pack.pack)
            }
        }
      }
    }
  }

  fun export(uri: Uri) {
    viewModelScope.launch(Dispatchers.Default) {
      runCatchingToast(context) {
        val xml = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><resources>")
        getAllApps().forEach {
          getIconEntry(it.componentName)?.let { entry ->
            if (entry.entry.type == IconEntry.Type.Normal) {
              val cn = it.componentName.flattenToString()
              val name = entry.entry.name
              val pack = if (entry.pack.pack == basePack) "" else entry.pack.pack
              xml.append(
                "<item component=\"$cn\" drawable=\"$name\"${pack.ifNotEmpty { " pack=\"$it\"" }}/>"
              )
            }
            // Can not handle entries of other type
          }
        }
        OutputStreamWriter(context.contentResolver.openOutputStream(uri, "wt")).use {
          it.write(xml.append("</resources>").toString())
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
              when (parser.name) {
                "item" -> {
                  var cn =
                    parser["component"]?.let { ComponentName.unflattenFromString(it) } ?: continue
                  insertOrUpdateAppIcon(
                    basePack,
                    cn,
                    NormalIconEntry(parser["drawable"] ?: continue),
                    parser["pack"] ?: "",
                  )
                }
              }
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
