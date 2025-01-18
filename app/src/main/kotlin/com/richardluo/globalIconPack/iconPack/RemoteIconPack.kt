package com.richardluo.globalIconPack.iconPack

import android.app.AndroidAppHelper
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.res.Resources
import android.net.Uri
import com.richardluo.globalIconPack.PrefDef
import com.richardluo.globalIconPack.PrefKey
import com.richardluo.globalIconPack.iconPack.database.FallbackSettings
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.utils.getBlob
import com.richardluo.globalIconPack.utils.getFirstRow
import com.richardluo.globalIconPack.utils.getOrPutNullable

class RemoteIconPack(pref: SharedPreferences, pack: String, resources: Resources) :
  IconPack(pref, pack, resources) {
  private val indexMap = mutableMapOf<ComponentName, Int?>()
  private val iconEntryList = mutableListOf<IconEntry>()

  private val contentResolver = AndroidAppHelper.currentApplication().contentResolver

  init {
    if (pref.getBoolean(PrefKey.ICON_FALLBACK, PrefDef.ICON_FALLBACK))
      contentResolver
        .query(
          Uri.parse("content://${IconPackProvider.AUTHORITIES}/${IconPackProvider.FALLBACKS}"),
          null,
          null,
          arrayOf(pack),
          null,
        )
        ?.getFirstRow { FallbackSettings.from(it.getBlob("fallback")) }
        ?.let { initFallbackSettings(it, pref) }
  }

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
