package com.richardluo.globalIconPack

import com.richardluo.globalIconPack.utils.ReflectHelper
import com.richardluo.globalIconPack.utils.rGet
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class BypassShortcutPermission {
  companion object {
    fun onHookAndroid(lpp: LoadPackageParam) {
      ReflectHelper.hookAllMethodsOrLog(
        ReflectHelper.findClass("com.android.server.pm.LauncherAppsService\$LauncherAppsImpl", lpp)
          ?: return,
        "ensureShortcutPermission",
        object : XC_MethodHook() {
          override fun beforeHookedMethod(param: MethodHookParam) {
            if (BuildConfig.APPLICATION_ID == param.args.rGet(-1)) param.result = null
          }
        },
      )
      ReflectHelper.hookAllMethodsOrLog(
        ReflectHelper.findClass("com.android.server.pm.ShortcutService", lpp) ?: return,
        "canSeeAnyPinnedShortcut",
        object : XC_MethodHook() {
          override fun beforeHookedMethod(param: MethodHookParam) {
            if (BuildConfig.APPLICATION_ID == param.args[0]) param.result = true
          }
        },
      )
    }
  }
}
