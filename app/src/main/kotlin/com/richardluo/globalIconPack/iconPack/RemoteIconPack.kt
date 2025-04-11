package com.richardluo.globalIconPack.iconPack

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.content.ComponentName
import android.content.res.Resources
import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.iconPack.database.FallbackSettings
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.iconPack.database.getBlob
import com.richardluo.globalIconPack.iconPack.database.useFirstRow
import com.richardluo.globalIconPack.reflect.Resources.getDrawableForDensity
import com.richardluo.globalIconPack.utils.ReflectHelper
import com.richardluo.globalIconPack.utils.call
import com.richardluo.globalIconPack.utils.getOrPutNullable
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
            IconFallback(FallbackSettings.from(it.getBlob("fallback")), ::getIcon, config)
              .orNullIfEmpty()
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
          ?.let {
            iconEntryList.add(it)
            iconEntryList.size - 1
          }
      }
    }

  override fun getId(cnList: List<ComponentName>): Array<Int?> =
    synchronized(indexMap) {
      val (hits, misses) = cnList.indices.partition { indexMap.contains(cnList[it]) }
      arrayOfNulls<Int?>(cnList.size).apply {
        hits.forEach { this[it] = indexMap[cnList[it]] }
        if (misses.isNotEmpty())
          contentResolver
            .query(
              IconPackProvider.ICON,
              null,
              null,
              arrayOf(
                pack,
                iconPackAsFallback.toString(),
                *misses.map { cnList[it].flattenToString() }.toTypedArray(),
              ),
              null,
            )
            ?.let { IconsCursorWrapper.useUnwrap(it, misses.size) }
            ?.forEachIndexed { i, entry ->
              val index = misses[i]
              val id =
                if (entry != null) {
                  iconEntryList.add(entry)
                  iconEntryList.size - 1
                } else null
              indexMap[cnList[index]] = id
              this[index] = id
            } ?: run { misses.forEach { this[it] = null } }
      }
    }

  override fun getIconEntry(id: Int): IconEntry? = iconEntryList.getOrNull(id)

  private inner class PackResources(inputPack: String = "") {
    val pack: String = inputPack.ifEmpty { this@RemoteIconPack.pack }
    val res: Resources =
      if (inputPack.isEmpty()) this@RemoteIconPack.res else getResources(inputPack)
  }

  override fun getIconNotAdaptive(entry: IconEntry, iconDpi: Int) =
    if (entry is IconEntryFromOtherPack)
      entry.getIcon { getIcon(PackResources(entry.pack), entry.entry, iconDpi) }
    else getIcon(PackResources(), entry, iconDpi)

  override fun getIcon(name: String, iconDpi: Int) = getIcon(PackResources(), name, iconDpi)

  private fun getIcon(pr: PackResources, entry: IconEntry, iconDpi: Int) =
    if (entry is IconEntryWithId) entry.getIconWithId { getIconWithDrawableId(pr, it, iconDpi) }
    else entry.getIcon { getIcon(pr, it, iconDpi) }

  private fun getIcon(pr: PackResources, name: String, iconDpi: Int = 0) =
    getDrawableId(pr, name).takeIf { it != 0 }?.let { getIconWithDrawableId(pr, it, iconDpi) }

  private fun getIconWithDrawableId(pr: PackResources, id: Int, iconDpi: Int = 0) =
    getDrawableForDensity(pr.res, id, iconDpi, null)

  @SuppressLint("DiscouragedApi")
  private fun getDrawableId(pr: PackResources, name: String) =
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
