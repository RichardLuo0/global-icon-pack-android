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
import com.richardluo.globalIconPack.iconPack.database.ClockIconEntry
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.iconPack.database.IconPackDB
import com.richardluo.globalIconPack.iconPack.database.NormalIconEntry
import com.richardluo.globalIconPack.utils.getBlob
import com.richardluo.globalIconPack.utils.getInt
import com.richardluo.globalIconPack.utils.getOrNull
import com.richardluo.globalIconPack.utils.getString
import com.richardluo.globalIconPack.utils.log

class IconEntryWithId(private val id: Int, val entry: IconEntry) : IconEntry(entry.name) {
  enum class Type {
    Normal,
    Clock,
  }

  fun getIconWithId(getIconFromId: (Int) -> Drawable?) = getIcon { getIconFromId(id) }

  override fun getIcon(getIcon: (String) -> Drawable?) = entry.getIcon(getIcon)

  override fun isCalendar() = entry.isCalendar()

  override fun isClock() = entry.isClock()

  companion object {
    fun toCursor(it: Cursor, getDrawableId: (pack: String, name: String) -> Int): MatrixCursor? {
      val entryBlob = it.getBlob("entry")
      val entry = from(entryBlob)
      val pack = it.getString("pack")
      return MatrixCursor(arrayOf("pack", "type", "id", "args")).apply {
        when (entry) {
          is NormalIconEntry ->
            addRow(arrayOf(pack, Type.Normal.ordinal, getDrawableId(pack, entry.name), entry.name))
          is ClockIconEntry ->
            addRow(arrayOf(pack, Type.Clock.ordinal, getDrawableId(pack, entry.name), entryBlob))
          else -> return null
        }
      }
    }

    fun fromCursor(c: Cursor) =
      IconEntryWithId(
        c.getInt("id"),
        when (c.getInt("type")) {
          Type.Normal.ordinal -> NormalIconEntry(c.getString("args"))
          Type.Clock.ordinal -> from(c.getBlob("args"))
          else -> throw Exception("Unknown icon entry with id")
        },
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
                    ?.apply { it.close() } ?: it
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

  private var isServiceStarted = false

  private fun startForegroundAsNeeded() {
    if (!isServiceStarted) {
      KeepAliveService.startForeground(context!!)
      isServiceStarted = true
    }
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
