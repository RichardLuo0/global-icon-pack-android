package com.richardluo.globalIconPack.utils

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.ui.model.IconEntryWithPack
import com.richardluo.globalIconPack.ui.model.IconPack
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter

object IconPackCreator {
  interface ApkBuilder {
    fun addColor(color: Int): Int

    fun addDrawable(input: InputStream, name: String, suffix: String = ""): Int

    fun addDimen(dimen: Float): Int

    fun addAppfilter(record: String)
  }

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
    private val appfilterXML =
      StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><resources>")

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

    override fun addAppfilter(record: String) {
      appfilterXML.append(record)
    }

    fun build(label: String, icon: String = "@android:drawable/sym_def_app_icon") {
      colorMap.build()
      dimenMap.build()

      values
        .createFileAndOpenStream(contentResolver, "text/xml", "values.xml")
        .writeText(valuesXML.append("</resources>").toString())

      workDir
        .createFileAndOpenStream(contentResolver, "text/plain", "resIds.txt")
        .writeText(resIds.toString())

      xml
        .createFileAndOpenStream(contentResolver, "text/xml", "appfilter.xml")
        .writeText(appfilterXML.append("</resources>").toString())

      context.resources
        .openRawResource(R.raw.icon_pack_manifest)
        .replaceAndWriteTo(
          workDir.createFileAndOpenStream(contentResolver, "text/xml", "AndroidManifest.xml"),
          packageName,
          label,
          icon,
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

  class FolderNotEmptyException : Exception()

  class Progress(val current: Int = 0, val total: Int = 1, val info: String = "") {
    val percentage: Float
      get() = current.toFloat() / total

    fun advance(info: String) = Progress(current + 1, total, info)

    override fun equals(other: Any?) =
      other is Progress && other.current == current && other.total == total

    override fun hashCode() = current
  }

  fun createIconPack(
    context: Context,
    uri: Uri,
    icon: IconEntryWithPack?,
    label: String,
    packageName: String,
    baseIconPack: IconPack,
    newIcons: Map<ComponentName, IconEntryWithPack?>,
    installedAppsOnly: Boolean,
    onProgress: (Progress) -> Unit,
  ) {
    val workDir = fromTreeUri(context, uri)
    if (workDir.listFiles().isNotEmpty()) throw FolderNotEmptyException()

    val apkBuilder = IconPackApkBuilder(packageName, context, workDir)

    val total =
      1 +
        (if (installedAppsOnly) newIcons.entries.size else baseIconPack.iconEntryMap.size) +
        (if (icon != null) 1 else 0) +
        1

    var progress = Progress(total = total)
    fun advanceProgressAndReport(info: String) {
      progress = progress.advance(info).also { onProgress(it) }
    }

    val packIdMapMap = mutableMapOf<IconPack, MutableMap<Int, Int>>()
    fun IconEntryWithPack.copyIcon(cn: ComponentName, iconName: String) =
      pack.copyIcon(
        entry,
        cn.flattenToString(),
        iconName,
        packIdMapMap.getOrPut(pack) { mutableMapOf() },
        apkBuilder,
      )

    advanceProgressAndReport("icon_fallback")
    baseIconPack.copyFallbacks(
      "icon_fallback",
      packIdMapMap.getOrPut(baseIconPack) { mutableMapOf() },
      apkBuilder,
    )

    var i = 0
    if (installedAppsOnly)
      newIcons.forEach { (cn, entry) ->
        advanceProgressAndReport(cn.packageName)
        if (entry == null) return@forEach
        entry.copyIcon(cn, "icon_${i++}")
      }
    else
      baseIconPack.iconEntryMap.forEach { (cn, entry) ->
        advanceProgressAndReport(cn.packageName)
        val newEntry =
          if (newIcons.containsKey(cn)) newIcons[cn] ?: return@forEach
          else IconEntryWithPack(entry, baseIconPack)
        newEntry.copyIcon(cn, "icon_${i++}")
      }

    if (icon != null) {
      advanceProgressAndReport("ic_launcher")
      icon.copyIcon(ComponentName(packageName, ""), "ic_launcher")
      advanceProgressAndReport("building...")
      apkBuilder.build(label, "@drawable/ic_launcher")
    } else {
      advanceProgressAndReport("building...")
      apkBuilder.build(label)
    }
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
