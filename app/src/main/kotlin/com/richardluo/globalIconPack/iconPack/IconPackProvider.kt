package com.richardluo.globalIconPack.iconPack

import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import com.richardluo.globalIconPack.iconPack.database.ReadableIconPackDB
import com.richardluo.globalIconPack.utils.getOrNull
import com.richardluo.globalIconPack.utils.logInApp

class IconPackProvider : ContentProvider() {
  private val iconPackDB by lazy { ReadableIconPackDB(context!!.getDatabasePath("iconPack.db")) }

  companion object {
    const val AUTHORITIES = "com.richardluo.globalIconPack.IconPackProvider"
    const val ICONS = "ICONS"
    const val FALLBACKS = "FALLBACKS"
  }

  override fun onCreate(): Boolean {
    context!!.startForegroundService(Intent(context!!, KeepAliveService::class.java))
    return true
  }

  override fun query(
    uri: Uri,
    projection: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    sortOrder: String?,
  ) =
    when (uri.path?.removePrefix("/")) {
      FALLBACKS ->
        if (selectionArgs != null && selectionArgs.isNotEmpty())
          runCatching { iconPackDB.getFallbackSettings(selectionArgs[0]) }
            .getOrNull { logInApp(it) }
        else null
      ICONS ->
        if (selectionArgs != null && selectionArgs.size >= 4)
          runCatching {
              iconPackDB.getIcon(
                selectionArgs[0],
                ComponentName(selectionArgs[1], selectionArgs[2]),
                selectionArgs[3].toBoolean(),
              )
            }
            .getOrNull { logInApp(it) }
        else null
      else -> null
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
