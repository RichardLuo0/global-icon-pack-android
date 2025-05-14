package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.util.Xml
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.iconPack.IconPackDB
import com.richardluo.globalIconPack.iconPack.IconPackDB.GetAllIconsCol
import com.richardluo.globalIconPack.iconPack.IconPackDB.GetIconCol
import com.richardluo.globalIconPack.iconPack.getBlob
import com.richardluo.globalIconPack.iconPack.getInt
import com.richardluo.globalIconPack.iconPack.getString
import com.richardluo.globalIconPack.iconPack.model.CalendarIconEntry
import com.richardluo.globalIconPack.iconPack.model.FallbackSettings
import com.richardluo.globalIconPack.iconPack.model.IconEntry
import com.richardluo.globalIconPack.iconPack.model.IconFallback
import com.richardluo.globalIconPack.iconPack.model.IconPackConfig
import com.richardluo.globalIconPack.iconPack.model.NormalIconEntry
import com.richardluo.globalIconPack.iconPack.model.defaultIconPackConfig
import com.richardluo.globalIconPack.iconPack.useEachRow
import com.richardluo.globalIconPack.iconPack.useFirstRow
import com.richardluo.globalIconPack.iconPack.useMapToArray
import com.richardluo.globalIconPack.ui.IconsHolder
import com.richardluo.globalIconPack.ui.model.AppIconInfo
import com.richardluo.globalIconPack.ui.model.IconEntryWithPack
import com.richardluo.globalIconPack.ui.model.IconInfo
import com.richardluo.globalIconPack.ui.model.OriginalIcon
import com.richardluo.globalIconPack.ui.model.VariantIcon
import com.richardluo.globalIconPack.ui.model.VariantPackIcon
import com.richardluo.globalIconPack.utils.ContextVM
import com.richardluo.globalIconPack.utils.InstanceManager.get
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.debounceInput
import com.richardluo.globalIconPack.utils.filter
import com.richardluo.globalIconPack.utils.get
import com.richardluo.globalIconPack.utils.ifNotEmpty
import com.richardluo.globalIconPack.utils.runCatchingToast
import com.richardluo.globalIconPack.utils.unflattenFromString
import java.io.OutputStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class IconVariantVM(context: Application) :
  ContextVM(context), IAppsFilter by AppsFilter(), IconsHolder {
  private val iconPackCache by get { IconPackCache(context) }
  private val iconCache by get { IconCache(context) }
  private val fallbackIconCache = IconCache(context, 1.0 / 16)
  private val iconPackDB by get { IconPackDB(context) }

  val iconPack = iconPackCache[WorldPreference.getPrefInApp(context).get(Pref.ICON_PACK)]
  val pack
    get() = iconPack.pack

  private val iconFallback =
    iconPackDB
      .getFallbackSettings(pack)
      .useFirstRow { FallbackSettings.from(it.getBlob(0)) }
      ?.let { IconFallback(it, iconPack::getIcon, defaultIconPackConfig) }
      ?.orNullIfEmpty()
  private val iconPackConfig = IconPackConfig(WorldPreference.getPrefInApp(context))

  val searchText = mutableStateOf("")

  private val iconsByType =
    combine(appsByType, iconPackDB.iconsUpdateFlow) { apps, _ ->
        apps.zip(getIconEntry(apps.map { it.componentName }))
      }
      .flowOn(Dispatchers.IO)
      .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val filteredIcons =
    iconsByType
      .filter(snapshotFlow { searchText.value }.debounceInput()) { (info), text ->
        info.label.contains(text, ignoreCase = true)
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val appIconListVM =
    AppIconListVM(context, viewModelScope, iconPackDB.iconsUpdateFlow, ::getIconEntry)

  override fun getCurrentIconPack() = iconPack

  fun setupActivityList(appIconInfo: AppIconInfo) {
    appIconListVM.setup(appIconInfo)
  }

  val modified =
    iconPackDB.modifiedUpdateFlow
      .map { iconPackDB.isPackModified(pack) }
      .flowOn(Dispatchers.IO)
      .stateIn(viewModelScope, SharingStarted.Lazily, false)

  fun getIconEntry(cnList: List<ComponentName>) =
    iconPackDB
      .getIcon(pack, cnList, iconPackConfig.iconPackAsFallback)
      .useMapToArray(cnList.size) { _, c ->
        val entry = IconEntry.from(c.getBlob(GetIconCol.Entry))
        val pack = c.getString(GetIconCol.Pack)
        val id = c.getInt(GetIconCol.Id).takeIf { it != 0 }
        val iconPack = if (pack.isNotEmpty()) iconPackCache[pack] else iconPack
        iconPack.makeValidEntry(entry, id)?.let { IconEntryWithPack(it, iconPack) }
      }
      .asList()

  override suspend fun loadIcon(pair: Pair<IconInfo, IconEntryWithPack?>) =
    if (pair.second != null) iconCache.loadIcon(pair.first, pair.second, iconPack, iconPackConfig)
    else fallbackIconCache.loadIcon(pair.first, iconFallback, iconPack, iconPackConfig)

  fun restoreDefault() =
    viewModelScope.launch(Dispatchers.IO) {
      runCatchingToast(context) { iconPackDB.resetPack(iconPack, IconPackApps.get().keys) }
    }

  fun flipModified() =
    viewModelScope.launch(Dispatchers.IO) {
      runCatchingToast(context) { iconPackDB.setPackModified(pack, !modified.value) }
    }

  override fun saveIcon(info: IconInfo, icon: VariantIcon) {
    val pack = pack
    val cn = info.componentName
    viewModelScope.launch(Dispatchers.IO) {
      runCatchingToast(context) {
        when (icon) {
          is OriginalIcon -> iconPackDB.deleteIcon(pack, cn)
          is VariantPackIcon -> iconPackDB.insertOrUpdateIcon(pack, cn, icon.entry, icon.pack)
        }
      }
    }
  }

  fun export(uri: Uri) =
    viewModelScope.async(Dispatchers.Default) {
      runCatchingToast(context) {
        val xml = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><resources>\n")
        iconPackDB.getAllIcons(pack, getAllAppsAndShortcuts()).useEachRow { c ->
          val entry = IconEntry.from(c.getBlob(GetAllIconsCol.Entry))
          val cn =
            ComponentName(
                c.getString(GetAllIconsCol.PackageName),
                c.getString(GetAllIconsCol.ClassName),
              )
              .flattenToString()
          val name = entry.name
          val pack = c.getString(GetAllIconsCol.Pack)
          when (entry.type) {
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
        OutputStreamWriter(context.contentResolver.openOutputStream(uri, "wt")).use {
          it.write(xml.append("</resources>\n").toString())
        }
        withContext(Dispatchers.Main) {
          Toast.makeText(context, R.string.exportedIconPack, Toast.LENGTH_LONG).show()
        }
      }
    }

  fun import(uri: Uri) =
    viewModelScope.async(Dispatchers.IO) {
      runCatchingToast(context) {
        context.contentResolver.openInputStream(uri).use {
          val parser = XmlPullParserFactory.newInstance().newPullParser()
          parser.setInput(it, Xml.Encoding.UTF_8.toString())
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
              insertOrUpdateIcon(
                pack,
                cn,
                entry,
                parser["pack"]?.let { iconPackCache[it] } ?: iconPack,
              )
            }
          }
        }
        withContext(Dispatchers.Main) {
          Toast.makeText(context, R.string.importedIconPack, Toast.LENGTH_LONG).show()
        }
      }
    }
}
