package com.richardluo.globalIconPack

import com.richardluo.globalIconPack.WorldPreference.getReadablePref
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class NoForceShape : Hook {
  override fun onHookPixelLauncher(lpp: LoadPackageParam) {
    if (getReadablePref().getBoolean("noForceShape", true)) {
      val baseIconFactory =
        XposedHelpers.findClass("com.android.launcher3.icons.BaseIconFactory", lpp.classLoader)
      XposedBridge.hookAllMethods(
        baseIconFactory,
        "normalizeAndWrapToAdaptiveIcon",
        object : XC_MethodHook() {
          override fun beforeHookedMethod(param: MethodHookParam) {
            param.args[1] = false
          }
        },
      )
    }
  }
}
