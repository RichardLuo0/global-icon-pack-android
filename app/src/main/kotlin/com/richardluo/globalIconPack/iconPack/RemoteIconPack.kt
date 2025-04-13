package com.richardluo.globalIconPack.iconPack

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.content.ComponentName
import android.content.res.Resources
import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.iconPack.database.FallbackSettings
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.iconPack.database.useFirstRow
import com.richardluo.globalIconPack.reflect.Resources.getDrawableForDensity
import com.richardluo.globalIconPack.utils.ReflectHelper
import com.richardluo.globalIconPack.utils.call
import com.richardluo.globalIconPack.utils.getOrPut
import com.richardluo.globalIconPack.utils.getOrPutNullable
import com.richardluo.globalIconPack.utils.mapIndexed
import java.util.Collections

class RemoteIconPack(pack: String, res: Resources, config: IconPackConfig = defaultIconPackConfig) :
  IconPack(pack, res) {
  private val iconPackAsFallback = config.iconPackAsFallback
  private val iconFallback: IconFallback?

  private val indexMap = mutableMapOf<ComponentName, Int?>()
  private val iconEntryList = Collections.synchronizedList(mutableListOf<IconEntry>())

  private val contentResolver = AndroidAppHelper.currentApplication().contentResolver
  private val idCache = mutableMapOf<String, Int>()
  private val resourcesMap = mutableMapOf<String, Resources>()

  init {
    iconFallback =
      if (config.iconFallback)
        contentResolver
          .query(IconPackProvider.FALLBACK, null, null, arrayOf(pack), null)
          ?.useFirstRow {
            IconFallback(FallbackSettings.from(it.getBlob(0)), ::getIcon, config).orNullIfEmpty()
          }
      else null
  }

  override fun getId(cn: ComponentName) =
    synchronized(indexMap) {
      indexMap.getOrPutNullable(cn) {
        contentResolver
          .query(
            IconPackProvider.ICON,
            null,
            null,
            arrayOf(pack, iconPackAsFallback.toString(), cn.flattenToString()),
            null,
          )
          ?.let { IconsCursorWrapper.useUnwrap(it, 1).getOrNull(0) }
          ?.let { info ->
            iconEntryList.add(info.entry)
            (iconEntryList.size - 1).also {
              if (info.fallback) indexMap[getComponentName(cn.packageName)] = it
            }
          }
      }
    }

  override fun getId(cnList: List<ComponentName>): Array<Int?> =
    synchronized(indexMap) {
      indexMap.getOrPut(cnList) { misses, getKey ->
        contentResolver
          .query(
            IconPackProvider.ICON,
            null,
            null,
            arrayOf(
              pack,
              iconPackAsFallback.toString(),
              *misses.map { it.flattenToString() }.toTypedArray(),
            ),
            null,
          )
          ?.let { IconsCursorWrapper.useUnwrap(it, misses.size) }
          ?.mapIndexed { i, info ->
            if (info != null) {
              iconEntryList.add(info.entry)
              (iconEntryList.size - 1).also {
                if (info.fallback) indexMap[getComponentName(getKey(i).packageName)] = it
              }
            } else null
          } ?: arrayOfNulls(misses.size)
      }
    }

  override fun getIconEntry(id: Int): IconEntry? = iconEntryList.getOrNull(id)

  private inner class PackRes(val pack: String, val res: Resources)

  private fun createPackRes(pack: String = "") =
    PackRes(pack.ifEmpty { this.pack }, if (pack.isEmpty()) this.res else getResources(pack))

  override fun getIconNotAdaptive(entry: IconEntry, iconDpi: Int) =
    if (entry is IconEntryFromOtherPack)
      entry.getIcon { getIcon(createPackRes(entry.pack), entry.entry, iconDpi) }
    else getIcon(createPackRes(), entry, iconDpi)

  override fun getIcon(name: String, iconDpi: Int) = getIcon(createPackRes(), name, iconDpi)

  private fun getIcon(pr: PackRes, entry: IconEntry, iconDpi: Int) =
    if (entry is IconEntryWithId) entry.getIconWithId { getIconWithDrawableId(pr, it, iconDpi) }
    else entry.getIcon { getIcon(pr, it, iconDpi) }

  private fun getIcon(pr: PackRes, name: String, iconDpi: Int = 0) =
    getDrawableId(pr, name).takeIf { it != 0 }?.let { getIconWithDrawableId(pr, it, iconDpi) }

  private fun getIconWithDrawableId(pr: PackRes, id: Int, iconDpi: Int = 0) =
    getDrawableForDensity(pr.res, id, iconDpi, null)

  @SuppressLint("DiscouragedApi")
  private fun getDrawableId(pr: PackRes, name: String) =
    idCache.getOrPut("${pr.pack}/$name") { pr.res.getIdentifier(name, "drawable", pr.pack) }

  private fun getResources(pack: String) =
    resourcesMap.getOrPut(pack) {
      AndroidAppHelper.currentApplication().packageManager.getResourcesForApplication(pack)
    }

  override fun genIconFrom(baseIcon: Drawable) = genIconFrom(baseIcon, iconFallback)
}

private var waitingForBootCompleted = true

private val getSystemProperty by lazy {
  ReflectHelper.findMethodFirstMatch("android.os.SystemProperties", null, "get", String::class.java)
}

fun createRemoteIconPack(
  pack: String,
  res: Resources,
  config: IconPackConfig = defaultIconPackConfig,
): RemoteIconPack? {
  return if (
    waitingForBootCompleted && getSystemProperty?.call<String?>(null, "sys.boot_completed") != "1"
  )
    null
  else {
    waitingForBootCompleted = false
    RemoteIconPack(pack, res, config)
  }
}
