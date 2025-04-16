package com.richardluo.globalIconPack.iconPack

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageItemInfo
import android.content.pm.ShortcutInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import com.richardluo.globalIconPack.iconPack.database.FallbackSettings
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.utils.IconHelper
import kotlin.collections.orEmpty

class IconFallback(
  val iconBacks: List<Bitmap>,
  val iconUpons: List<Bitmap>,
  val iconMasks: List<Bitmap>,
  val iconScale: Float = 1f,
  val scaleOnlyForeground: Boolean,
  val nonAdaptiveScale: Float,
  val convertToAdaptive: Boolean,
) {
  constructor(
    fs: FallbackSettings,
    getIcon: (String) -> Drawable?,
    config: IconPackConfig,
  ) : this(
    fs.iconBacks.mapNotNull { getIcon(it)?.toBitmap() },
    fs.iconUpons.mapNotNull { getIcon(it)?.toBitmap() },
    fs.iconMasks.mapNotNull { getIcon(it)?.toBitmap() },
    config.scale ?: fs.iconScale,
    config.scaleOnlyForeground,
    config.nonAdaptiveScale,
    config.convertToAdaptive,
  )

  fun isEmpty() =
    iconBacks.isEmpty() &&
      iconUpons.isEmpty() &&
      iconMasks.isEmpty() &&
      iconScale == 1f &&
      nonAdaptiveScale == 1f &&
      convertToAdaptive == false

  fun orNullIfEmpty() = if (isEmpty()) null else this
}

fun IconFallback?.withConfig(config: IconPackConfig) =
  IconFallback(
    this?.iconBacks.orEmpty(),
    this?.iconUpons.orEmpty(),
    this?.iconMasks.orEmpty(),
    config.scale ?: this?.iconScale ?: 1f,
    config.scaleOnlyForeground,
    config.nonAdaptiveScale,
    config.convertToAdaptive,
  )

abstract class IconPack(val pack: String, val res: Resources) {

  abstract fun getId(cn: ComponentName): Int?

  abstract fun getId(cnList: List<ComponentName>): Array<Int?>

  abstract fun getIconEntry(id: Int): IconEntry?

  fun getIconEntry(cn: ComponentName) = getId(cn)?.let { getIconEntry(it) }

  protected abstract fun getIconNotAdaptive(entry: IconEntry, iconDpi: Int): Drawable?

  fun getIcon(entry: IconEntry, iconDpi: Int) =
    getIconNotAdaptive(entry, iconDpi)?.let { IconHelper.makeAdaptive(it) }

  fun getIcon(id: Int, iconDpi: Int) = getIconEntry(id)?.let { getIcon(it, iconDpi) }

  abstract fun getIcon(name: String, iconDpi: Int = 0): Drawable?

  abstract fun genIconFrom(baseIcon: Drawable): Drawable

  protected fun genIconFrom(baseIcon: Drawable, iconFallback: IconFallback?) =
    iconFallback?.run {
      IconHelper.processIcon(
        baseIcon,
        res,
        iconBacks.randomOrNull(),
        iconUpons.randomOrNull(),
        iconMasks.randomOrNull(),
        iconScale,
        scaleOnlyForeground,
        nonAdaptiveScale,
        convertToAdaptive,
      )
    } ?: baseIcon
}

fun getComponentName(info: PackageItemInfo) =
  if (info is ApplicationInfo) getComponentName(info.packageName)
  else ComponentName(info.packageName, info.name)

fun getComponentName(packageName: String) = ComponentName(packageName, "")

fun getComponentName(shortcut: ShortcutInfo) = ComponentName("${shortcut.`package`}@", shortcut.id)
