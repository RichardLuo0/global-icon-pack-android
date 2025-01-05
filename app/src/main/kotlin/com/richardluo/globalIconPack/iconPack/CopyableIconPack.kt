package com.richardluo.globalIconPack.iconPack

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.utils.getOrNull
import com.richardluo.globalIconPack.utils.ifNotEmpty
import com.richardluo.globalIconPack.utils.isHighTwoByte
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import org.xmlpull.v1.XmlPullParser

class CopyableIconPack(pref: SharedPreferences, pack: String, resources: Resources) :
  LocalIconPack(pref, pack, resources) {

  fun copyIconTo(
    iconEntry: IconEntry,
    component: String,
    name: String,
    xml: StringBuilder,
    addColor: (Int) -> String,
    write: (InputStream, String) -> Unit,
  ) =
    iconEntry.copyTo(component, name, xml) { resName, newName ->
      getDrawableId(resName).takeIf { it != 0 }?.let { writeAllFiles(it, newName, addColor, write) }
    }

  @SuppressLint("DiscouragedApi")
  private fun writeAllFiles(
    resId: Int,
    newName: String,
    addColor: (Int) -> String,
    write: (InputStream, String) -> Unit,
  ) {
    val parser =
      runCatching { resources.getXml(resId) }
        .getOrNull { write(resources.openRawResource(resId), newName) } ?: return
    val xml = StringBuilder()
    var index = 0
    var isAndroidNSSet = false
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
      when (parser.eventType) {
        XmlPullParser.END_DOCUMENT -> break
        XmlPullParser.START_TAG -> {
          xml.append(
            "<${parser.namespace.ifNotEmpty { "android:" } }${parser.name} ${if (!isAndroidNSSet) {
              isAndroidNSSet = true
              "xmlns:android=\"http://schemas.android.com/apk/res/android\" "} else ""}"
          )
          for (i in 0 until parser.attributeCount) {
            val name = parser.getAttributeName(i)
            val fullName = "${parser.getAttributeNamespace(i).ifNotEmpty { "android:" }}$name"
            val id = parser.getAttributeResourceValue(i, 0)
            if (id != 0 && isHighTwoByte(id, 0x7F000000)) {
              when (resources.getResourceTypeName(id)) {
                "drawable",
                "mipmap" -> {
                  val newRes = "${newName}_${index++}"
                  writeAllFiles(id, newRes, addColor, write)
                  xml.append("$fullName=\"@drawable/$newRes\"")
                }
                "color" ->
                  xml.append("$fullName=\"@color/${addColor(resources.getColor(id,null))}\"")
              }
            } else {
              val value = parser.getAttributeValue(i)
              xml.append("$fullName=\"$value\"")
            }
            xml.append(' ')
          }
          xml.append(">")
        }
        XmlPullParser.TEXT -> xml.append(parser.text)
        XmlPullParser.END_TAG -> xml.append("</${parser.name}>")
      }
    }
    write(xml.toString().byteInputStream(), "$newName.xml")
    parser.close()
  }

  fun copyFallbacks(name: String, xml: StringBuilder, write: (InputStream, String) -> Unit) {
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
    write: (InputStream, String) -> Unit,
  ) {
    if (bitmapList.isEmpty()) return
    xml.append("<$tag")
    bitmapList.forEachIndexed { i, bitmap ->
      xml.append(" img$i=\"${name}_$i\" ")
      ByteArrayOutputStream().use {
        bitmap.compress(CompressFormat.PNG, 100, it)
        write(ByteArrayInputStream(it.toByteArray()), "${name}_$i")
      }
    }
    xml.append("/>")
  }

  fun getAllIconEntries() =
    indexMap.map { (cn, id) -> iconEntryList.getOrNull(id)?.let { Pair(cn, it) } }.filterNotNull()
}
