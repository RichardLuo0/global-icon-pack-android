package com.richardluo.globalIconPack.reflect

import de.robv.android.xposed.XposedHelpers

object ApplicationPackageManager {
  val clazz: Class<*> by lazy {
    XposedHelpers.findClass("android.app.ApplicationPackageManager", null)
  }
}
