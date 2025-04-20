package com.richardluo.globalIconPack.iconPack

import android.app.AndroidAppHelper
import android.content.ComponentName
import android.graphics.drawable.Drawable
import androidx.core.database.getIntOrNull
import com.richardluo.globalIconPack.BuildConfig
import com.richardluo.globalIconPack.iconPack.database.FallbackSettings
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.iconPack.database.IconPackDB
import com.richardluo.globalIconPack.iconPack.database.IconPackDB.GetIconColumn
import com.richardluo.globalIconPack.iconPack.database.useFirstRow
import com.richardluo.globalIconPack.iconPack.database.useMapToArray
import com.richardluo.globalIconPack.utils.getOrPut
import com.richardluo.globalIconPack.utils.mapIndexed
import java.util.Collections
import kotlin.to

class DatabaseSource(pack: String, config: IconPackConfig = defaultIconPackConfig) :
  Source, ResourceOwner(pack) {
  companion object {
    const val DATABASE_PATH = "/data/misc/${BuildConfig.APPLICATION_ID}/iconPack.db"
  }

  private val iconPackAsFallback = config.iconPackAsFallback
  private val iconFallback: IconFallback?

  private val indexMap = mutableMapOf<ComponentName, Int?>()
  private val iconEntryList = Collections.synchronizedList(mutableListOf<IconResolver>())

  private val db: IconPackDB = IconPackDB(AndroidAppHelper.currentApplication(), DATABASE_PATH)
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

  override fun getId(cnList: List<ComponentName>): Array<Int?> =
    synchronized(indexMap) {
      indexMap.getOrPut(cnList) { misses, getKey ->
        db
          .getIcon(pack, misses, iconPackAsFallback)
          .useMapToArray(misses.size) { c ->
            IconResolver.from(c) to (c.getIntOrNull(GetIconColumn.Fallback.ordinal) == 1)
          }
          .mapIndexed { i, info ->
            if (info != null) {
              iconEntryList.add(info.first)
              (iconEntryList.size - 1).also {
                if (info.second) indexMap[getComponentName(getKey(i).packageName)] = it
              }
            } else null
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
