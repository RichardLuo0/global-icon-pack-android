package com.richardluo.globalIconPack.iconPack.source

import android.app.Application
import android.content.ComponentName
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import android.graphics.drawable.Drawable
import androidx.core.database.getIntOrNull
import com.richardluo.globalIconPack.AppPref
import com.richardluo.globalIconPack.BuildConfig
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
import com.richardluo.globalIconPack.utils.Logger.logE
import com.richardluo.globalIconPack.utils.getOrPut
import io.github.libxposed.api.XposedInterface
import java.util.Collections

class ShareSource(
  val context: Application,
  private val xp: XposedInterface,
  pack: String,
  private val config: IconPackConfig = defaultIconPackConfig,
) : Source, ResourceOwner(context, pack) {
  companion object {
    const val DATABASE_PATH = "/data/misc/${BuildConfig.APPLICATION_ID}/iconPack.db"
  }

  private val iconPackAsFallback = config.iconPackAsFallback
  private var iconFallback: IconFallback? = null
  private var fallbackSettings: FallbackSettings? = null

  private val indexMap = mutableMapOf<ComponentName, Int?>()
  private val iconEntryList = Collections.synchronizedList(mutableListOf<IconResolver>())

  private val db =
    context(xp) {
      IconPackDB(
        context,
        AppPreference.get().getString(AppPref.PATH.key, DATABASE_PATH)!!,
        SQLiteDatabase.OPEN_READONLY,
      )
    }
  private val resourcesMap = mutableMapOf<String, ResourceOwner>()

  init {
    context(xp) {
      iconFallback =
        if (config.iconFallback)
          db.getFallbackSettings(pack).useFirstRow {
            val settings = FallbackSettings.from(it.getBlob(0))
            fallbackSettings = settings
            IconFallback(settings, { getIcon(it) }, config).orNullIfEmpty()
          }
        else null
    }
  }

  override fun getId(cn: ComponentName) = getId(listOf(cn)).getOrNull(0)

  override fun getId(cnList: List<ComponentName>) = runCatching {
    synchronized(indexMap) {
      indexMap.getOrPut(cnList) { misses ->
        db.getIcon(pack, misses, iconPackAsFallback).useMapToArray(misses.size) { i, c ->
          val cn = misses[i]
          if (c.getIntOrNull(GetIconCol.Fallback.ordinal) == 1 || cn.className.isEmpty()) {
            // Is fallback
            val cn = getComponentName(cn.packageName)
            if (indexMap.contains(cn)) indexMap[cn]
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
  }
    .getOrElse {
      logE(it)
      List(cnList.size) { null }
    }

  override fun getIconEntry(id: Int): IconEntry? = iconEntryList.getOrNull(id)

  context(xposed: XposedInterface)
  override fun getIconNotAdaptive(entry: IconEntry, iconDpi: Int) =
    if (entry is IconResolver) entry.getIcon(::getResourceOwner, iconDpi)
    else entry.getIcon { getIcon(it, iconDpi) }

  context(xposed: XposedInterface)
  override fun getIcon(name: String, iconDpi: Int) = getIconByName(name, iconDpi)

  private fun getResourceOwner(pack: String) =
    if (pack.isEmpty()) this
    else synchronized(resourcesMap) {
      resourcesMap.getOrPut(pack) { ResourceOwner(context, pack) }
    }

  override fun updateConfiguration(config: Configuration) {
    super<ResourceOwner>.updateConfiguration(config)
    synchronized(resourcesMap) {
      resourcesMap.values.forEach { it.updateConfiguration(config) }
    }
  }

  override fun reloadResources() {
    super<ResourceOwner>.reloadResources()
    synchronized(resourcesMap) {
      resourcesMap.values.forEach { it.reloadResources() }
    }
    fallbackSettings?.let {
      context(xp) {
        iconFallback =
          if (config.iconFallback)
            IconFallback(it, { getIcon(it) }, config).orNullIfEmpty()
          else null
      }
    }
  }

  override fun genIconFrom(baseIcon: Drawable) = genIconFrom(res, baseIcon, iconFallback)

  override fun maskIconFrom(baseIcon: Drawable) =
    maskIconFrom(res, baseIcon, iconFallback?.iconMasks)
}
