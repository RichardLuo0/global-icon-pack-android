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
import androidx.core.graphics.drawable.toBitmap
import com.richardluo.globalIconPack.PrefDef
import com.richardluo.globalIconPack.PrefKey
import com.richardluo.globalIconPack.iconPack.database.FallbackSettings
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.reflect.Resources.getDrawableForDensity
import com.richardluo.globalIconPack.utils.IconHelper
import com.richardluo.globalIconPack.utils.isInMod

abstract class IconPack(pref: SharedPreferences, val pack: String, val resources: Resources) {
  protected class IconFallback(
    val iconBacks: List<Bitmap>,
    val iconUpons: List<Bitmap>,
    val iconMasks: List<Bitmap>,
    val iconScale: Float = 1f,
  )

  protected val iconPackAsFallback =
    pref.getBoolean(PrefKey.ICON_PACK_AS_FALLBACK, PrefDef.ICON_PACK_AS_FALLBACK)
  protected var iconFallback: IconFallback? = null

  protected fun initFallbackSettings(fs: FallbackSettings, pref: SharedPreferences) {
    iconFallback =
      IconFallback(
        fs.iconBacks.mapNotNull { getIcon(it)?.toBitmap() },
        fs.iconUpons.mapNotNull { getIcon(it)?.toBitmap() },
        fs.iconMasks.mapNotNull { getIcon(it)?.toBitmap() },
        if (pref.getBoolean(PrefKey.OVERRIDE_ICON_FALLBACK, PrefDef.OVERRIDE_ICON_FALLBACK))
          pref.getFloat(PrefKey.ICON_PACK_SCALE, PrefDef.ICON_PACK_SCALE)
        else fs.iconScale,
      )
  }

  abstract fun getIconEntry(id: Int): IconEntry?

  abstract fun getId(cn: ComponentName): Int?

  fun getIconEntry(cn: ComponentName) = getId(cn)?.let { getIconEntry(it) }

  open fun getIcon(entry: IconEntry, iconDpi: Int) =
    entry
      .getIcon { getIcon(it, iconDpi) }
      ?.let { if (useUnClipAdaptive) IconHelper.makeAdaptive(it) else it }

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
    iconFallback?.run {
      if (useUnClipAdaptive)
        IconHelper.processIcon(
          resources,
          baseIcon,
          iconBacks.randomOrNull(),
          iconUpons.randomOrNull(),
          iconMasks.randomOrNull(),
          iconScale,
        )
      else
        IconHelper.processIconToBitmap(
          resources,
          baseIcon,
          iconBacks.randomOrNull(),
          iconUpons.randomOrNull(),
          iconMasks.randomOrNull(),
          iconScale,
        )
    } ?: baseIcon
}

fun getComponentName(info: PackageItemInfo): ComponentName =
  if (info is ApplicationInfo) getComponentName(info.packageName)
  else ComponentName(info.packageName, info.name)

fun getComponentName(packageName: String): ComponentName = ComponentName(packageName, "")

/**
 * UnClipAdaptiveIconDrawable does not work correctly for some apps. It maybe clipped by adaptive
 * icon mask or shows black background, but we don't know how to efficiently convert Bitmap to Path.
 */
val useUnClipAdaptive: Boolean by lazy {
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
