package com.richardluo.globalIconPack.iconPack

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.content.ComponentName
import android.content.res.Resources
import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.iconPack.database.FallbackSettings
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.reflect.Resources.getDrawableForDensity
import com.richardluo.globalIconPack.utils.getBlob
import com.richardluo.globalIconPack.utils.getFirstRow
import com.richardluo.globalIconPack.utils.getOrPutNullable
import com.richardluo.globalIconPack.utils.getString

class IconEntryFromOtherPack(val entry: IconEntry, val pack: String) : IconEntry by entry

class RemoteIconPack(
  pack: String,
  resources: Resources,
  config: IconPackConfig = IconPackConfig(),
) : IconPack(pack, resources) {
  private val iconPackAsFallback = config.iconPackAsFallback
  private val iconFallback: IconFallback?

  private val indexMap = mutableMapOf<ComponentName, Int?>()
  private val iconEntryList = mutableListOf<IconEntry>()

  private val contentResolver = AndroidAppHelper.currentApplication().contentResolver
  private val idCache = mutableMapOf<String, Int>()
  private val resourcesMap = mutableMapOf<String, Resources>()

  init {
    iconFallback =
      if (config.iconFallback)
        contentResolver
          .query(IconPackProvider.FALLBACKS, null, null, arrayOf(pack), null)
          ?.getFirstRow {
            IconFallback(
                FallbackSettings.from(it.getBlob("fallback")),
                ::getIcon,
                config.scale,
                config.scaleOnlyForeground,
              )
              .orNullIfEmpty()
          }
      else null
  }

  override fun getId(cn: ComponentName): Int? =
    indexMap.getOrPutNullable(cn) {
      contentResolver
        .query(
          IconPackProvider.ICONS,
          null,
          null,
          arrayOf(pack, cn.packageName, cn.className, iconPackAsFallback.toString()),
          null,
        )
        ?.getFirstRow {
          val entry =
            if (it.getColumnIndex("type") > 0) IconEntryWithId.fromCursor(it)
            else IconEntry.from(it.getBlob("entry"))
          val pack = it.getString("pack")
          iconEntryList.add(if (pack.isEmpty()) entry else IconEntryFromOtherPack(entry, pack))
          iconEntryList.size - 1
        }
    }

  override fun getIconEntry(id: Int): IconEntry? = iconEntryList.getOrNull(id)

  private inner class PackResources(inputPack: String = "") {
    val pack: String = inputPack.ifEmpty { this@RemoteIconPack.pack }
    val res: Resources = if (inputPack.isEmpty()) resources else getResources(inputPack)
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
