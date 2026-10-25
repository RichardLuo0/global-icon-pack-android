package com.richardluo.globalIconPack.iconPack.source

import android.app.Application
import android.content.ComponentName
import android.database.sqlite.SQLiteDatabase
import android.graphics.drawable.Drawable
import androidx.core.database.getIntOrNull
import com.richardluo.globalIconPack.AppPref
import com.richardluo.globalIconPack.BuildConfig
import com.richardluo.globalIconPack.iconPack.IconPackDB
import com.richardluo.globalIconPack.iconPack.IconPackDB.GetIconCol
import com.richardluo.globalIconPack.iconPack.model.FallbackOwner
import com.richardluo.globalIconPack.iconPack.model.FallbackSettings
import com.richardluo.globalIconPack.iconPack.model.IconEntry
import com.richardluo.globalIconPack.iconPack.model.IconPackConfig
import com.richardluo.globalIconPack.iconPack.model.IconResolver
import com.richardluo.globalIconPack.iconPack.model.ResourceOwner
import com.richardluo.globalIconPack.iconPack.model.defaultIconPackConfig
import com.richardluo.globalIconPack.iconPack.useFirstRow
import com.richardluo.globalIconPack.iconPack.useMapToArray
import com.richardluo.globalIconPack.utils.AppPreference
import com.richardluo.globalIconPack.utils.Logger.logD
import com.richardluo.globalIconPack.utils.Logger.logE
import com.richardluo.globalIconPack.utils.ThemeColorMonitor
import com.richardluo.globalIconPack.utils.getOrPut
import io.github.libxposed.api.XposedInterface
import java.util.Collections

class ShareSource(
  context: Application,
  xposed: XposedInterface,
  pack: String,
  config: IconPackConfig = defaultIconPackConfig,
) : Source, ResourceOwner(context, pack) {
  companion object {
    const val DATABASE_PATH = "/data/misc/${BuildConfig.APPLICATION_ID}/iconPack.db"
  }

  private val iconPackAsFallback = config.iconPackAsFallback

  private val indexMap = mutableMapOf<ComponentName, Int?>()
  private val iconEntryList = Collections.synchronizedList(mutableListOf<IconResolver>())

  private val db =
    context(xposed) {
      IconPackDB(
        context,
        AppPreference.get().getString(AppPref.PATH.key, DATABASE_PATH)!!,
        SQLiteDatabase.OPEN_READONLY,
      )
    }
  private val resourcesMap = mutableMapOf<String, ResourceOwner>()

  private val fallbackOwner: FallbackOwner

  private val themeColorMonitor = ThemeColorMonitor(context)

  init {
    fallbackOwner =
      FallbackOwner(
        config,
        db.getFallbackSettings(pack).useFirstRow {
          FallbackSettings.from(it.getBlob(0))
        },
      ) {
        context(xposed) {
          getIcon(it)
        }
      }
  }

  override fun updateResources() {
    themeColorMonitor.update {
      res = context.packageManager.getResourcesForApplication(pack)
      resourcesMap.clear()
      fallbackOwner.rebuild()
      logD("resource updated")
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
    if (pack.isEmpty()) this else resourcesMap.getOrPut(pack) { ResourceOwner(context, pack) }

  context(xposed: XposedInterface)
  override fun genIconFrom(baseIcon: Drawable) =
    genIconFrom(res, baseIcon, fallbackOwner.iconFallback)

  context(xposed: XposedInterface)
  override fun maskIconFrom(baseIcon: Drawable) =
    maskIconFrom(res, baseIcon, fallbackOwner.iconFallback?.iconMasks)
}
