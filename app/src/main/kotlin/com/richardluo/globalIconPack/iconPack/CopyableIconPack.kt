package com.richardluo.globalIconPack.iconPack

import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.utils.AXMLEditor
import com.richardluo.globalIconPack.utils.isHighTwoByte
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

interface ApkBuilder {
  fun addColor(color: Int): Int

  fun addDrawable(input: InputStream, name: String, suffix: String = ""): Int

  fun addDimen(dimen: Float): Int
}

class CopyableIconPack(pref: SharedPreferences, pack: String, resources: Resources) :
  LocalIconPack(pref, pack, resources) {

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
    copyFallback(iconBacks, "iconback", "${name}_0", xml, apkBuilder)
    copyFallback(iconUpons, "iconupon", "${name}_1", xml, apkBuilder)
    copyFallback(iconMasks, "iconmask", "${name}_2", xml, apkBuilder)
    xml.append("<scale factor=\"$iconScale\" />")
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

  fun getAllIconEntries() =
    indexMap.map { (cn, id) -> iconEntryList.getOrNull(id)?.let { Pair(cn, it) } }.filterNotNull()
}
