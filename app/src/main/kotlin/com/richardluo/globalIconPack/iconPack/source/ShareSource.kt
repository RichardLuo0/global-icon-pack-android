package com.richardluo.globalIconPack.iconPack.source

import android.app.AndroidAppHelper
import android.content.ComponentName
import android.graphics.drawable.Drawable
import androidx.core.database.getIntOrNull
import com.richardluo.globalIconPack.AppPref
import com.richardluo.globalIconPack.BuildConfig
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.iconPack.IconPackDB
import com.richardluo.globalIconPack.iconPack.IconPackDB.GetIconCol
import com.richardluo.globalIconPack.iconPack.model.FallbackSettings
import com.richardluo.globalIconPack.iconPack.model.IconEntry
import com.richardluo.globalIconPack.iconPack.model.IconFallback
import com.richardluo.globalIconPack.iconPack.model.IconPackConfig
import com.richardluo.globalIconPack.iconPack.model.IconResolver
import com.richardluo.globalIconPack.iconPack.model.ResourceOwner
import com.richardluo.globalIconPack.iconPack.model.defaultIconPackConfig
import com.richardluo.globalIconPack.iconPack.useFirstRow
import com.richardluo.globalIconPack.iconPack.useMapToArray
import com.richardluo.globalIconPack.utils.AppPreference
import com.richardluo.globalIconPack.utils.getOrPut
import java.util.Collections

class ShareSource(pack: String, config: IconPackConfig = defaultIconPackConfig) :
  Source, ResourceOwner(pack) {
  companion object {
    const val DATABASE_PATH = "/data/misc/${BuildConfig.APPLICATION_ID}/iconPack.db"
  }

  private val iconPackAsFallback = config.iconPackAsFallback
  private val iconFallback: IconFallback?

  private val indexMap = mutableMapOf<ComponentName, Int?>()
  private val iconEntryList = Collections.synchronizedList(mutableListOf<IconResolver>())

  private val db =
    IconPackDB(
      AndroidAppHelper.currentApplication(),
      AppPreference.get().getString(AppPref.PATH.key, DATABASE_PATH)!!,
    )
  private val resourcesMap = mutableMapOf<String, ResourceOwner>()

  init {
    iconFallback =
      if (config.iconFallback)
        db.getFallbackSettings(pack).useFirstRow {
          IconFallback(FallbackSettings.from(it.getBlob(0)), ::getIcon, config).orNullIfEmpty()
        }
      else null
  }

  override fun getId(cn: ComponentName) = getId(listOf(cn)).getOrNull(0)

  override fun getId(cnList: List<ComponentName>) =
    synchronized(indexMap) {
      indexMap.getOrPut(cnList) { misses, getKey ->
        db.getIcon(pack, misses, iconPackAsFallback).useMapToArray(misses.size) { i, c ->
          if (c.getIntOrNull(GetIconCol.Fallback.ordinal) == 1) {
            // Is fallback
            val cn = getComponentName(getKey(i).packageName)
            if (indexMap.contains(cn)) return@useMapToArray indexMap[cn]
            else {
              iconEntryList.add(IconResolver.from(c))
              (iconEntryList.size - 1).also { indexMap[cn] = it }
            }
          } else {
            iconEntryList.add(IconResolver.from(c))
            iconEntryList.size - 1
          }
        }
      }
    }

  override fun getIconEntry(id: Int): IconEntry? = iconEntryList.getOrNull(id)

  override fun getIconNotAdaptive(entry: IconEntry, iconDpi: Int) =
    if (entry is IconResolver) entry.getIcon(::getResourceOwner, iconDpi)
    else entry.getIcon { getIcon(it, iconDpi) }

  override fun getIcon(name: String, iconDpi: Int) = getIconByName(name, iconDpi)

  private fun getResourceOwner(pack: String) =
    if (pack.isEmpty()) this else resourcesMap.getOrPut(pack) { ResourceOwner(pack) }

  override fun genIconFrom(baseIcon: Drawable) = genIconFrom(res, baseIcon, iconFallback)
}
