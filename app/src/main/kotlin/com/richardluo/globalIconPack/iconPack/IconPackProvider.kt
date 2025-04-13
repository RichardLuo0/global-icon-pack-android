package com.richardluo.globalIconPack.iconPack

import android.annotation.SuppressLint
import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.Resources
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.StrictMode
import androidx.core.database.getIntOrNull
import androidx.core.net.toUri
import com.richardluo.globalIconPack.BuildConfig
import com.richardluo.globalIconPack.iconPack.database.CalendarIconEntry
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.iconPack.database.IconEntry.Type
import com.richardluo.globalIconPack.iconPack.database.IconPackDB
import com.richardluo.globalIconPack.iconPack.database.IconPackDB.GetIconColumn
import com.richardluo.globalIconPack.iconPack.database.NormalIconEntry
import com.richardluo.globalIconPack.iconPack.database.getAsColumnArray
import com.richardluo.globalIconPack.iconPack.database.getBlob
import com.richardluo.globalIconPack.iconPack.database.getInt
import com.richardluo.globalIconPack.iconPack.database.getString
import com.richardluo.globalIconPack.iconPack.database.useEachRow
import com.richardluo.globalIconPack.iconPack.database.useMapToArray
import com.richardluo.globalIconPack.utils.InstanceManager.get
import com.richardluo.globalIconPack.utils.getOrNull
import com.richardluo.globalIconPack.utils.log
import com.richardluo.globalIconPack.utils.unflattenFromString
import java.io.ByteArrayInputStream
import java.io.DataInputStream

class IconEntryWithId(val entry: IconEntry, private val id: Int) : IconEntry by entry {

  fun getIconWithId(getIconFromId: (Int) -> Drawable?) =
    if (id == 0) null else getIcon { getIconFromId(id) }
}

class IconEntryFromOtherPack(val entry: IconEntry, val pack: String) : IconEntry by entry

object IconsCursorWrapper {
  private enum class Column {
    Index,
    Pack,
    Fallback,
    Type,
    Id,
    Args,
  }

  fun useWrap(c: Cursor, getDrawableId: (pack: String, name: String) -> Int): Cursor =
    MatrixCursor(getAsColumnArray<Column>()).apply {
      c.useEachRow { c ->
        val index = c.getInt(GetIconColumn.Index)
        val entry = c.getBlob(GetIconColumn.Entry)
        val pack = c.getString(GetIconColumn.Pack)
        val fallback = c.getIntOrNull(GetIconColumn.Fallback.ordinal) == 1
        DataInputStream(ByteArrayInputStream(entry)).use {
          when (it.readByte()) {
            Type.Normal.ordinal.toByte() -> {
              val name = it.readUTF()
              val id = getDrawableId(pack, name).takeIf { it != 0 } ?: return@useEachRow
              addRow(arrayOf(index, pack, fallback, Type.Normal.ordinal, id, name))
            }
            Type.Clock.ordinal.toByte() -> {
              val id = getDrawableId(pack, it.readUTF()).takeIf { it != 0 } ?: return@useEachRow
              addRow(arrayOf(index, pack, fallback, Type.Clock.ordinal, id, entry))
            }
            else -> addRow(arrayOf(index, pack, fallback, -1, 0, entry))
          }
        }
      }
    }

  class EntryInfo(val entry: IconEntry, val fallback: Boolean)

  fun useUnwrap(c: Cursor, size: Int) =
    c.useMapToArray(size, Column.Index) { c ->
      val entry =
        when (c.getInt(Column.Type)) {
          Type.Normal.ordinal ->
            IconEntryWithId(NormalIconEntry(c.getString(Column.Args)), c.getInt(Column.Id))
          Type.Clock.ordinal ->
            IconEntryWithId(CalendarIconEntry.from(c.getBlob(Column.Args)), c.getInt(Column.Id))
          else -> IconEntry.from(c.getBlob(Column.Args.ordinal))
        }
      val pack = c.getString(Column.Pack)
      EntryInfo(
        if (pack.isEmpty()) entry else IconEntryFromOtherPack(entry, pack),
        c.getInt(Column.Fallback) != 0,
      )
    }
}

class IconPackProvider : ContentProvider() {
  companion object {
    const val AUTHORITIES = "${BuildConfig.APPLICATION_ID}.IconPackProvider"
    val ICON = "content://$AUTHORITIES/ICON".toUri()
    val FALLBACK = "content://$AUTHORITIES/FALLBACK".toUri()
  }

  private lateinit var iconPackDB: IconPackDB

  override fun onCreate(): Boolean {
    iconPackDB = get { IconPackDB(context!!) }.value
    return true
  }

  override fun query(
    uri: Uri,
    projection: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    sortOrder: String?,
  ): Cursor? {
    val oldPolicy = StrictMode.allowThreadDiskReads()
    selectionArgs ?: return null
    return runCatching {
        when (uri) {
          FALLBACK ->
            if (selectionArgs.isNotEmpty()) iconPackDB.getFallbackSettings(selectionArgs[0])
            else null
          ICON ->
            if (selectionArgs.size >= 3) {
              IconsCursorWrapper.useWrap(
                iconPackDB.getIcon(
                  selectionArgs[0],
                  selectionArgs.drop(2).mapNotNull { unflattenFromString(it) },
                  selectionArgs[1].toBoolean(),
                )
              ) { pack, name ->
                getDrawableId(pack.ifEmpty { selectionArgs[0] }, name)
              }
            } else null
          else -> null
        }
      }
      .getOrNull { log(it) }
      .also { StrictMode.setThreadPolicy(oldPolicy) }
  }

  private val idCacheMap = mutableMapOf<String, Int>()

  @SuppressLint("DiscouragedApi")
  private fun getDrawableId(pack: String, name: String) =
    idCacheMap.getOrPut("$pack/$name") { getResources(pack).getIdentifier(name, "drawable", pack) }

  private val resourcesMap = mutableMapOf<String, Resources>()

  private fun getResources(pack: String) =
    resourcesMap.getOrPut(pack) { context!!.packageManager.getResourcesForApplication(pack) }

  override fun getType(uri: Uri): String? = null

  override fun insert(uri: Uri, values: ContentValues?): Uri? = null

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

  override fun update(
    uri: Uri,
    values: ContentValues?,
    selection: String?,
    selectionArgs: Array<out String>?,
  ): Int = 0
}
