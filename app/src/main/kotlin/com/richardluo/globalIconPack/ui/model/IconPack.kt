package com.richardluo.globalIconPack.ui.model

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.res.Resources
import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.iconPack.model.FallbackSettings
import com.richardluo.globalIconPack.iconPack.model.IconEntry
import com.richardluo.globalIconPack.iconPack.model.IconFallback
import com.richardluo.globalIconPack.iconPack.model.IconPackConfig
import com.richardluo.globalIconPack.iconPack.model.defaultIconPackConfig
import com.richardluo.globalIconPack.iconPack.model.withConfig
import com.richardluo.globalIconPack.iconPack.source.getComponentName
import com.richardluo.globalIconPack.iconPack.source.loadIconPack
import com.richardluo.globalIconPack.utils.AXMLEditor
import com.richardluo.globalIconPack.utils.IconHelper
import com.richardluo.globalIconPack.utils.IconPackCreator.ApkBuilder
import com.richardluo.globalIconPack.utils.get
import com.richardluo.globalIconPack.utils.isHighTwoByte
import org.xmlpull.v1.XmlPullParser

private class IconEntryWithId(val entry: IconEntry, val id: Int) : IconEntry by entry

class IconPack(val pack: String, val res: Resources) {
  val info by lazy { loadIconPack(res, pack) }
  val iconFallback by lazy {
    IconFallback(FallbackSettings(info), ::getIcon, defaultIconPackConfig).orNullIfEmpty()
  }
  val iconEntryMap by lazy { info.iconEntryMap }

  private val idCache = mutableMapOf<String, Int>()

  fun getIconEntry(cn: ComponentName, iconPackAsFallback: Boolean = false) =
    (iconEntryMap[cn]
        ?: if (iconPackAsFallback) iconEntryMap[getComponentName(cn.packageName)] else null)
      ?.let { entry -> makeValidEntry(entry) }

  fun makeValidEntry(entry: IconEntry, expectId: Int? = null): IconEntry? {
    // Get id now because it will be used anyway
    // Exclude those without valid drawable id
    val id =
      when (entry.type) {
        IconEntry.Type.Normal,
        IconEntry.Type.Clock -> getDrawableId(entry.name).takeIf { it != 0 } ?: return null
        else -> 0
      }
    return if (expectId == null || id == expectId) {
      id.takeIf { it != 0 }?.let { IconEntryWithId(entry, it) } ?: entry
    } else null
  }

  fun getIcon(entry: IconEntry, iconDpi: Int) =
    when (entry) {
      is IconEntryWithId -> entry.getIcon { res.getDrawableForDensity(entry.id, iconDpi, null) }
      else -> entry.getIcon { getIcon(it, iconDpi) }
    }?.let { IconHelper.makeAdaptive(it) }

  fun getIcon(name: String, iconDpi: Int = 0) =
    getDrawableId(name).takeIf { it != 0 }?.let { res.getDrawableForDensity(it, iconDpi, null) }

  @SuppressLint("DiscouragedApi")
  fun getDrawableId(name: String) =
    idCache.getOrPut(name) { res.getIdentifier(name, "drawable", pack) }

  val drawables: Set<String> by lazy {
    @SuppressLint("DiscouragedApi")
    val parser =
      res.getIdentifier("drawable", "xml", pack).takeIf { 0 != it }?.let { res.getXml(it) }
        ?: return@lazy setOf()
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
    entry: IconEntry,
    component: String,
    name: String,
    appfilterXML: StringBuilder,
    apkBuilder: ApkBuilder,
  ) =
    entry.copyTo(component, name, appfilterXML) { resName, newName ->
      addAllFiles(getDrawableId(resName), newName, apkBuilder)
    }

  private fun addAllFiles(resId: Int, name: String, apkBuilder: ApkBuilder): Int {
    if (resId == 0) return 0
    val stream = res.openRawResource(resId)
    return if (AXMLEditor.isAXML(stream)) {
      val editor = AXMLEditor(stream)
      var i = 0
      editor.replaceResourceId { id ->
        if (!isHighTwoByte(id, 0x7f000000)) return@replaceResourceId null
        when (res.getResourceTypeName(id)) {
          "drawable",
          "mipmap" -> addAllFiles(id, "${name}_${i++}", apkBuilder)
          "color" -> apkBuilder.addColor(res.getColor(id, null))
          "dimen" -> apkBuilder.addDimen(res.getDimension(id))
          else -> null
        }
      }
      apkBuilder.addDrawable(editor.toStream(), name, ".compiledXML")
    } else apkBuilder.addDrawable(stream, name)
  }

  fun copyFallbacks(name: String, xml: StringBuilder, apkBuilder: ApkBuilder) {
    info.apply {
      copyFallback(iconBacks, "iconback", "${name}_0", xml, apkBuilder)
      copyFallback(iconUpons, "iconupon", "${name}_1", xml, apkBuilder)
      copyFallback(iconMasks, "iconmask", "${name}_2", xml, apkBuilder)
      xml.append("<scale factor=\"$iconScale\" />")
    }
  }

  private fun copyFallback(
    imgNameList: List<String>,
    tag: String,
    name: String,
    xml: StringBuilder,
    apkBuilder: ApkBuilder,
  ) {
    if (imgNameList.isEmpty()) return
    xml.append("<$tag")
    imgNameList.forEachIndexed { i, imgName ->
      val newName = "${name}_$i"
      xml.append(" img$i=\"$newName\" ")
      addAllFiles(getDrawableId(imgName), newName, apkBuilder)
    }
    xml.append("/>")
  }

  override fun equals(other: Any?) = other is IconPack && other.pack == pack

  override fun hashCode() = pack.hashCode()

  companion object {
    fun genIconFrom(
      res: Resources,
      baseIcon: Drawable,
      iconFallback: IconFallback?,
      config: IconPackConfig,
    ) =
      com.richardluo.globalIconPack.iconPack.source.genIconFrom(
        res,
        baseIcon,
        iconFallback.withConfig(config)?.orNullIfEmpty(),
      )
  }
}
