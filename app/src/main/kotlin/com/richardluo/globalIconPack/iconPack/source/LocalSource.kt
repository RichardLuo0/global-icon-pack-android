package com.richardluo.globalIconPack.iconPack.source

import android.content.ComponentName
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
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
import com.richardluo.globalIconPack.utils.log
import com.richardluo.globalIconPack.utils.parseXML
import com.richardluo.globalIconPack.utils.unflattenFromString
import org.xmlpull.v1.XmlPullParser

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

  override fun maskIconFrom(baseIcon: Drawable) =
    maskIconFrom(res, baseIcon, iconFallback?.iconMasks)
}

interface IconPackInfo {
  val iconBacks: List<String>
  val iconUpons: List<String>
  val iconMasks: List<String>
  val iconScale: Float
  val iconEntryMap: Map<ComponentName, IconEntry>
  val clockIconEntryMap: Map<String, ClockIconEntry>
}

private class MutableIconPackInfo : IconPackInfo {
  override val iconBacks = mutableListOf<String>()
  override val iconUpons = mutableListOf<String>()
  override val iconMasks = mutableListOf<String>()
  override var iconScale = 1f
  override val iconEntryMap = mutableMapOf<ComponentName, IconEntry>()
  override val clockIconEntryMap = mutableMapOf<String, ClockIconEntry>()
}

fun loadIconPack(resources: Resources, pack: String): IconPackInfo {
  val info = MutableIconPackInfo()
  val iconEntryMap = info.iconEntryMap
  val clockIconEntryMap = info.clockIconEntryMap

  val parser = parseXML(resources, "appfilter", pack) ?: return info
  val compPrefix = "ComponentInfo{"
  val compSuffix = "}"

  fun addFallback(parseXml: XmlPullParser, list: MutableList<String>) {
    for (i in 0 until parseXml.attributeCount) if (parseXml.getAttributeName(i).startsWith("img"))
      list.add(parseXml.getAttributeValue(i))
  }

  fun addIcon(parseXml: XmlPullParser, iconEntry: IconEntry) {
    val cnString = parseXml["component"]?.removeSurrounding(compPrefix, compSuffix) ?: return
    val cn = unflattenFromString(cnString) ?: return
    // If a calendar icon entry has been set, skip it
    if (iconEntryMap[cn]?.type == IconEntry.Type.Calendar) return
    // Set new icon entry
    iconEntryMap[cn] = iconEntry
    // Use the first icon as app icon. I don't see a better way.
    iconEntryMap.putIfAbsent(getComponentName(cn.packageName), iconEntry)
  }

  try {
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
          clockIconEntryMap[drawableName] =
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
    // Replace clock iconEntries in iconEntryMap
    if (clockIconEntryMap.isNotEmpty())
      iconEntryMap.replaceAll { _, entry -> clockIconEntryMap[entry.name] ?: entry }
  } catch (e: Exception) {
    log(e)
  }

  if (parser is XmlResourceParser) parser.close()
  return info
}
