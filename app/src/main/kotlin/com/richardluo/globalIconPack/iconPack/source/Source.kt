package com.richardluo.globalIconPack.iconPack.source

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageItemInfo
import android.content.pm.ShortcutInfo
import android.content.res.Resources
import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.iconPack.model.IconEntry
import com.richardluo.globalIconPack.iconPack.model.IconFallback
import com.richardluo.globalIconPack.utils.IconHelper

interface Source {
  fun getId(cn: ComponentName): Int?

  fun getId(cnList: List<ComponentName>): List<Int?>

  fun getIconEntry(id: Int): IconEntry?

  fun getIconEntry(cn: ComponentName) = getId(cn)?.let { getIconEntry(it) }

  fun getIconNotAdaptive(entry: IconEntry, iconDpi: Int = 0): Drawable?

  fun getIcon(entry: IconEntry, iconDpi: Int = 0) =
    getIconNotAdaptive(entry, iconDpi)?.let { IconHelper.makeAdaptive(it) }

  fun getIcon(id: Int, iconDpi: Int) = getIconEntry(id)?.let { getIcon(it, iconDpi) }

  fun getIcon(name: String, iconDpi: Int = 0): Drawable?

  fun genIconFrom(baseIcon: Drawable): Drawable

  fun maskIconFrom(baseIcon: Drawable): Drawable
}

fun genIconFrom(res: Resources, baseIcon: Drawable, iconFallback: IconFallback?) =
  iconFallback?.run {
    IconHelper.processIcon(
      baseIcon,
      res,
      iconBacks.randomOrNull()?.newDrawable(),
      iconUpons.randomOrNull()?.newDrawable(),
      iconMasks.randomOrNull()?.newDrawable(),
      iconScale,
      scaleOnlyForeground,
      backAsAdaptiveBack,
      nonAdaptiveScale,
      convertToAdaptive,
    )
  } ?: baseIcon

fun maskIconFrom(res: Resources, baseIcon: Drawable, iconMasks: List<Drawable>?) =
  iconMasks?.run {
    IconHelper.processIcon(
      baseIcon,
      res,
      null,
      null,
      iconMasks.randomOrNull()?.newDrawable(),
      1f,
      scaleOnlyForeground = true,
      backAsAdaptiveBack = true,
      nonAdaptiveScale = 1f,
      convertToAdaptive = true,
    )
  } ?: baseIcon

private fun Drawable.newDrawable() = this.constantState?.newDrawable() ?: this

fun getComponentName(info: PackageItemInfo) =
  if (info is ApplicationInfo) getComponentName(info.packageName)
  else ComponentName(info.packageName, info.fullClassName)

fun getComponentName(packageName: String) = ComponentName(packageName, "")

fun getComponentName(shortcut: ShortcutInfo) = ComponentName("${shortcut.`package`}@", shortcut.id)

fun ComponentName.isSamePackage(cn: ComponentName) =
  packageName.removeSuffix("@") == cn.packageName.removeSuffix("@")

private val PackageItemInfo.fullClassName: String
  get() = if (name == null) "" else if (name.startsWith(".")) packageName + name else name
