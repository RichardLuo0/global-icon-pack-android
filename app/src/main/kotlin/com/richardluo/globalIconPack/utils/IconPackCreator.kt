package com.richardluo.globalIconPack.utils

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.iconPack.ApkBuilder
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
  class IconPackApkBuilder(
    private val packageName: String,
    private val context: Context,
    private val workDir: DocumentFile,
  ) : ApkBuilder {
    private val contentResolver = context.contentResolver
    private val res = workDir.createDirectorySafe("res")
    private val drawable = res.createDirectorySafe("drawable")
    private val values = res.createDirectorySafe("values")
    private val xml = res.createDirectorySafe("xml")

    private val resIds = StringBuilder()
    private val valuesXML = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><resources>")

    private class Ref(val id: Int, val name: String)

    private inner class RefMap<T>(
      private val type: String,
      private val initId: Int,
      private val toString: (T) -> String,
    ) : LinkedHashMap<T, Ref>() {
      private var index = 0

      fun getOrPut(key: T) =
        getOrPut(key) {
          val name = "${type}_$index"
          val id = initId + index
          resIds.append("$packageName:$type/$name = 0x${id.toHexString()}\n")
          index++
          Ref(id, name)
        }

      fun build() {
        map { valuesXML.append("<$type name=\"${it.value.name}\">${toString(it.key)}</$type>") }
      }
    }

    private var drawableId = 0x7f010000

    override fun addDrawable(input: InputStream, name: String, suffix: String): Int {
      val id = drawableId++
      input.writeTo(drawable.createFileAndOpenStream(contentResolver, "", name + suffix))
      resIds.append("$packageName:drawable/$name = 0x${id.toHexString()}\n")
      return id
    }

    private val colorMap = RefMap<Int>("color", 0x7f020000) { "#${ it.toHexString()}" }

    override fun addColor(color: Int) = colorMap.getOrPut(color).id

    private val dimenMap = RefMap<Float>("dimen", 0x7f030000) { "${it}dp" }

    override fun addDimen(dimen: Float) = dimenMap.getOrPut(dimen).id

    fun addXML(content: String) {
      xml.createFileAndOpenStream(contentResolver, "text/xml", "appfilter.xml").writeText(content)
    }

    fun build(label: String) {
      colorMap.build()
      dimenMap.build()
      values
        .createFileAndOpenStream(contentResolver, "text/xml", "values.xml")
        .writeText(valuesXML.append("</resources>").toString())

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
  }

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
    val workDir = fromTreeUri(context, uri)
    workDir.listFiles().forEach { file -> file.delete() }

    val apkBuilder = IconPackApkBuilder(packageName, context, workDir)

    val appfilterXML = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><resources>")
    val baseIconPack = getIconPack(basePack)
    baseIconPack.copyFallbacks("icon_fallback", appfilterXML, apkBuilder)
    if (installedAppsOnly)
      newIcons.entries.forEachIndexed { i, (cn, entry) ->
        if (entry == null) return@forEachIndexed
        val iconName = "icon_${i}"
        getIconPack(entry.pack)
          .copyIcon(entry.entry, cn.flattenToString(), iconName, appfilterXML, apkBuilder)
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
          appfilterXML,
          apkBuilder,
        )
      }
    apkBuilder.addXML(appfilterXML.append("</resources>").toString())

    apkBuilder.build(label)
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
