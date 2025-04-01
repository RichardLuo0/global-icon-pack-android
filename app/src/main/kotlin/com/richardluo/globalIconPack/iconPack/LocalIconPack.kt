package com.richardluo.globalIconPack.iconPack

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.util.Xml
import com.richardluo.globalIconPack.iconPack.database.CalendarIconEntry
import com.richardluo.globalIconPack.iconPack.database.ClockIconEntry
import com.richardluo.globalIconPack.iconPack.database.ClockMetadata
import com.richardluo.globalIconPack.iconPack.database.FallbackSettings
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.iconPack.database.NormalIconEntry
import com.richardluo.globalIconPack.reflect.Resources.getDrawableForDensity
import com.richardluo.globalIconPack.utils.get
import com.richardluo.globalIconPack.utils.getOrNull
import com.richardluo.globalIconPack.utils.log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class LocalIconPack(pack: String, resources: Resources, config: IconPackConfig = IconPackConfig()) :
  IconPack(pack, resources) {
  private val iconPackAsFallback = config.iconPackAsFallback
  private val iconFallback: IconFallback?

  private val indexMap = mutableMapOf<ComponentName, Int>()
  private val iconEntryList = mutableListOf<IconEntry>()

  private val idCache = mutableMapOf<String, Int>()

  init {
    loadIconPack(resources, pack).let { info ->
      iconFallback =
        if (config.iconFallback)
          IconFallback(
              FallbackSettings(info.iconBacks, info.iconUpons, info.iconMasks, info.iconScale),
              ::getIcon,
              config.scale,
              config.scaleOnlyForeground,
            )
            .orNullIfEmpty()
        else null
      info.iconEntryMap.forEach { (cn, entry) ->
        iconEntryList.add(entry)
        indexMap[cn] = iconEntryList.size - 1
      }
    }
  }

  override fun getId(cn: ComponentName) =
    indexMap[cn] ?: if (iconPackAsFallback) indexMap[getComponentName(cn.packageName)] else null

  override fun getIconEntry(id: Int): IconEntry? = iconEntryList.getOrNull(id)

  override fun getIconNotAdaptive(entry: IconEntry, iconDpi: Int) =
    entry.getIcon { getIcon(it, iconDpi) }

  override fun getIcon(name: String, iconDpi: Int) =
    getDrawableId(name)
      .takeIf { it != 0 }
      ?.let { getDrawableForDensity(resources, it, iconDpi, null) }

  @SuppressLint("DiscouragedApi")
  private fun getDrawableId(name: String) =
    idCache.getOrPut(name) { resources.getIdentifier(name, "drawable", pack) }

  override fun genIconFrom(baseIcon: Drawable) = genIconFrom(baseIcon, iconFallback)
}

open class IconPackInfo {
  open val iconBacks = listOf<String>()
  open val iconUpons = listOf<String>()
  open val iconMasks = listOf<String>()
  open val iconScale: Float = 1f
  open val iconEntryMap = mapOf<ComponentName, IconEntry>()
}

private class MutableIconPackInfo : IconPackInfo() {
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

// Fix when classname is empty
fun unflattenFromString(str: String): ComponentName? {
  val sep = str.indexOf('/')
  if (sep < 0) {
    return null
  }
  val pkg = str.substring(0, sep)
  var cls = str.substring(sep + 1)
  if (cls.isNotEmpty() && cls[0] == '.') {
    cls = pkg + cls
  }
  return ComponentName(pkg, cls)
}
