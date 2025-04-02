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
import com.richardluo.globalIconPack.utils.getFirstRow
import com.richardluo.globalIconPack.utils.getInstance
import com.richardluo.globalIconPack.utils.getString
import com.richardluo.globalIconPack.utils.ifNotEmpty
import com.richardluo.globalIconPack.utils.runCatchingToast
import java.io.OutputStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class IconVariantVM(app: Application) : ContextVM(app) {
  private val iconPackCache by getInstance { IconPackCache(app) }
  private val iconCache = IconCache(app) { iconPackCache.getIconPack(it) }
  private val iconPackDB by getInstance { IconPackDB(app) }

  val basePack = WorldPreference.getPrefInApp(app).get(Pref.ICON_PACK)
  private val iconPackConfig = IconPackConfig(WorldPreference.getPrefInApp(app))

  val expandSearchBar = mutableStateOf(false)

  val filterAppsVM = FilterAppsVM(context)
  val filteredIcons =
    combineTransform(filterAppsVM.filteredApps, iconPackDB.iconsUpdateFlow) { apps, _ ->
        if (apps == null) emit(null)
        else emit(withContext(Dispatchers.IO) { apps.map { it to getIconEntry(it.componentName) } })
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val chooseIconVM = ChooseIconVM(basePack, { iconPackCache.getIconPack(it) }, { loadIcon(it) })

  val modified =
    iconPackDB.modifiedUpdateFlow
      .map { withContext(Dispatchers.IO) { iconPackDB.isPackModified(basePack) } }
      .stateIn(viewModelScope, SharingStarted.Lazily, false)

  private fun getIconEntry(cn: ComponentName) =
    iconPackDB.getIcon(basePack, cn, iconPackConfig.iconPackAsFallback).getFirstRow {
      val entry = IconEntry.from(it.getBlob("entry"))
      val pack = it.getString("pack").ifEmpty { basePack }
      val iconPack = iconPackCache.getIconPack(pack)
      iconPack.makeValidEntry(entry)?.let { entry -> IconEntryWithPack(entry, iconPack) }
    }

  suspend fun loadIcon(pair: Pair<AppIconInfo, IconEntryWithPack?>) =
    iconCache.loadIcon(pair.first, pair.second, basePack, iconPackConfig)

  fun restoreDefault() {
    if (basePack.isEmpty()) return
    viewModelScope.launch {
      runCatchingToast(context) { withContext(Dispatchers.IO) { iconPackDB.resetPack(basePack) } }
    }
  }

  fun flipModified() {
    if (basePack.isEmpty()) return
    viewModelScope.launch {
      runCatchingToast(context) {
        withContext(Dispatchers.IO) { iconPackDB.setPackModified(basePack, !modified.value) }
      }
    }
  }

  fun replaceIcon(icon: VariantIcon) {
    val info = chooseIconVM.selectedApp.value ?: return
    val cn = info.componentName
    viewModelScope.launch {
      runCatchingToast(context) {
        withContext(Dispatchers.IO) {
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
  }

  fun export(uri: Uri) {
    viewModelScope.launch {
      runCatchingToast(context) {
        withContext(Dispatchers.Default) {
          val xml = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><resources>")
          filterAppsVM.getAllApps().forEach {
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
          OutputStreamWriter(context.contentResolver.openOutputStream(uri)).use {
            it.write(xml.append("</resources>").toString())
          }
        }
        Toast.makeText(context, R.string.exportedIconPack, Toast.LENGTH_LONG).show()
      }
    }
  }

  fun import(uri: Uri) {
    viewModelScope.launch {
      runCatchingToast(context) {
        withContext(Dispatchers.IO) {
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
        }
        Toast.makeText(context, R.string.importedIconPack, Toast.LENGTH_LONG).show()
      }
    }
  }
}
