package com.richardluo.globalIconPack.iconPack

import android.app.AndroidAppHelper
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.res.Resources
import android.database.Cursor
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import com.richardluo.globalIconPack.iconPack.database.FallbackSettings
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.utils.getOrPutNullable

private fun <T> Cursor.getFirstRow(block: (Cursor) -> T) =
  this.takeIf { it.moveToFirst() }?.use(block)

private fun Cursor.getBlob(name: String) = this.getBlob(getColumnIndexOrThrow(name))

class RemoteIconPack(pref: SharedPreferences, getResources: (packageName: String) -> Resources?) :
  IconPack(pref, getResources) {
  private val indexMap = mutableMapOf<ComponentName, Int?>()
  private val iconEntryList = mutableListOf<IconEntry>()

  private val contentResolver = AndroidAppHelper.currentApplication().contentResolver

  init {
    contentResolver
      .query(
        Uri.parse("content://${IconPackProvider.AUTHORITIES}/${IconPackProvider.FALLBACKS}"),
        null,
        null,
        arrayOf(pack),
        null,
      )
      ?.getFirstRow { FallbackSettings.from(it.getBlob("fallback")) }
      ?.let { fs ->
        this.iconBacks = fs.iconBacks.mapNotNull { getIcon(it)?.toBitmap() }
        this.iconUpons = fs.iconUpons.mapNotNull { getIcon(it)?.toBitmap() }
        this.iconMasks = fs.iconMasks.mapNotNull { getIcon(it)?.toBitmap() }
        if (iconFallback && !enableOverrideIconFallback) this.iconScale = fs.iconScale
      }
      ?: run {
        this.iconBacks = listOf()
        this.iconUpons = listOf()
        this.iconMasks = listOf()
      }
  }

  override fun getIconEntry(cn: ComponentName) = getId(cn)?.let { iconEntryList.getOrNull(it) }

  override fun getIconEntry(id: Int): IconEntry? = iconEntryList.getOrNull(id)

  override fun getId(cn: ComponentName): Int? =
    indexMap.getOrPutNullable(cn) {
      contentResolver
        .query(
          Uri.parse("content://${IconPackProvider.AUTHORITIES}/${IconPackProvider.ICONS}"),
          null,
          null,
          arrayOf(pack, cn.packageName, cn.className, iconPackAsFallback.toString()),
          null,
        )
        ?.getFirstRow { IconEntry.from(it.getBlob("entry")) }
        ?.let {
          iconEntryList.add(it)
          iconEntryList.size - 1
        }
    }
}
