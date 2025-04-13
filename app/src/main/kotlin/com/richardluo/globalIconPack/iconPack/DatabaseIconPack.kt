package com.richardluo.globalIconPack.iconPack

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.content.ComponentName
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.core.database.getIntOrNull
import com.richardluo.globalIconPack.BuildConfig
import com.richardluo.globalIconPack.iconPack.IconsCursorWrapper.EntryInfo
import com.richardluo.globalIconPack.iconPack.database.FallbackSettings
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.iconPack.database.IconPackDB
import com.richardluo.globalIconPack.iconPack.database.IconPackDB.GetIconColumn
import com.richardluo.globalIconPack.iconPack.database.getBlob
import com.richardluo.globalIconPack.iconPack.database.getString
import com.richardluo.globalIconPack.iconPack.database.useFirstRow
import com.richardluo.globalIconPack.iconPack.database.useMapToArray
import com.richardluo.globalIconPack.reflect.Resources.getDrawableForDensity
import com.richardluo.globalIconPack.utils.getOrPut
import com.richardluo.globalIconPack.utils.mapIndexed
import java.util.Collections
import kotlin.text.ifEmpty

class DatabaseIconPack(
  pack: String,
  res: Resources,
  config: IconPackConfig = defaultIconPackConfig,
) : IconPack(pack, res) {
  companion object {
    const val DATABASE_PATH = "/data/misc/${BuildConfig.APPLICATION_ID}/iconPack.db"
  }

  private val iconPackAsFallback = config.iconPackAsFallback
  private val iconFallback: IconFallback?

  private val indexMap = mutableMapOf<ComponentName, Int?>()
  private val iconEntryList = Collections.synchronizedList(mutableListOf<IconEntry>())

  private val db: IconPackDB = IconPackDB(AndroidAppHelper.currentApplication(), DATABASE_PATH)
  private val idCache = mutableMapOf<String, Int>()
  private val resourcesMap = mutableMapOf<String, Resources>()

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
            val entry = IconEntry.from(c.getBlob(GetIconColumn.Entry))
            val pack = c.getString(GetIconColumn.Pack)
            val fallback = c.getIntOrNull(GetIconColumn.Fallback.ordinal) == 1
            EntryInfo(if (pack.isEmpty()) entry else IconEntryFromOtherPack(entry, pack), fallback)
          }
          .mapIndexed { i, info ->
            if (info != null) {
              iconEntryList.add(info.entry)
              (iconEntryList.size - 1).also {
                if (info.fallback) indexMap[getComponentName(getKey(i).packageName)] = it
              }
            } else null
          }
      }
    }

  override fun getIconEntry(id: Int): IconEntry? = iconEntryList.getOrNull(id)

  private inner class PackRes(val pack: String, val res: Resources)

  private fun createPackRes(pack: String = "") =
    PackRes(pack.ifEmpty { this.pack }, if (pack.isEmpty()) this.res else getResources(pack))

  override fun getIconNotAdaptive(entry: IconEntry, iconDpi: Int) =
    entry.getIcon {
      getIcon(
        if (entry is IconEntryFromOtherPack) createPackRes(entry.pack) else createPackRes(),
        it,
        iconDpi,
      )
    }

  override fun getIcon(name: String, iconDpi: Int) = getIcon(createPackRes(), name, iconDpi)

  private fun getIcon(pr: PackRes, name: String, iconDpi: Int = 0) =
    getDrawableId(pr, name)
      .takeIf { it != 0 }
      ?.let { getDrawableForDensity(pr.res, it, iconDpi, null) }

  @SuppressLint("DiscouragedApi")
  private fun getDrawableId(pr: PackRes, name: String) =
    idCache.getOrPut("${pr.pack}/$name") { pr.res.getIdentifier(name, "drawable", pr.pack) }

  private fun getResources(pack: String) =
    resourcesMap.getOrPut(pack) {
      AndroidAppHelper.currentApplication().packageManager.getResourcesForApplication(pack)
    }

  override fun genIconFrom(baseIcon: Drawable) = genIconFrom(baseIcon, iconFallback)
}
