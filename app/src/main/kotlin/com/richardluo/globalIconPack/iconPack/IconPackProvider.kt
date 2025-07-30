package com.richardluo.globalIconPack.iconPack

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.StrictMode
import androidx.core.net.toUri
import com.richardluo.globalIconPack.BuildConfig
import com.richardluo.globalIconPack.utils.InstanceManager.get
import com.richardluo.globalIconPack.utils.getOrNull
import com.richardluo.globalIconPack.utils.log
import com.richardluo.globalIconPack.utils.unflattenFromString

class IconPackProvider : ContentProvider() {
  companion object {
    const val AUTHORITIES = "${BuildConfig.APPLICATION_ID}.IconPackProvider"
    val ICON = "content://$AUTHORITIES/ICON".toUri()
    val FALLBACK = "content://$AUTHORITIES/FALLBACK".toUri()
  }

  private var iconPackDB: IconPackDB? = null

  override fun onCreate(): Boolean {
    runCatching { iconPackDB = get { IconPackDB(context!!) }.value }
    return true
  }

  override fun query(
    uri: Uri,
    projection: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    sortOrder: String?,
  ): Cursor? {
    val iconPackDB = iconPackDB ?: return null
    selectionArgs ?: return null
    return strictModeAllowThreadDiskReads {
      runCatching {
          when (uri) {
            FALLBACK ->
              if (selectionArgs.isNotEmpty()) iconPackDB.getFallbackSettings(selectionArgs[0])
              else null
            ICON ->
              if (selectionArgs.size >= 3) {
                iconPackDB.getIcon(
                  selectionArgs[0],
                  selectionArgs.drop(2).mapNotNull { unflattenFromString(it) },
                  selectionArgs[1].toBoolean(),
                )
              } else null
            else -> null
          }
        }
        .getOrNull { log(it) }
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

private inline fun <T> strictModeAllowThreadDiskReads(crossinline block: () -> T): T {
  val oldPolicy = StrictMode.allowThreadDiskReads()
  val result = block()
  StrictMode.setThreadPolicy(oldPolicy)
  return result
}
