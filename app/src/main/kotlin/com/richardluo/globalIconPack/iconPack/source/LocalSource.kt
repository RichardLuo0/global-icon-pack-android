package com.richardluo.globalIconPack.iconPack.source

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.util.Xml
import com.richardluo.globalIconPack.iconPack.model.CalendarIconEntry
import com.richardluo.globalIconPack.iconPack.model.ClockIconEntry
import com.richardluo.globalIconPack.iconPack.model.ClockMetadata
import com.richardluo.globalIconPack.iconPack.model.FallbackSettings
import com.richardluo.globalIconPack.iconPack.model.IconEntry
import com.richardluo.globalIconPack.iconPack.model.IconFallback
import com.richardluo.globalIconPack.iconPack.model.IconPackConfig
import com.richardluo.globalIconPack.iconPack.model.NormalIconEntry
import com.richardluo.globalIconPack.iconPack.model.ResourceOwner
import com.richardluo.globalIconPack.iconPack.model.defaultIconPackConfig
import com.richardluo.globalIconPack.utils.get
import com.richardluo.globalIconPack.utils.getOrNull
import com.richardluo.globalIconPack.utils.log
import com.richardluo.globalIconPack.utils.unflattenFromString
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class LocalSource(pack: String, config: IconPackConfig = defaultIconPackConfig) :
  Source, ResourceOwner(pack) {
  private val iconPackAsFallback = config.iconPackAsFallback
  private val iconFallback: IconFallback?

  private val indexMap: Map<ComponentName, Int>
  private val iconEntryList: List<IconEntry>

  init {
    val info = loadIconPack(res, pack)
    iconFallback =
      if (config.iconFallback)
        IconFallback(FallbackSettings(info), ::getIcon, config).orNullIfEmpty()
      else null
    indexMap = mutableMapOf<ComponentName, Int>()
    var i = 0
    iconEntryList =
      info.iconEntryMap.map { (cn, entry) ->
        indexMap[cn] = i++
        entry
      }
  }

  override fun getId(cn: ComponentName) =
    indexMap[cn] ?: if (iconPackAsFallback) indexMap[getComponentName(cn.packageName)] else null

  override fun getId(cnList: List<ComponentName>) = cnList.map { getId(it) }

  override fun getIconEntry(id: Int): IconEntry? = iconEntryList.getOrNull(id)

  override fun getIconNotAdaptive(entry: IconEntry, iconDpi: Int) =
    entry.getIcon { getIcon(it, iconDpi) }

  override fun getIcon(name: String, iconDpi: Int) = getIconById(getDrawableId(name), iconDpi)

  private fun getDrawableId(name: String) = getIdByName(name)

  override fun genIconFrom(baseIcon: Drawable) = genIconFrom(res, baseIcon, iconFallback)
}

interface IconPackInfo {
  val iconBacks: List<String>
  val iconUpons: List<String>
  val iconMasks: List<String>
  val iconScale: Float
  val iconEntryMap: Map<ComponentName, IconEntry>
}

private class MutableIconPackInfo : IconPackInfo {
  override val iconBacks: MutableList<String> = mutableListOf()
  override val iconUpons: MutableList<String> = mutableListOf()
  override val iconMasks: MutableList<String> = mutableListOf()
  override var iconScale: Float = 1f
  override val iconEntryMap: MutableMap<ComponentName, IconEntry> = mutableMapOf()
}

@SuppressLint("DiscouragedApi")
internal fun loadIconPack(resources: Resources, pack: String): IconPackInfo {
  val info = MutableIconPackInfo()
  val iconEntryMap = info.iconEntryMap

  val parser = getAppFilter(resources, pack) ?: return info
  val compStart = "ComponentInfo{"
  val compStartLength = compStart.length
  val compEnd = "}"
  val compEndLength = compEnd.length

  fun addFallback(parseXml: XmlPullParser, list: MutableList<String>) {
    for (i in 0 until parseXml.attributeCount) if (parseXml.getAttributeName(i).startsWith("img"))
      list.add(parseXml.getAttributeValue(i))
  }

  // Any type other than normal will take priority
  fun addIconEntry(cn: ComponentName, entry: IconEntry) {
    if (!iconEntryMap.containsKey(cn) || entry !is NormalIconEntry) iconEntryMap[cn] = entry
  }

  fun addIcon(parseXml: XmlPullParser, iconEntry: IconEntry) {
    var componentName: String = parseXml["component"] ?: return
    if (componentName.startsWith(compStart) && componentName.endsWith(compEnd)) {
      componentName = componentName.substring(compStartLength, componentName.length - compEndLength)
    }
    unflattenFromString(componentName)?.let { cn ->
      addIconEntry(cn, iconEntry)
      // Use the first icon as app icon. I don't see a better way.
      addIconEntry(getComponentName(cn.packageName), iconEntry)
    }
  }

  try {
    val clockMetaMap = mutableMapOf<String, IconEntry>()
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
      if (parser.eventType != XmlPullParser.START_TAG) continue
      when (parser.name) {
        "iconback" -> addFallback(parser, info.iconBacks)
        "iconupon" -> addFallback(parser, info.iconUpons)
        "iconmask" -> addFallback(parser, info.iconMasks)
        "scale" -> info.iconScale = parser.getAttributeValue(null, "factor")?.toFloatOrNull() ?: 1f

        "item" -> addIcon(parser, NormalIconEntry(parser["drawable"] ?: continue))
        "calendar" -> addIcon(parser, CalendarIconEntry(parser["prefix"] ?: continue))
        "dynamic-clock" -> {
          val drawableName = parser["drawable"] ?: continue
          if (parser !is XmlResourceParser) continue
          clockMetaMap[drawableName] =
            ClockIconEntry(
              drawableName,
              ClockMetadata(
                parser.getAttributeIntValue(null, "hourLayerIndex", -1),
                parser.getAttributeIntValue(null, "minuteLayerIndex", -1),
                parser.getAttributeIntValue(null, "secondLayerIndex", -1),
                parser.getAttributeIntValue(null, "defaultHour", 0),
                parser.getAttributeIntValue(null, "defaultMinute", 0),
                parser.getAttributeIntValue(null, "defaultSecond", 0),
              ),
            )
        }
      }
    }
    if (clockMetaMap.isNotEmpty())
      iconEntryMap
        .filter { it.value !is ClockIconEntry }
        .forEach { (cn, entry) -> clockMetaMap[entry.name]?.let { iconEntryMap[cn] = it } }
  } catch (e: Exception) {
    log(e)
  }

  if (parser is XmlResourceParser) parser.close()
  return info
}

@SuppressLint("DiscouragedApi")
private fun getAppFilter(res: Resources, pack: String) =
  runCatching {
      res.getIdentifier("appfilter", "xml", pack).takeIf { 0 != it }?.let { res.getXml(it) }
        ?: run {
          XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(res.assets.open("appfilter.xml"), Xml.Encoding.UTF_8.toString())
          }
        }
    }
    .getOrNull { log(it) }
