package com.richardluo.globalIconPack.ui.model

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ShortcutInfo
import com.richardluo.globalIconPack.iconPack.source.getComponentName

abstract class IconInfo(val componentName: ComponentName, val label: String) {

  override fun equals(other: Any?): Boolean {
    return other is IconInfo && componentName == other.componentName
  }

  override fun hashCode() = componentName.hashCode()
}

class AppIconInfo(componentName: ComponentName, label: String, val info: ApplicationInfo) :
  IconInfo(componentName, label) {
  constructor(
    context: Context,
    info: ApplicationInfo,
  ) : this(
    getComponentName(info.packageName),
    info.loadLabel(context.packageManager).toString(),
    info,
  )
}

class ActivityIconInfo(componentName: ComponentName, label: String, val info: ActivityInfo) :
  IconInfo(componentName, label) {
  constructor(
    context: Context,
    info: ActivityInfo,
  ) : this(
    ComponentName(info.packageName, info.name),
    if (info.nonLocalizedLabel != null || info.labelRes != 0)
      info.loadLabel(context.packageManager).toString()
    else info.name.substringAfterLast("."),
    info,
  )
}

class ShortcutIconInfo(val info: ShortcutInfo) :
  IconInfo(getComponentName(info), info.getLabel()?.toString() ?: info.id)

private fun ShortcutInfo.getLabel() = shortLabel?.ifEmpty { longLabel }
