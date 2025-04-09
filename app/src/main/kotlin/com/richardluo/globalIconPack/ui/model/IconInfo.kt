package com.richardluo.globalIconPack.ui.model

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.ShortcutInfo
import com.richardluo.globalIconPack.iconPack.getComponentName

abstract class IconInfo(val componentName: ComponentName, val label: String) {

  override fun equals(other: Any?): Boolean {
    return if (other !is IconInfo) false else componentName == other.componentName
  }

  override fun hashCode() = componentName.hashCode()
}

class AppIconInfo(componentName: ComponentName, label: String, val info: ApplicationInfo) :
  IconInfo(componentName, label) {
  constructor(
    info: LauncherActivityInfo
  ) : this(info.componentName, info.label.toString(), info.applicationInfo)

  constructor(
    context: Context,
    info: ApplicationInfo,
  ) : this(
    getComponentName(info.packageName),
    info.loadLabel(context.packageManager).toString(),
    info,
  )
}

class ShortcutIconInfo(val info: ShortcutInfo) :
  IconInfo(getComponentName(info), info.getLabel()?.toString() ?: info.id)

private fun ShortcutInfo.getLabel() = shortLabel?.ifEmpty { longLabel }
