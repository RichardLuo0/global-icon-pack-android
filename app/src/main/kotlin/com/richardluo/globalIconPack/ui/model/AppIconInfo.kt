package com.richardluo.globalIconPack.ui.model

import android.content.ComponentName
import android.content.pm.ShortcutInfo

open class AppIconInfo(val componentName: ComponentName, val label: String) {

  override fun equals(other: Any?): Boolean {
    return if (other !is AppIconInfo) false else componentName == other.componentName
  }

  override fun hashCode() = componentName.hashCode()
}

class ShortcutIconInfo(componentName: ComponentName, val shortcut: ShortcutInfo) :
  AppIconInfo(componentName, shortcut.shortLabel.toString())
