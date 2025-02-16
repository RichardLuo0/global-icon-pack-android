package com.richardluo.globalIconPack.iconPack

import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageItemInfo
import android.content.pm.ShortcutInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.iconPack.database.FallbackSettings
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.utils.IconHelper

abstract class IconPack(pref: SharedPreferences, val pack: String, val resources: Resources) {
  protected class IconFallback(
    val iconBacks: List<Bitmap>,
    val iconUpons: List<Bitmap>,
    val iconMasks: List<Bitmap>,
    val iconScale: Float = 1f,
  )

  protected val iconPackAsFallback = pref.get(Pref.ICON_PACK_AS_FALLBACK)
  protected var iconFallback: IconFallback? = null

  protected fun initFallbackSettings(fs: FallbackSettings, pref: SharedPreferences) {
    iconFallback =
      IconFallback(
        fs.iconBacks.mapNotNull { getIcon(it)?.toBitmap() },
        fs.iconUpons.mapNotNull { getIcon(it)?.toBitmap() },
        fs.iconMasks.mapNotNull { getIcon(it)?.toBitmap() },
        if (pref.get(Pref.OVERRIDE_ICON_FALLBACK)) pref.get(Pref.ICON_PACK_SCALE) else fs.iconScale,
      )
  }

  abstract fun getId(cn: ComponentName): Int?

  abstract fun getIconEntry(id: Int): IconEntry?

  fun getIconEntry(cn: ComponentName) = getId(cn)?.let { getIconEntry(it) }

  protected abstract fun getIconNotAdaptive(entry: IconEntry, iconDpi: Int): Drawable?

  fun getIcon(entry: IconEntry, iconDpi: Int) =
    getIconNotAdaptive(entry, iconDpi)?.let { IconHelper.makeAdaptive(it) }

  fun getIcon(id: Int, iconDpi: Int) = getIconEntry(id)?.let { getIcon(it, iconDpi) }

  abstract fun getIcon(name: String, iconDpi: Int = 0): Drawable?

  fun genIconFrom(baseIcon: Drawable) =
    iconFallback?.run {
      IconHelper.processIcon(
        resources,
        baseIcon,
        iconBacks.randomOrNull(),
        iconUpons.randomOrNull(),
        iconMasks.randomOrNull(),
        iconScale,
      )
    } ?: baseIcon
}

fun getComponentName(info: PackageItemInfo) =
  if (info is ApplicationInfo) getComponentName(info.packageName)
  else ComponentName(info.packageName, info.name)

fun getComponentName(packageName: String) = ComponentName(packageName, "")

fun getComponentName(shortcut: ShortcutInfo) = ComponentName("${shortcut.`package`}@", shortcut.id)
