package com.richardluo.globalIconPack.ui.model

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.iconPack.IconFallback
import com.richardluo.globalIconPack.iconPack.IconPackConfig
import com.richardluo.globalIconPack.iconPack.database.FallbackSettings
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.iconPack.getComponentName
import com.richardluo.globalIconPack.iconPack.loadIconPack
import com.richardluo.globalIconPack.utils.AXMLEditor
import com.richardluo.globalIconPack.utils.IconHelper
import com.richardluo.globalIconPack.utils.get
import com.richardluo.globalIconPack.utils.isHighTwoByte
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import org.xmlpull.v1.XmlPullParser

interface ApkBuilder {
  fun addColor(color: Int): Int

  fun addDrawable(input: InputStream, name: String, suffix: String = ""): Int

  fun addDimen(dimen: Float): Int
}

class IconPack(val pack: String, val resources: Resources) {
  private val iconFallback: IconFallback?
  private val iconEntryMap: Map<ComponentName, IconEntry>

  private val idCache = mutableMapOf<String, Int>()

  init {
    loadIconPack(resources, pack).let { info ->
      iconFallback =
        IconFallback(
            FallbackSettings(info.iconBacks, info.iconUpons, info.iconMasks, info.iconScale),
            ::getIcon,
            info.iconScale,
            Pref.SCALE_ONLY_FOREGROUND.second,
          )
          .orNullIfEmpty()
      iconEntryMap = info.iconEntryMap
    }
  }

  fun getIcon(entry: IconEntry, iconDpi: Int) =
    entry.getIcon { getIcon(it, iconDpi) }?.let { IconHelper.makeAdaptive(it) }

  fun getIconEntry(cn: ComponentName, config: IconPackConfig) =
    iconEntryMap[cn]
      ?: if (config.iconPackAsFallback) iconEntryMap[getComponentName(cn.packageName)] else null

  fun getIcon(name: String, iconDpi: Int = 0) =
    getDrawableId(name)
      .takeIf { it != 0 }
      ?.let { resources.getDrawableForDensity(it, iconDpi, null) }

  @SuppressLint("DiscouragedApi")
  private fun getDrawableId(name: String) =
    idCache.getOrPut(name) { resources.getIdentifier(name, "drawable", pack) }

  fun genIconFrom(baseIcon: Drawable, config: IconPackConfig): Drawable {
    return if (config.iconFallback) {
      iconFallback.run {
        IconHelper.processIcon(
          resources,
          baseIcon,
          this?.iconBacks?.randomOrNull(),
          this?.iconUpons?.randomOrNull(),
          this?.iconMasks?.randomOrNull(),
          config.scale ?: (this?.iconScale ?: return baseIcon),
          config.scaleOnlyForeground,
        )
      }
    } else baseIcon
  }

  val drawables: Set<String> by lazy {
    @SuppressLint("DiscouragedApi")
    val parser =
      resources
        .getIdentifier("drawable", "xml", pack)
        .takeIf { 0 != it }
        ?.let { resources.getXml(it) } ?: return@lazy setOf()
    mutableSetOf<String>().apply {
      while (parser.next() != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType != XmlPullParser.START_TAG) continue
        when (parser.name) {
          "item" -> add(parser["drawable"] ?: continue)
        }
      }
    }
  }

  fun copyIcon(
    iconEntry: IconEntry,
    component: String,
    name: String,
    appfilterXML: StringBuilder,
    apkBuilder: ApkBuilder,
  ) =
    iconEntry.copyTo(component, name, appfilterXML) { resName, newName ->
      getDrawableId(resName).takeIf { it != 0 }?.let { addAllFiles(it, newName, apkBuilder) }
    }

  private fun addAllFiles(resId: Int, name: String, apkBuilder: ApkBuilder): Int {
    val stream = resources.openRawResource(resId)
    return if (AXMLEditor.isAXML(stream)) {
      val editor = AXMLEditor(stream)
      var i = 0
      editor.replaceResourceId { id ->
        if (!isHighTwoByte(id, 0x7f000000)) return@replaceResourceId null
        when (resources.getResourceTypeName(id)) {
          "drawable",
          "mipmap" -> addAllFiles(id, "${name}_${i++}", apkBuilder)
          "color" -> apkBuilder.addColor(resources.getColor(id, null))
          "dimen" -> apkBuilder.addDimen(resources.getDimension(id))
          else -> null
        }
      }
      apkBuilder.addDrawable(editor.toStream(), name, ".compiledXML")
    } else apkBuilder.addDrawable(stream, name)
  }

  fun copyFallbacks(name: String, xml: StringBuilder, apkBuilder: ApkBuilder) {
    iconFallback?.apply {
      copyFallback(iconBacks, "iconback", "${name}_0", xml, apkBuilder)
      copyFallback(iconUpons, "iconupon", "${name}_1", xml, apkBuilder)
      copyFallback(iconMasks, "iconmask", "${name}_2", xml, apkBuilder)
      xml.append("<scale factor=\"$iconScale\" />")
    }
  }

  private fun copyFallback(
    bitmapList: List<Bitmap>,
    tag: String,
    name: String,
    xml: StringBuilder,
    apkBuilder: ApkBuilder,
  ) {
    if (bitmapList.isEmpty()) return
    xml.append("<$tag")
    bitmapList.forEachIndexed { i, bitmap ->
      xml.append(" img$i=\"${name}_$i\" ")
      ByteArrayOutputStream().use {
        bitmap.compress(CompressFormat.PNG, 100, it)
        apkBuilder.addDrawable(ByteArrayInputStream(it.toByteArray()), "${name}_$i")
      }
    }
    xml.append("/>")
  }

  fun getAllIconEntries() = iconEntryMap
}
