package com.richardluo.globalIconPack.iconPack

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.util.Xml
import androidx.core.graphics.drawable.toBitmap
import com.richardluo.globalIconPack.iconPack.database.ClockMetadata
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.iconPack.database.IconType
import com.richardluo.globalIconPack.utils.log
import com.richardluo.globalIconPack.utils.logInApp
import java.io.IOException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory

class LocalIconPack(
  pref: SharedPreferences,
  isInMod: Boolean = false,
  getResources: (packageName: String) -> Resources?,
) : IconPack(pref, getResources) {
  private val indexMap = mutableMapOf<ComponentName, Int>()
  private val iconEntryList = mutableListOf<IconEntry?>()

  override fun getIconEntry(cn: ComponentName) = indexMap[cn]?.let { iconEntryList.getOrNull(it) }

  override fun getIconEntry(id: Int): IconEntry? = iconEntryList.getOrNull(id)

  override fun getId(cn: ComponentName) =
    indexMap[cn] ?: if (iconPackAsFallback) indexMap[getComponentName(cn.packageName)] else null

  init {
    loadIconPack(resources, pack, iconFallback, isInMod)?.let { info ->
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
}

data class IconPackInfo(
  val iconBacks: List<String>,
  val iconUpons: List<String>,
  val iconMasks: List<String>,
  val iconScale: Float,
  val iconEntryMap: MutableMap<ComponentName, IconEntry>,
)

@SuppressLint("DiscouragedApi")
internal fun loadIconPack(
  resources: Resources,
  pack: String,
  iconFallback: Boolean,
  isInMod: Boolean = false,
): IconPackInfo? {
  val iconBacks = mutableListOf<String>()
  val iconUpons = mutableListOf<String>()
  val iconMasks = mutableListOf<String>()
  var iconScale = 1f
  val iconEntryMap = mutableMapOf<ComponentName, IconEntry>()

  val parseXml = getXml(resources, pack, "appfilter") ?: return null
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
    if (!iconEntryMap.containsKey(cn) || entry.type != IconType.Normal) iconEntryMap[cn] = entry
  }

  fun addIcon(parseXml: XmlPullParser, iconType: IconType) {
    var componentName: String = parseXml["component"] ?: return
    val drawableName =
      parseXml[if (iconType == IconType.Calendar) "prefix" else "drawable"] ?: return
    if (componentName.startsWith(compStart) && componentName.endsWith(compEnd)) {
      componentName = componentName.substring(compStartLength, componentName.length - compEndLength)
    }
    ComponentName.unflattenFromString(componentName)?.let { cn ->
      val iconEntry = IconEntry(drawableName, iconType)
      addIconEntry(cn, iconEntry)
      // TODO Use the first icon as app icon. I don't see a better way.
      addIconEntry(getComponentName(cn.packageName), iconEntry)
    }
  }

  try {
    val clockMetaMap = mutableMapOf<String, IconEntry>()
    while (parseXml.next() != XmlPullParser.END_DOCUMENT) {
      if (parseXml.eventType != XmlPullParser.START_TAG) continue
      when (parseXml.name) {
        "iconback" -> addFallback(parseXml, iconBacks)
        "iconupon" -> addFallback(parseXml, iconUpons)
        "iconmask" -> addFallback(parseXml, iconMasks)
        "scale" -> {
          if (!iconFallback) continue
          iconScale = parseXml.getAttributeValue(null, "factor")?.toFloatOrNull() ?: 1f
        }
        "item" -> addIcon(parseXml, IconType.Normal)
        "calendar" -> addIcon(parseXml, IconType.Calendar)
        "dynamic-clock" -> {
          val drawableName = parseXml["drawable"] ?: continue
          if (parseXml !is XmlResourceParser) continue
          clockMetaMap[drawableName] =
            IconEntry(
              drawableName,
              IconType.Clock,
              ClockMetadata(
                parseXml.getAttributeIntValue(null, "hourLayerIndex", -1),
                parseXml.getAttributeIntValue(null, "minuteLayerIndex", -1),
                parseXml.getAttributeIntValue(null, "secondLayerIndex", -1),
                parseXml.getAttributeIntValue(null, "defaultHour", 0),
                parseXml.getAttributeIntValue(null, "defaultMinute", 0),
                parseXml.getAttributeIntValue(null, "defaultSecond", 0),
              ),
            )
        }
      }
    }
    iconEntryMap.forEach { (cn, entry) ->
      val drawableName = entry.takeUnless { it.type == IconType.Clock }?.name ?: return@forEach
      clockMetaMap[drawableName]?.let { iconEntryMap[cn] = it }
    }
  } catch (e: Exception) {
    if (isInMod) log(e) else logInApp(e)
  }
  return IconPackInfo(iconBacks, iconUpons, iconMasks, iconScale, iconEntryMap)
}

@SuppressLint("DiscouragedApi")
private fun getXml(resources: Resources, pack: String, name: String): XmlPullParser? {
  try {
    return resources
      .getIdentifier(name, "xml", pack)
      .takeIf { 0 != it }
      ?.let { resources.getXml(it) }
      ?: run {
        XmlPullParserFactory.newInstance().newPullParser().apply {
          setInput(resources.assets.open("$name.xml"), Xml.Encoding.UTF_8.toString())
        }
      }
  } catch (_: PackageManager.NameNotFoundException) {} catch (_: IOException) {} catch (
    _: XmlPullParserException) {}
  return null
}

private operator fun XmlPullParser.get(key: String): String? = this.getAttributeValue(null, key)
