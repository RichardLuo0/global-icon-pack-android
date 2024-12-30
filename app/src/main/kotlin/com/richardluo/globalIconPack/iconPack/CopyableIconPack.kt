package com.richardluo.globalIconPack.iconPack

import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

class CopyableIconPack(pref: SharedPreferences, pack: String, resources: Resources) :
  LocalIconPack(pref, pack, resources) {

  fun copyIconTo(
    iconEntry: IconEntry,
    component: String,
    name: String,
    xml: StringBuilder,
    write: (InputStream, String) -> Unit,
  ) =
    iconEntry.copyTo(component, name, xml) { resName, newName ->
      getDrawableId(resName)
        .takeIf { it != 0 }
        ?.let { write(resources.openRawResource(it), newName) }
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
