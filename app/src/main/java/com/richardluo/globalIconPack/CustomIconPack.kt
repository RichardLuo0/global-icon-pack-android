package com.richardluo.globalIconPack

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageItemInfo
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.util.Xml
import com.richardluo.globalIconPack.reflect.Resources.getDrawable
import com.richardluo.globalIconPack.reflect.Resources.getDrawableForDensity
import de.robv.android.xposed.XposedBridge
import java.io.IOException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory

class CustomIconPack(
  private val pm: PackageManager,
  private val packPackageName: String,
  private val pref: SharedPreferences,
) {
  private val packResources = pm.getResourcesForApplication(packPackageName)
  private val indexMap = mutableMapOf<ComponentName, Int>()
  private val iconEntryList = mutableListOf<IconEntry?>()

  private var iconBack: Drawable? = null
  private var iconUpon: Drawable? = null
  private var iconMask: Drawable? = null
  private var scale: Float = 1f

  private val idCache = mutableMapOf<String, Int>()

  fun getIconEntry(cn: ComponentName) = indexMap[cn]?.let { iconEntryList.getOrNull(it) }

  fun getIconEntry(id: Int): IconEntry? = iconEntryList.getOrNull(id)

  fun getId(cn: ComponentName) = indexMap[cn]

  fun getIcon(iconEntry: IconEntry, iconDpi: Int): Drawable? = iconEntry.getIcon(this, iconDpi)

  fun getIcon(id: Int, iconDpi: Int): Drawable? = getIconEntry(id)?.let { getIcon(it, iconDpi) }

  fun getIcon(resName: String, iconDpi: Int): Drawable? =
    getDrawableId(resName)
      .takeIf { it != 0 }
      ?.let { getDrawableForDensity(packResources, it, iconDpi, null) }

  @SuppressLint("DiscouragedApi")
  fun getDrawableId(name: String) =
    idCache.getOrPut(name) { packResources.getIdentifier(name, "drawable", packPackageName) }

  fun genIconFrom(baseIcon: Drawable, iconDpi: Int) =
    IconHelper.processIcon(packResources, baseIcon, iconDpi, iconBack, iconUpon, iconMask, scale)

  @SuppressLint("DiscouragedApi")
  fun loadInternal() {
    val parseXml = getXml("appfilter") ?: return
    val compStart = "ComponentInfo{"
    val compStartLength = compStart.length
    val compEnd = "}"
    val compEndLength = compEnd.length
    try {
      val clockMetaMap = mutableMapOf<String, Int>()
      while (parseXml.next() != XmlPullParser.END_DOCUMENT) {
        if (parseXml.eventType != XmlPullParser.START_TAG) continue
        val name = parseXml.name
        when (name) {
          "iconback" -> {
            if (!pref.getBoolean("iconBack", true)) continue
            for (i in 0 until parseXml.attributeCount) {
              if (parseXml.getAttributeName(i).startsWith("img")) {
                val imgName = parseXml.getAttributeValue(i)
                val id = packResources.getIdentifier(imgName, "drawable", packPackageName)
                if (id != 0) iconBack = getDrawable(packResources, id, null)
              }
            }
          }

          "iconupon" -> {
            if (!pref.getBoolean("iconUpon", true)) continue
            for (i in 0 until parseXml.attributeCount) {
              if (parseXml.getAttributeName(i).startsWith("img")) {
                val imgName = parseXml.getAttributeValue(i)
                val id = packResources.getIdentifier(imgName, "drawable", packPackageName)
                if (id != 0) iconUpon = getDrawable(packResources, id, null)
              }
            }
          }

          "iconmask" -> {
            if (!pref.getBoolean("iconMask", true)) continue
            for (i in 0 until parseXml.attributeCount) {
              if (parseXml.getAttributeName(i).startsWith("img")) {
                val imgName = parseXml.getAttributeValue(i)
                val id = packResources.getIdentifier(imgName, "drawable", packPackageName)
                if (id != 0) iconMask = getDrawable(packResources, id, null)
              }
            }
          }

          "scale" -> {
            if (!pref.getBoolean("scale", true)) continue
            scale = parseXml.getAttributeValue(null, "factor")?.toFloatOrNull() ?: 1f
          }

          "item" -> {
            var componentName: String? = parseXml["component"]
            val drawableName = parseXml["drawable"]
            if (componentName != null && drawableName != null) {
              if (componentName.startsWith(compStart) && componentName.endsWith(compEnd)) {
                componentName =
                  componentName.substring(compStartLength, componentName.length - compEndLength)
              }
              ComponentName.unflattenFromString(componentName)?.let { cn ->
                val iconEntry = IconEntry(drawableName, IconType.Normal)
                addIconEntry(cn, iconEntry)
                // TODO Use the first icon as app icon. I don't see a better way.
                addIconEntry(getComponentName(cn.packageName), iconEntry)
              }
            }
          }

          "calendar" -> {
            var componentName: String? = parseXml["component"]
            val drawableName = parseXml["prefix"]
            if (componentName != null && drawableName != null) {
              if (componentName.startsWith(compStart) && componentName.endsWith(compEnd)) {
                componentName =
                  componentName.substring(compStartLength, componentName.length - compEndLength)
              }
              ComponentName.unflattenFromString(componentName)?.let { cn ->
                val iconEntry = IconEntry(drawableName, IconType.Calendar)
                addIconEntry(cn, iconEntry)
                addIconEntry(getComponentName(cn.packageName), iconEntry)
              }
            }
          }

          "dynamic-clock" -> {
            val drawableName = parseXml["drawable"]
            if (drawableName != null) {
              if (parseXml is XmlResourceParser) {
                iconEntryList.add(
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
                )
                clockMetaMap[drawableName] = iconEntryList.size - 1
              }
            }
          }
        }
      }
      indexMap.forEach { (cn, id) ->
        val drawableName =
          iconEntryList.getOrNull(id)?.takeUnless { it.type == IconType.Clock }?.name ?: return
        clockMetaMap[drawableName]?.let {
          iconEntryList[id] = null
          indexMap[cn] = it
        }
      }
    } catch (e: Exception) {
      XposedBridge.log(e)
    }
  }

  private fun getXml(name: String): XmlPullParser? {
    try {
      @SuppressLint("DiscouragedApi")
      val resourceId = packResources.getIdentifier(name, "xml", packPackageName)
      return if (0 != resourceId) {
        pm.getXml(packPackageName, resourceId, null)
      } else {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(packResources.assets.open("$name.xml"), Xml.Encoding.UTF_8.toString())
        parser
      }
    } catch (_: PackageManager.NameNotFoundException) {} catch (_: IOException) {} catch (
      _: XmlPullParserException) {}
    return null
  }

  // Any type other than normal will take priority
  private fun addIconEntry(cn: ComponentName, entry: IconEntry) {
    if (!indexMap.containsKey(cn)) {
      iconEntryList.add(entry)
      indexMap[cn] = iconEntryList.size - 1
    } else if (entry.type != IconType.Normal) {
      val index = indexMap[cn] ?: return
      if (iconEntryList.getOrNull(index)?.type == IconType.Normal) iconEntryList[index] = entry
    }
  }
}

private operator fun XmlPullParser.get(key: String): String? = this.getAttributeValue(null, key)

private lateinit var cip: CustomIconPack

fun getCip(): CustomIconPack? {
  return if (!::cip.isInitialized) {
    val pref = WorldPreference.getReadablePref()
    val packPackageName = pref.getString("iconPack", "")?.takeIf { it.isNotEmpty() } ?: return null
    AndroidAppHelper.currentApplication()?.packageManager?.let {
      cip = CustomIconPack(it, packPackageName, pref).apply { loadInternal() }
      cip
    }
  } else cip
}

fun getComponentName(info: PackageItemInfo): ComponentName {
  return if (info is ApplicationInfo) getComponentName(info.packageName)
  else ComponentName(info.packageName, info.name)
}

fun getComponentName(packageName: String): ComponentName = ComponentName(packageName, "")
