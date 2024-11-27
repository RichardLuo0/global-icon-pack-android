package com.richardluo.globalIconPack

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class SplashScreenHook : Hook {
  override fun onHookSystemUI(lpp: LoadPackageParam) {
    val adaptiveForegroundDrawable =
      XposedHelpers.findClass(
        "com.android.wm.shell.startingsurface.SplashscreenIconDrawableFactory\$AdaptiveForegroundDrawable",
        lpp.classLoader,
      )
    val mForegroundDrawable =
      XposedHelpers.findField(adaptiveForegroundDrawable, "mForegroundDrawable")

    XposedBridge.hookAllMethods(
      adaptiveForegroundDrawable,
      "draw",
      object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam): Any? {
          (mForegroundDrawable[param.thisObject] as Drawable).draw((param.args[0] as Canvas))
          return null
        }
      },
    )
  }
}
