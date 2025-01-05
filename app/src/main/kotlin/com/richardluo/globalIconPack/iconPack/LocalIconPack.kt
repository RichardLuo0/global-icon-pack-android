package com.richardluo.globalIconPack.iconPack

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.util.Xml
import androidx.core.graphics.drawable.toBitmap
import com.richardluo.globalIconPack.iconPack.database.CalendarIconEntry
import com.richardluo.globalIconPack.iconPack.database.ClockIconEntry
import com.richardluo.globalIconPack.iconPack.database.ClockMetadata
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.iconPack.database.NormalIconEntry
import com.richardluo.globalIconPack.utils.getOrNull
import com.richardluo.globalIconPack.utils.log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

open class LocalIconPack(pref: SharedPreferences, pack: String, resources: Resources) :
  IconPack(pref, pack, resources) {
  protected val indexMap = mutableMapOf<ComponentName, Int>()
  protected val iconEntryList = mutableListOf<IconEntry>()

  init {
    loadIconPack(resources, pack, iconFallback).let { info ->
      this.iconBacks = info.iconBacks.mapNotNull { getIcon(it)?.toBitmap() }
      this.iconUpons = info.iconUpons.mapNotNull { getIcon(it)?.toBitmap() }
      this.iconMasks = info.iconMasks.mapNotNull { getIcon(it)?.toBitmap() }
      if (iconFallback && !enableOverrideIconFallback) this.iconScale = info.iconScale
      info.iconEntryMap.forEach { (cn, entry) ->
        iconEntryList.add(entry)
        indexMap[cn] = iconEntryList.size - 1
      }
    }
  }

  override fun getIconEntry(id: Int): IconEntry? = iconEntryList.getOrNull(id)

  override fun getId(cn: ComponentName) =
    indexMap[cn] ?: if (iconPackAsFallback) indexMap[getComponentName(cn.packageName)] else null
}

class IconPackInfo(
  val iconBacks: MutableList<String> = mutableListOf(),
  val iconUpons: MutableList<String> = mutableListOf(),
  val iconMasks: MutableList<String> = mutableListOf(),
  var iconScale: Float = 1f,
  val iconEntryMap: MutableMap<ComponentName, IconEntry> = mutableMapOf(),
)

@SuppressLint("DiscouragedApi")
internal fun loadIconPack(resources: Resources, pack: String, iconFallback: Boolean): IconPackInfo {
  val info = IconPackInfo()
  val iconEntryMap = info.iconEntryMap

  val parser = getXml(resources, pack) ?: return info
  val compStart = "ComponentInfo{"
  val compStartLength = compStart.length
  val compEnd = "}"
  val compEndLength = compEnd.length

  fun addFallback(parseXml: XmlPullParser, list: MutableList<String>) {
    if (!iconFallback) return
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
    ComponentName.unflattenFromString(componentName)?.let { cn ->
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
        "scale" -> {
          if (!iconFallback) continue
          info.iconScale = parser.getAttributeValue(null, "factor")?.toFloatOrNull() ?: 1f
        }
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
private fun getXml(resources: Resources, pack: String) =
  runCatching {
      resources
        .getIdentifier("appfilter", "xml", pack)
        .takeIf { 0 != it }
        ?.let { resources.getXml(it) }
        ?: run {
          XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(resources.assets.open("appfilter.xml"), Xml.Encoding.UTF_8.toString())
          }
        }
    }
    .getOrNull { log(it) }

private operator fun XmlPullParser.get(key: String): String? = this.getAttributeValue(null, key)
