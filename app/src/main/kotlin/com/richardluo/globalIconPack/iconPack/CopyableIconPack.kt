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

class CopyableIconPack(pref: SharedPreferences, pack: String, resources: Resources) :
  LocalIconPack(pref, pack, resources) {

  fun copyIcon(
    iconEntry: IconEntry,
    component: String,
    name: String,
    xml: StringBuilder,
    addColor: (color: Int) -> Int,
    addDrawable: (InputStream, String, String) -> Int,
  ) =
    iconEntry.copyTo(component, name, xml) { resName, newName ->
      getDrawableId(resName)
        .takeIf { it != 0 }
        ?.let { addAllFiles(it, newName, addColor, addDrawable) }
    }

  private fun addAllFiles(
    resId: Int,
    name: String,
    addColor: (color: Int) -> Int,
    addDrawable: (InputStream, String, String) -> Int,
  ): Int {
    val stream = resources.openRawResource(resId)
    return if (AXMLEditor.isAXML(stream)) {
      val editor = AXMLEditor(stream)
      var i = 0
      editor.replaceResourceId { id ->
        if (!isHighTwoByte(id, 0x7f000000)) return@replaceResourceId null
        when (resources.getResourceTypeName(id)) {
          "drawable",
          "mipmap" -> addAllFiles(id, "${name}_${i++}", addColor, addDrawable)
          "color" -> addColor(resources.getColor(id, null))
          else -> null
        }
      }
      addDrawable(editor.toStream(), name, ".compiledXML")
    } else addDrawable(stream, name, "")
  }

  fun copyFallbacks(
    name: String,
    xml: StringBuilder,
    write: (InputStream, String, String) -> Unit,
  ) {
    copyFallback(iconBacks, "iconback", "${name}_0", xml, write)
    copyFallback(iconUpons, "iconupon", "${name}_1", xml, write)
    copyFallback(iconMasks, "iconmask", "${name}_2", xml, write)
    xml.append("<scale factor=\"$iconScale\" />")
  }

  private fun copyFallback(
    bitmapList: List<Bitmap>,
    tag: String,
    name: String,
    xml: StringBuilder,
    write: (InputStream, String, String) -> Unit,
  ) {
    if (bitmapList.isEmpty()) return
    xml.append("<$tag")
    bitmapList.forEachIndexed { i, bitmap ->
      xml.append(" img$i=\"${name}_$i\" ")
      ByteArrayOutputStream().use {
        bitmap.compress(CompressFormat.PNG, 100, it)
        write(ByteArrayInputStream(it.toByteArray()), "${name}_$i", "")
      }
    }
    xml.append("/>")
  }

  fun getAllIconEntries() =
    indexMap.map { (cn, id) -> iconEntryList.getOrNull(id)?.let { Pair(cn, it) } }.filterNotNull()
}
