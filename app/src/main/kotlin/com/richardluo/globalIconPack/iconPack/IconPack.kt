package com.richardluo.globalIconPack.iconPack

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageItemInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.PrefKey
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.reflect.Resources.getDrawableForDensity
import com.richardluo.globalIconPack.utils.IconHelper
import com.richardluo.globalIconPack.utils.isInMod

abstract class IconPack(pref: SharedPreferences, val pack: String, val resources: Resources) {
  protected val iconFallback = pref.getBoolean(PrefKey.ICON_FALLBACK, true)
  protected val enableOverrideIconFallback = pref.getBoolean(PrefKey.OVERRIDE_ICON_FALLBACK, false)
  // Fallback settings from icon pack
  protected lateinit var iconBacks: List<Bitmap>
  protected lateinit var iconUpons: List<Bitmap>
  protected lateinit var iconMasks: List<Bitmap>
  protected var iconScale: Float =
    if (iconFallback && enableOverrideIconFallback) {
      pref.getFloat(PrefKey.ICON_PACK_SCALE, 1f)
    } else 1f

  protected val iconPackAsFallback = pref.getBoolean(PrefKey.ICON_PACK_AS_FALLBACK, false)

  abstract fun getIconEntry(id: Int): IconEntry?

  abstract fun getId(cn: ComponentName): Int?

  fun getIconEntry(cn: ComponentName) = getId(cn)?.let { getIconEntry(it) }

  fun getIcon(iconEntry: IconEntry, iconDpi: Int) =
    iconEntry
      .getIcon { getIcon(it, iconDpi) }
      ?.let { if (useAdaptive) IconHelper.makeAdaptive(it) else it }

  fun getIcon(id: Int, iconDpi: Int): Drawable? = getIconEntry(id)?.let { getIcon(it, iconDpi) }

  fun getIcon(resName: String, iconDpi: Int = 0) =
    getDrawableId(resName)
      .takeIf { it != 0 }
      ?.let {
        if (isInMod) getDrawableForDensity(resources, it, iconDpi, null)
        else resources.getDrawableForDensity(it, iconDpi, null)
      }

  private val idCache = mutableMapOf<String, Int>()

  @SuppressLint("DiscouragedApi")
  protected fun getDrawableId(name: String) =
    idCache.getOrPut(name) { resources.getIdentifier(name, "drawable", pack) }

  fun genIconFrom(baseIcon: Drawable) =
    if (useAdaptive)
      IconHelper.processIcon(
        resources,
        baseIcon,
        iconBacks.randomOrNull(),
        iconUpons.randomOrNull(),
        iconMasks.randomOrNull(),
        iconScale,
      )
    else if (iconFallback) {
      // Do not pass global scale because BitmapDrawable will scale anyway
      IconHelper.processIconToBitmap(
        resources,
        baseIcon,
        iconBacks.randomOrNull(),
        iconUpons.randomOrNull(),
        iconMasks.randomOrNull(),
        iconScale,
      )
    } else baseIcon
}

fun getComponentName(info: PackageItemInfo): ComponentName =
  if (info is ApplicationInfo) getComponentName(info.packageName)
  else ComponentName(info.packageName, info.name)

fun getComponentName(packageName: String): ComponentName = ComponentName(packageName, "")

/**
 * CustomAdaptiveIconDrawable does not work correctly for some apps. It maybe clipped by adaptive
 * icon mask or shows black background, but we don't know how to efficiently convert Bitmap to Path.
 */
private val useAdaptive: Boolean by lazy {
  if (!isInMod) false
  else
    when (val packageName = AndroidAppHelper.currentPackageName()) {
      "com.android.settings" -> false
      "com.android.systemui" -> false
      "com.android.intentresolver" -> true
      else -> {
        val intent =
          Intent().apply {
            setPackage(packageName)
            setAction(Intent.ACTION_MAIN)
            addCategory(Intent.CATEGORY_HOME)
          }
        AndroidAppHelper.currentApplication()
          .packageManager
          .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
          .let { it?.activityInfo != null }
      }
    }
}
