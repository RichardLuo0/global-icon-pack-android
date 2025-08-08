package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.util.Xml
import android.widget.Toast
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
import com.richardluo.globalIconPack.ui.model.AnyCompIcon
import com.richardluo.globalIconPack.ui.model.AppCompInfo
import com.richardluo.globalIconPack.ui.model.CompInfo
import com.richardluo.globalIconPack.ui.model.IconEntryWithPack
import com.richardluo.globalIconPack.ui.model.OriginalIcon
import com.richardluo.globalIconPack.ui.model.VariantIcon
import com.richardluo.globalIconPack.ui.model.VariantPackIcon
import com.richardluo.globalIconPack.ui.model.to
import com.richardluo.globalIconPack.ui.repo.Apps
import com.richardluo.globalIconPack.utils.ContextVM
import com.richardluo.globalIconPack.utils.ILoadable
import com.richardluo.globalIconPack.utils.Loadable
import com.richardluo.globalIconPack.utils.SingletonManager.get
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.get
import com.richardluo.globalIconPack.utils.ifNotEmpty
import com.richardluo.globalIconPack.utils.map
import com.richardluo.globalIconPack.utils.runCatchingToast
import com.richardluo.globalIconPack.utils.unflattenFromString
import java.io.OutputStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class IconVariantVM(context: Application) :
  ContextVM(context), IAppsFilter by AppsFilter(), IconsHolder, ILoadable by Loadable() {
  private val iconPackCache by get { IconPackCache(context) }
  private val iconCache by get { IconCache(context) }
  private val fallbackIconCache = IconCache(context)
  private val iconPackDB by get { IconPackDB(context) }

  val iconPack = iconPackCache[WorldPreference.get().get(Pref.ICON_PACK)]
  val pack
    get() = iconPack.pack

  override val currentIconPack
    get() = iconPack

  private val iconFallback =
    iconPackDB
      .getFallbackSettings(pack)
      .useFirstRow { FallbackSettings.from(it.getBlob(0)) }
      ?.let { IconFallback(it, iconPack::getIcon, defaultIconPackConfig) }
      ?.orNullIfEmpty()
  private val iconPackConfig = IconPackConfig(WorldPreference.get())

  override val updateFlow: Flow<*> = iconPackDB.iconsUpdateFlow

  private val icons =
    combine(Apps.flow, updateFlow) { apps, _ ->
        apps.map {
          it.zip(getIconEntry(it.map { it.componentName })) { info, entry -> info to entry }
        }
      }
      .flowOn(Dispatchers.IO)
      .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val filteredIcons =
    createFilteredIconsFlow(icons).stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val modified =
    iconPackDB.modifiedUpdateFlow
      .map { iconPackDB.isPackModified(pack) }
      .flowOn(Dispatchers.IO)
      .stateIn(viewModelScope, SharingStarted.Lazily, false)

  fun getIconEntry(cnList: List<ComponentName>) =
    iconPackDB
      .getIcon(pack, cnList, false)
      .useMapToArray(cnList.size) { _, c ->
        val entry = IconEntry.from(c.getBlob(GetIconCol.Entry))
        val pack = c.getString(GetIconCol.Pack)
        val id = c.getInt(GetIconCol.Id).takeIf { it != 0 }
        val iconPack = if (pack.isNotEmpty()) iconPackCache[pack] else iconPack
        iconPack.makeValidEntry(entry, id)?.let { IconEntryWithPack(it, iconPack) }
      }
      .asList()

  override fun mapIconEntry(cnList: List<ComponentName>) = getIconEntry(cnList)

  override suspend fun loadIcon(compIcon: AnyCompIcon) =
    if (compIcon.entry != null)
      iconCache.loadIcon(compIcon.info, compIcon.entry, iconPack, iconPackConfig)
    else fallbackIconCache.loadIcon(compIcon.info, iconFallback, iconPack, iconPackConfig)

  fun restoreDefault() {
    viewModelScope.launch(Dispatchers.IO) {
      runCatchingToast(context) { iconPackDB.restorePack(iconPack) }
    }
  }

  override fun restoreDefault(info: AppCompInfo) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatchingToast(context) {
        val packageName = info.componentName.packageName
        iconPackDB.restorePackForPackage(iconPack, packageName)
        // Shortcuts
        iconPackDB.restorePackForPackage(iconPack, "$packageName@")
      }
    }
  }

  override fun clearAll(info: AppCompInfo) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatchingToast(context) {
        val packageName = info.componentName.packageName
        iconPackDB.clearPackage(iconPack, packageName)
        // Shortcuts
        iconPackDB.clearPackage(iconPack, "$packageName@")
      }
    }
  }

  fun flipModified() {
    viewModelScope.launch(Dispatchers.IO) {
      runCatchingToast(context) { iconPackDB.setPackModified(pack, !modified.value) }
    }
  }

  override fun saveIcon(info: CompInfo, icon: VariantIcon) {
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

  fun autoFill(packs: List<String>) {
    packs.ifEmpty {
      return
    }
    launchLoading(viewModelScope) {
      val iconPacks = packs.map { iconPackCache[it] }
      icons.first()?.forEach { icons ->
        icons.forEach { (info, entry) ->
          if (entry != null) return@forEach
          val cn = info.componentName
          iconPacks.firstNotNullOfOrNull { iconPack ->
            iconPack.getIconEntry(cn)?.also {
              iconPackDB.insertOrUpdateIcon(pack, cn, it, iconPack)
            }
          }
        }
      }
    }
  }

  fun export(uri: Uri) =
    launchLoading(viewModelScope) {
      runCatchingToast(context) {
        val xml = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><resources>\n")
        iconPackDB.getAllIcons(pack, Apps.getAllWithShortcuts()).useEachRow { c ->
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
            // Can not handle entries of other types
            else -> {}
          }
        }
        OutputStreamWriter(context.contentResolver.openOutputStream(uri, "wt")).use {
          it.write(xml.append("</resources>\n").toString())
        }
        withContext(Dispatchers.Main) {
          Toast.makeText(context, R.string.iconVariant_info_exported, Toast.LENGTH_LONG).show()
        }
      }
    }

  fun import(uri: Uri) =
    launchLoading(viewModelScope) {
      runCatchingToast(context) {
        context.contentResolver.openInputStream(uri).use {
          val parser = XmlPullParserFactory.newInstance().newPullParser()
          parser.setInput(it, Xml.Encoding.UTF_8.toString())
          iconPackDB.transaction {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
              if (parser.eventType != XmlPullParser.START_TAG) continue
              val cn = parser["component"]?.let { unflattenFromString(it) } ?: continue
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
          Toast.makeText(context, R.string.iconVariant_info_imported, Toast.LENGTH_LONG).show()
        }
      }
    }
}
