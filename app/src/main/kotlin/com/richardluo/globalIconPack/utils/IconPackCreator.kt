package com.richardluo.globalIconPack.utils

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.iconPack.CopyableIconPack
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter

object IconPackCreator {
  data class IconEntryWithPack(val entry: IconEntry, val pack: String)

  @OptIn(ExperimentalStdlibApi::class)
  fun createIconPack(
    context: Context,
    uri: Uri,
    label: String,
    packageName: String,
    basePack: String,
    newIcons: Map<ComponentName, IconEntryWithPack?>,
    installedAppsOnly: Boolean,
    getIconPack: (String) -> CopyableIconPack,
  ) {
    val contentResolver = context.contentResolver
    val workDir = fromTreeUri(context, uri)
    workDir.listFiles().forEach { file -> file.delete() }

    val res = workDir.createDirectorySafe("res")
    val drawable = res.createDirectorySafe("drawable")
    val values = res.createDirectorySafe("values")
    val xml = res.createDirectorySafe("xml")

    val resIds = StringBuilder()

    val colorsSb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><resources>")
    val initColorId = 0x7f010000
    var colorIndex = 0
    fun addColor(color: Int): Int {
      val name = "color_$colorIndex"
      val id = initColorId + colorIndex
      colorsSb.append("<color name=\"$name\">#${color.toHexString()}</color>")
      resIds.append("$packageName:color/$name = 0x${id.toHexString()}\n")
      colorIndex++
      return id
    }

    var drawableId = 0x7f020000
    fun addDrawable(input: InputStream, name: String): Int {
      val id = drawableId++
      input.writeTo(drawable.createFileAndOpenStream(contentResolver, "", name))
      resIds.append("$packageName:drawable/$name = 0x${id.toHexString()}\n")
      return id
    }

    val appFilterSb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><resources>")
    val baseIconPack = getIconPack(basePack)
    baseIconPack.copyFallbacks("icon_fallback", appFilterSb, ::addDrawable)
    if (installedAppsOnly)
      newIcons.entries.forEachIndexed { i, (cn, entry) ->
        if (entry == null) return@forEachIndexed
        val iconName = "icon_${i}"
        getIconPack(entry.pack)
          .copyIcon(
            entry.entry,
            cn.flattenToString(),
            iconName,
            appFilterSb,
            ::addColor,
            ::addDrawable,
          )
      }
    else
      baseIconPack.getAllIconEntries().forEachIndexed { i, (cn, entry) ->
        val iconName = "icon_$i"
        val newEntry =
          if (newIcons.containsKey(cn))
            if (newIcons[cn] == null) return@forEachIndexed else newIcons[cn]
          else null
        val finalIconEntry = newEntry?.entry ?: entry
        val finalIconPack = newEntry?.let { getIconPack(it.pack) } ?: baseIconPack
        finalIconPack.copyIcon(
          finalIconEntry,
          cn.flattenToString(),
          iconName,
          appFilterSb,
          ::addColor,
          ::addDrawable,
        )
      }
    appFilterSb.append("</resources>")
    xml
      .createFileAndOpenStream(contentResolver, "text/xml", "appfilter.xml")
      .writeText(appFilterSb.toString())

    colorsSb.append("</resources>")
    values
      .createFileAndOpenStream(contentResolver, "text/xml", "colors.xml")
      .writeText(colorsSb.toString())

    workDir
      .createFileAndOpenStream(contentResolver, "text/plain", "resIds.txt")
      .writeText(resIds.toString())

    context.resources
      .openRawResource(R.raw.icon_pack_manifest)
      .replaceAndWriteTo(
        workDir.createFileAndOpenStream(contentResolver, "text/xml", "AndroidManifest.xml"),
        packageName,
        label,
      )
    context.resources
      .openRawResource(R.raw.create_icon_pack)
      .writeTo(workDir.createFileAndOpenStream(contentResolver, "", "create_icon_pack.sh"))
    context.resources
      .openRawResource(R.raw.dummy)
      .writeTo(workDir.createFileAndOpenStream(contentResolver, "", "dummy.jks"))
    context.resources
      .openRawResource(R.raw.classes)
      .writeTo(workDir.createFileAndOpenStream(contentResolver, "", "classes.dex"))
  }

  private fun InputStream.writeTo(output: OutputStream) {
    use {
      output.use {
        val buffer = ByteArray(1024)
        var length: Int
        while (read(buffer).also { length = it } > 0) {
          output.write(buffer, 0, length)
        }
      }
    }
  }

  private fun InputStream.replaceAndWriteTo(output: OutputStream, vararg args: String) {
    BufferedInputStream(this).use { reader ->
      OutputStreamWriter(BufferedOutputStream(output)).use { writer ->
        var ch: Int
        while (reader.read().also { ch = it } != -1) {
          if (ch == '$'.code) writer.write(args[reader.read().toChar().digitToInt()])
          else writer.write(ch)
        }
      }
    }
  }

  private fun OutputStream.writeText(str: String) {
    OutputStreamWriter(this).use { it.write(str) }
  }

  private class FileException(path: String) : Exception("unable to create \"$path\"")

  private fun fromTreeUri(context: Context, uri: Uri) =
    DocumentFile.fromTreeUri(context, uri) ?: throw FileException(uri.toString())

  private fun DocumentFile.createDirectorySafe(displayName: String) =
    createDirectory(displayName) ?: throw FileException(displayName)

  private fun DocumentFile.createFileAndOpenStream(
    contentResolver: ContentResolver,
    mimeType: String,
    displayName: String,
  ) =
    createFile(mimeType, displayName)?.let { contentResolver.openOutputStream(it.uri) }
      ?: throw FileException(displayName)
}
