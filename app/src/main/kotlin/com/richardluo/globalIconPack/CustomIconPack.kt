package com.richardluo.globalIconPack

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageItemInfo
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Xml
import androidx.core.graphics.drawable.toBitmap
import com.richardluo.globalIconPack.reflect.Resources.getDrawable
import com.richardluo.globalIconPack.reflect.Resources.getDrawableForDensity
import java.io.IOException
import kotlin.concurrent.Volatile
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory

class CustomIconPack(pm: PackageManager, private val pref: SharedPreferences) {
  private val packPackageName =
    pref.getString("iconPack", "")?.takeIf { it.isNotEmpty() }
      ?: throw Exception("No icon pack set")
  private val packResources = pm.getResourcesForApplication(packPackageName)
  private val indexMap = mutableMapOf<ComponentName, Int>()
  private val iconEntryList = mutableListOf<IconEntry?>()

  // Fallback settings from icon pack
  private var iconBacks = mutableListOf<Bitmap>()
  private var iconUpons = mutableListOf<Bitmap>()
  private var iconMasks = mutableListOf<Bitmap>()
  private var iconScale: Float = 1f

  private var globalScale: Float = pref.getFloat("scale", 1f)

  private val idCache = mutableMapOf<String, Int>()

  fun getIconEntry(cn: ComponentName) = indexMap[cn]?.let { iconEntryList.getOrNull(it) }

  fun getIconEntry(id: Int): IconEntry? = iconEntryList.getOrNull(id)

  fun getId(cn: ComponentName) = indexMap[cn]

  fun getPackIcon(resId: Int, iconDpi: Int) =
    getDrawableForDensity(packResources, resId, iconDpi, null)?.let {
      if (isLauncher) IconHelper.makeAdaptive(it, globalScale) else it
    }

  fun getIcon(iconEntry: IconEntry, iconDpi: Int): Drawable? =
    iconEntry.getIcon(this, iconDpi)?.let {
      if (isLauncher) IconHelper.makeAdaptive(it, globalScale) else it
    }

  fun getIcon(id: Int, iconDpi: Int): Drawable? = getIconEntry(id)?.let { getIcon(it, iconDpi) }

  fun getIcon(resName: String, iconDpi: Int): Drawable? =
    getDrawableId(resName)
      .takeIf { it != 0 }
      ?.let { getDrawableForDensity(packResources, it, iconDpi, null) }

  @SuppressLint("DiscouragedApi")
  fun getDrawableId(name: String) =
    idCache.getOrPut(name) { packResources.getIdentifier(name, "drawable", packPackageName) }

  fun genIconFrom(baseIcon: Drawable) =
    if (isLauncher)
      IconHelper.processIcon(
        packResources,
        baseIcon,
        iconBacks.randomOrNull(),
        iconUpons.randomOrNull(),
        iconMasks.randomOrNull(),
        iconScale * globalScale,
      )
    else {
      // Do not pass scale because BitmapDrawable will scale anyway
      IconHelper.processIconToBitmap(
        packResources,
        baseIcon,
        iconBacks.randomOrNull(),
        iconUpons.randomOrNull(),
        iconMasks.randomOrNull(),
      )
    }

  @SuppressLint("DiscouragedApi")
  fun loadInternal() {
    val parseXml = getXml("appfilter") ?: return
    val compStart = "ComponentInfo{"
    val compStartLength = compStart.length
    val compEnd = "}"
    val compEndLength = compEnd.length

    fun addFallback(parseXml: XmlPullParser, list: MutableList<Bitmap>) {
      if (!pref.getBoolean("iconFallback", true)) return
      for (i in 0 until parseXml.attributeCount) if (parseXml.getAttributeName(i).startsWith("img"))
        packResources
          .getIdentifier(parseXml.getAttributeValue(i), "drawable", packPackageName)
          .takeIf { it != 0 }
          ?.let { getDrawable(packResources, it, null)?.toBitmap() }
          ?.let { list.add(it) }
    }

    fun addIcon(parseXml: XmlPullParser, iconType: IconType) {
      var componentName: String = parseXml["component"] ?: return
      val drawableName =
        parseXml[if (iconType == IconType.Calendar) "prefix" else "drawable"] ?: return
      if (componentName.startsWith(compStart) && componentName.endsWith(compEnd)) {
        componentName =
          componentName.substring(compStartLength, componentName.length - compEndLength)
      }
      ComponentName.unflattenFromString(componentName)?.let { cn ->
        val iconEntry = IconEntry(drawableName, iconType)
        addIconEntry(cn, iconEntry)
        // TODO Use the first icon as app icon. I don't see a better way.
        addIconEntry(getComponentName(cn.packageName), iconEntry)
      }
    }

    try {
      val clockMetaMap = mutableMapOf<String, Int>()
      while (parseXml.next() != XmlPullParser.END_DOCUMENT) {
        if (parseXml.eventType != XmlPullParser.START_TAG) continue
        when (parseXml.name) {
          "iconback" -> addFallback(parseXml, iconBacks)
          "iconupon" -> addFallback(parseXml, iconUpons)
          "iconmask" -> addFallback(parseXml, iconMasks)
          "scale" -> {
            if (!pref.getBoolean("iconFallback", true)) continue
            iconScale = parseXml.getAttributeValue(null, "factor")?.toFloatOrNull() ?: 1f
          }
          "item" -> addIcon(parseXml, IconType.Normal)
          "calendar" -> addIcon(parseXml, IconType.Calendar)
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
      log(e)
    }
  }

  @SuppressLint("DiscouragedApi")
  private fun getXml(name: String): XmlPullParser? {
    try {
      return packResources
        .getIdentifier(name, "xml", packPackageName)
        .takeIf { 0 != it }
        ?.let { packResources.getXml(it) }
        ?: run {
          XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(packResources.assets.open("$name.xml"), Xml.Encoding.UTF_8.toString())
          }
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

@Volatile private var cip: CustomIconPack? = null

fun isCipInitialized() = cip != null

fun getCip(): CustomIconPack? {
  if (cip == null) {
    synchronized(CustomIconPack::class) {
      if (cip == null) {
        val pref = WorldPreference.getReadablePref()
        AndroidAppHelper.currentApplication()?.packageManager?.let {
          runCatching { cip = CustomIconPack(it, pref).apply { loadInternal() } }
            .exceptionOrNull()
            ?.let { log(it) }
        }
      }
    }
  }
  return cip
}

fun getComponentName(info: PackageItemInfo): ComponentName =
  if (info is ApplicationInfo) getComponentName(info.packageName)
  else ComponentName(info.packageName, info.name)

fun getComponentName(packageName: String): ComponentName = ComponentName(packageName, "")

val isLauncher: Boolean by lazy {
  when (val packageName = AndroidAppHelper.currentPackageName()) {
    "com.android.systemui" -> false
    "com.android.settings" -> false
    else -> {
      // Query if it is a launcher app
      val intent =
        Intent().apply {
          setPackage(packageName)
          setAction(Intent.ACTION_MAIN)
          addCategory(Intent.CATEGORY_HOME)
        }
      AndroidAppHelper.currentApplication()
        .packageManager
        .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        .let { it?.activityInfo != null }
    }
  }
}
