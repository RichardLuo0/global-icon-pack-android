package com.richardluo.globalIconPack.iconPack

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.net.Uri
import com.richardluo.globalIconPack.PrefDef
import com.richardluo.globalIconPack.PrefKey
import com.richardluo.globalIconPack.iconPack.database.FallbackSettings
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.reflect.Resources.getDrawableForDensity
import com.richardluo.globalIconPack.utils.IconHelper
import com.richardluo.globalIconPack.utils.getBlob
import com.richardluo.globalIconPack.utils.getFirstRow
import com.richardluo.globalIconPack.utils.getOrPutNullable
import com.richardluo.globalIconPack.utils.getString
import com.richardluo.globalIconPack.utils.isInMod

private class IconEntryFromOtherPack(val pack: String, val entry: IconEntry) :
  IconEntry(entry.name) {
  override fun getIcon(getIcon: (String) -> Drawable?) = entry.getIcon(getIcon)

  override fun copyTo(
    component: String,
    newName: String,
    xml: StringBuilder,
    copyRes: (String, String) -> Unit,
  ) = entry.copyTo(component, newName, xml, copyRes)
}

class RemoteIconPack(pref: SharedPreferences, pack: String, resources: Resources) :
  IconPack(pref, pack, resources) {
  private val resourcesMap = mutableMapOf<String, Resources>()
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
        ?.getFirstRow {
          val entry = IconEntry.from(it.getBlob("entry"))
          val pack = it.getString("pack")
          iconEntryList.add(if (pack.isEmpty()) entry else IconEntryFromOtherPack(pack, entry))
          iconEntryList.size - 1
        }
    }

  override fun getIcon(entry: IconEntry, iconDpi: Int) =
    if (entry is IconEntryFromOtherPack) {
      entry
        .getIcon { getIcon(entry.pack, entry.name, iconDpi) }
        ?.let { if (useUnClipAdaptive) IconHelper.makeAdaptive(it) else it }
    } else super.getIcon(entry, iconDpi)

  private fun getIcon(pack: String, resName: String, iconDpi: Int = 0) =
    getDrawableId(pack, resName)
      .takeIf { it != 0 }
      ?.let {
        if (isInMod) getDrawableForDensity(getResources(pack), it, iconDpi, null)
        else getResources(pack).getDrawableForDensity(it, iconDpi, null)
      }

  private val idCacheMap = mutableMapOf<String, Int>()

  @SuppressLint("DiscouragedApi")
  private fun getDrawableId(pack: String, name: String) =
    idCacheMap.getOrPut("$pack/$name") { getResources(pack).getIdentifier(name, "drawable", pack) }

  private fun getResources(pack: String) =
    resourcesMap.getOrPut(pack) {
      AndroidAppHelper.currentApplication().packageManager.getResourcesForApplication(pack)
    }
}
