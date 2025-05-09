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

  fun getId(cnList: List<ComponentName>): Array<Int?>

  fun getIconEntry(id: Int): IconEntry?

  fun getIconEntry(cn: ComponentName) = getId(cn)?.let { getIconEntry(it) }

  fun getIconNotAdaptive(entry: IconEntry, iconDpi: Int = 0): Drawable?

  fun getIcon(entry: IconEntry, iconDpi: Int = 0) =
    getIconNotAdaptive(entry, iconDpi)?.let { IconHelper.makeAdaptive(it) }

  fun getIcon(id: Int, iconDpi: Int) = getIconEntry(id)?.let { getIcon(it, iconDpi) }

  fun getIcon(name: String, iconDpi: Int = 0): Drawable?

  fun genIconFrom(baseIcon: Drawable): Drawable
}

fun genIconFrom(res: Resources, baseIcon: Drawable, iconFallback: IconFallback?) =
  iconFallback?.run {
    IconHelper.processIcon(
      baseIcon,
      res,
      iconBacks.randomOrNull(),
      iconUpons.randomOrNull(),
      iconMasks.randomOrNull(),
      iconScale,
      scaleOnlyForeground,
      backAsAdaptiveBack,
      nonAdaptiveScale,
      convertToAdaptive,
    )
  } ?: baseIcon

fun getComponentName(info: PackageItemInfo) =
  if (info is ApplicationInfo) getComponentName(info.packageName)
  else ComponentName(info.packageName, info.name)

fun getComponentName(packageName: String) = ComponentName(packageName, "")

fun getComponentName(shortcut: ShortcutInfo) = ComponentName("${shortcut.`package`}@", shortcut.id)
