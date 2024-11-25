package com.richardluo.globalIconPack

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageItemInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Xml
import com.richardluo.globalIconPack.reflect.Resources.getDrawable
import com.richardluo.globalIconPack.reflect.Resources.getDrawableForDensity
import de.robv.android.xposed.XSharedPreferences
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException

class CustomIconPack(
    private val pm: PackageManager,
    private val packPackageName: String,
    private val pref: XSharedPreferences
) {
    private val packResources = pm.getResourcesForApplication(packPackageName)
    private val componentMap = mutableMapOf<ComponentName, IconEntry>()
    private val calendarMap = mutableMapOf<ComponentName, IconEntry>()
    private val clockMap = mutableMapOf<ComponentName, IconEntry>()
    private val clockMetas = mutableMapOf<IconEntry, ClockMetadata>()

    private var iconBack: Drawable? = null
    private var iconUpon: Drawable? = null
    private var iconMask: Drawable? = null
    private var scale: Float = 1f

    private val idCache = mutableMapOf<String, Int>()

    fun getPackPackageName() = packPackageName

    fun getComponentName(info: PackageItemInfo) =
        if (info is ApplicationInfo) getComponentName(info.packageName) else ComponentName(
            info.packageName, info.name
        )

    fun getComponentName(packageName: String) = pm.getLaunchIntentForPackage(packageName)?.component

    val label = pm.let { pm ->
        pm.getApplicationInfo(packPackageName, 0).loadLabel(pm).toString()
    }

    fun getIconEntry(componentName: ComponentName) = componentMap[componentName]
    fun getCalendar(componentName: ComponentName) = calendarMap[componentName]
    fun getClock(entry: IconEntry) = clockMetas[entry]

    fun getCalendars(): MutableSet<ComponentName> = calendarMap.keys
    fun getClocks(): MutableSet<ComponentName> = clockMap.keys

    fun getIcon(iconEntry: IconEntry, iconDpi: Int): Drawable? {
        val id = getDrawableId(iconEntry.name)
        if (id == 0) return null
        return try {
            return getDrawableForDensity.invoke(packResources, id, iconDpi, null) as Drawable?
        } catch (_: Resources.NotFoundException) {
            null
        }
    }

    fun generateIcon(baseIcon: Drawable, iconDpi: Int) = generateIcon(
        packResources, baseIcon, iconDpi, iconBack, iconUpon, iconMask, scale
    )

//    fun createFromExternalPicker(icon: Intent.ShortcutIconResource): IconPickerItem? {
//        @SuppressLint("DiscouragedApi")
//        val id = packResources.getIdentifier(icon.resourceName, null, null)
//        if (id == 0) return null
//        val simpleName = packResources.getResourceEntryName(id)
//        return IconPickerItem(packPackageName, simpleName, simpleName, IconType.Normal)
//    }

    @SuppressLint("DiscouragedApi")
    fun loadInternal() {
        val parseXml = getXml("appfilter") ?: return
        val compStart = "ComponentInfo{"
        val compStartLength = compStart.length
        val compEnd = "}"
        val compEndLength = compEnd.length
        try {
            while (parseXml.next() != XmlPullParser.END_DOCUMENT) {
                if (parseXml.eventType != XmlPullParser.START_TAG) continue
                val name = parseXml.name
                val isCalendar = name == "calendar"
                when (name) {
                    "iconback" -> {
                        if (pref.getBoolean("noIconBack", false)) continue
                        for (i in 0 until parseXml.attributeCount) {
                            if (parseXml.getAttributeName(i).startsWith("img")) {
                                val imgName = parseXml.getAttributeValue(i)
                                val id = packResources.getIdentifier(
                                    imgName, "drawable", packPackageName
                                )
                                if (id != 0) iconBack =
                                    getDrawable.invoke(packResources, id, null) as Drawable?
                            }
                        }
                    }

                    "iconupon" -> {
                        if (pref.getBoolean("noIconUpon", false)) continue
                        for (i in 0 until parseXml.attributeCount) {
                            if (parseXml.getAttributeName(i).startsWith("img")) {
                                val imgName = parseXml.getAttributeValue(i)
                                val id = packResources.getIdentifier(
                                    imgName, "drawable", packPackageName
                                )
                                if (id != 0) iconUpon =
                                    getDrawable.invoke(packResources, id, null) as Drawable?
                            }
                        }
                    }

                    "iconmask" -> {
                        if (pref.getBoolean("noIconMask", false)) continue
                        for (i in 0 until parseXml.attributeCount) {
                            if (parseXml.getAttributeName(i).startsWith("img")) {
                                val imgName = parseXml.getAttributeValue(i)
                                val id = packResources.getIdentifier(
                                    imgName, "drawable", packPackageName
                                )
                                if (id != 0) iconMask =
                                    getDrawable.invoke(packResources, id, null) as Drawable?
                            }
                        }
                    }

                    "scale" -> {
                        if (pref.getBoolean("noScale", false)) continue
                        scale = parseXml.getAttributeValue(null, "factor")?.toFloatOrNull() ?: 1f
                    }

                    "item", "calendar" -> {
                        var componentName: String? = parseXml["component"]
                        val drawableName = parseXml[if (isCalendar) "prefix" else "drawable"]
                        if (componentName != null && drawableName != null) {
                            if (componentName.startsWith(compStart) && componentName.endsWith(
                                    compEnd
                                )
                            ) {
                                componentName = componentName.substring(
                                    compStartLength, componentName.length - compEndLength
                                )
                            }
                            val parsed = ComponentName.unflattenFromString(componentName)
                            if (parsed != null) {
                                if (isCalendar) {
                                    calendarMap[parsed] =
                                        IconEntry(packPackageName, drawableName, IconType.Calendar)
                                } else {
                                    componentMap[parsed] =
                                        IconEntry(packPackageName, drawableName, IconType.Normal)
                                }
                            }
                        }
                    }

//                    "dynamic-clock" -> {
//                        val drawableName = parseXml["drawable"]
//                        if (drawableName != null) {
//                            if (parseXml is XmlResourceParser) {
//                                clockMetas[IconEntry(
//                                    packPackageName, drawableName, IconType.Normal
//                                )] = ClockMetadata(
//                                    parseXml.getAttributeIntValue(null, "hourLayerIndex", -1),
//                                    parseXml.getAttributeIntValue(null, "minuteLayerIndex", -1),
//                                    parseXml.getAttributeIntValue(null, "secondLayerIndex", -1),
//                                    parseXml.getAttributeIntValue(null, "defaultHour", 0),
//                                    parseXml.getAttributeIntValue(null, "defaultMinute", 0),
//                                    parseXml.getAttributeIntValue(null, "defaultSecond", 0),
//                                )
//                            }
//                        }
//                    }
                }
            }
//            componentMap.forEach { (componentName, iconEntry) ->
//                if (clockMetas.containsKey(iconEntry)) {
//                    clockMap[componentName] = iconEntry
//                }
//            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }

//    fun getAllIcons(): Flow<List<IconPickerCategory>> = flow {
//        load()
//
//        val result = mutableListOf<IconPickerCategory>()
//
//        var currentTitle: String? = null
//        val currentItems = mutableListOf<IconPickerItem>()
//
//        suspend fun endCategory() {
//            if (currentItems.isEmpty()) return
//            val title = currentTitle ?: context.getString(R.string.icon_picker_default_category)
//            result.add(IconPickerCategory(title, ArrayList(currentItems)))
//            currentTitle = null
//            currentItems.clear()
//            emit(ArrayList(result))
//        }
//
//        val parser = getXml("drawable")
//        while (parser != null && parser.next() != XmlPullParser.END_DOCUMENT) {
//            if (parser.eventType != XmlPullParser.START_TAG) continue
//            when (parser.name) {
//                "category" -> {
//                    val title = parser["title"] ?: continue
//                    endCategory()
//                    currentTitle = title
//                }
//
//                "item" -> {
//                    val drawableName = parser["drawable"] ?: continue
//                    val resId = getDrawableId(drawableName)
//                    if (resId != 0) {
//                        val item = IconPickerItem(
//                            packPackageName,
//                            drawableName,
//                            drawableName,
//                            IconType.Normal
//                        )
//                        currentItems.add(item)
//                    }
//                }
//            }
//        }
//        endCategory()
//    }.flowOn(Dispatchers.IO)

    @SuppressLint("DiscouragedApi")
    public fun getDrawableId(name: String) = idCache.getOrPut(name) {
        packResources.getIdentifier(name, "drawable", packPackageName)
    }

    private fun getXml(name: String): XmlPullParser? {
        try {
            @SuppressLint("DiscouragedApi") val resourceId =
                packResources.getIdentifier(name, "xml", packPackageName)
            return if (0 != resourceId) {
                pm.getXml(packPackageName, resourceId, null)
            } else {
                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(
                    packResources.assets.open("$name.xml"), Xml.Encoding.UTF_8.toString()
                )
                parser
            }
        } catch (_: PackageManager.NameNotFoundException) {
        } catch (_: IOException) {
        } catch (_: XmlPullParserException) {
        }
        return null
    }
}

private operator fun XmlPullParser.get(key: String): String? = this.getAttributeValue(null, key)