package com.richardluo.globalIconPack.iconPack

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.Resources
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.drawable.Drawable
import android.net.Uri
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.iconPack.database.IconPackDB
import com.richardluo.globalIconPack.iconPack.database.NormalIconEntry
import com.richardluo.globalIconPack.utils.getBlob
import com.richardluo.globalIconPack.utils.getInt
import com.richardluo.globalIconPack.utils.getOrNull
import com.richardluo.globalIconPack.utils.getString
import com.richardluo.globalIconPack.utils.log
import java.io.ByteArrayInputStream
import java.io.DataInputStream

class IconEntryWithId(val entry: IconEntry, private val id: Int) : IconEntry by entry {
  private enum class Type {
    Normal,
    Clock,
  }

  fun getIconWithId(getIconFromId: (Int) -> Drawable?) =
    if (id == 0) null else getIcon { getIconFromId(id) }

  companion object {
    fun toCursor(cur: Cursor, getDrawableId: (pack: String, name: String) -> Int): Cursor? {
      val data = cur.getBlob("entry")
      val pack = cur.getString("pack")
      return MatrixCursor(arrayOf("pack", "type", "id", "args")).apply {
        DataInputStream(ByteArrayInputStream(data)).use {
          when (it.readByte()) {
            IconEntry.Type.Normal.ordinal.toByte() -> {
              val name = it.readUTF()
              val id = getDrawableId(pack, name).takeIf { it != 0 } ?: return null
              addRow(arrayOf(pack, Type.Normal.ordinal, id, name))
              cur.close()
            }
            IconEntry.Type.Clock.ordinal.toByte() -> {
              val id = getDrawableId(pack, it.readUTF()).takeIf { it != 0 } ?: return null
              addRow(arrayOf(pack, Type.Clock.ordinal, id, data))
              cur.close()
            }
            else -> return cur
          }
        }
      }
    }

    fun fromCursor(c: Cursor) =
      IconEntryWithId(
        when (c.getInt("type")) {
          Type.Normal.ordinal -> NormalIconEntry(c.getString("args"))
          Type.Clock.ordinal -> IconEntry.from(c.getBlob("args"))
          else -> throw Exception("Unknown icon entry with id")
        },
        c.getInt("id"),
      )
  }
}

class IconPackProvider : ContentProvider() {
  companion object {
    const val AUTHORITIES = "com.richardluo.globalIconPack.IconPackProvider"
    const val ICONS = "ICONS"
    const val FALLBACKS = "FALLBACKS"
  }

  private lateinit var iconPackDB: IconPackDB

  override fun onCreate(): Boolean {
    iconPackDB = IconPackDB(context!!)
    return true
  }

  override fun query(
    uri: Uri,
    projection: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    sortOrder: String?,
  ): Cursor? {
    startForegroundAsNeeded()
    return runCatching {
        when (uri.path?.removePrefix("/")) {
          FALLBACKS ->
            if (selectionArgs != null && selectionArgs.isNotEmpty())
              iconPackDB.getFallbackSettings(selectionArgs[0])
            else null
          ICONS ->
            if (selectionArgs != null && selectionArgs.size >= 4)
              iconPackDB
                .getIcon(
                  selectionArgs[0],
                  ComponentName(selectionArgs[1], selectionArgs[2]),
                  selectionArgs[3].toBoolean(),
                )
                .takeIf { it.moveToFirst() }
                ?.let {
                  IconEntryWithId.toCursor(it) { pack, name ->
                    getDrawableId(pack.ifEmpty { selectionArgs[0] }, name)
                  }
                }
            else null
          else -> null
        }
      }
      .getOrNull { log(it) }
  }

  private val idCacheMap = mutableMapOf<String, Int>()

  @SuppressLint("DiscouragedApi")
  private fun getDrawableId(pack: String, name: String) =
    idCacheMap.getOrPut("$pack/$name") { getResources(pack).getIdentifier(name, "drawable", pack) }

  private val resourcesMap = mutableMapOf<String, Resources>()

  private fun getResources(pack: String) =
    resourcesMap.getOrPut(pack) { context!!.packageManager.getResourcesForApplication(pack) }

  private fun startForegroundAsNeeded() {
    KeepAliveService.startForeground(context!!)
  }

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
