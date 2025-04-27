package com.richardluo.globalIconPack.xposed

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import com.richardluo.globalIconPack.utils.ReflectHelper
import com.richardluo.globalIconPack.utils.asType
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class NoForceShape(val drawWholeIconForTransparentBackgroundInSplashScreen: Boolean) : Hook {
  override fun onHookSystemUI(lpp: LoadPackageParam) {
    // Draw the whole icon even if the background is transparent
    // https://cs.android.com/android/platform/superproject/+/android15-qpr1-release:frameworks/base/libs/WindowManager/Shell/src/com/android/wm/shell/startingsurface/SplashscreenContentDrawer.java;l=676
    if (drawWholeIconForTransparentBackgroundInSplashScreen) {
      val iconColor =
        ReflectHelper.findClass(
          "com.android.wm.shell.startingsurface.SplashscreenContentDrawer\$ColorCache\$IconColor",
          lpp,
        )
      val mBgColorF = ReflectHelper.findField(iconColor, "mBgColor") ?: return
      val mIsBgComplexF = ReflectHelper.findField(iconColor, "mIsBgComplex") ?: return
      ReflectHelper.hookAllConstructors(
        iconColor,
        object : XC_MethodHook() {
          override fun afterHookedMethod(param: MethodHookParam) {
            val mBgColor = mBgColorF.get(param.thisObject).asType<Int>() ?: return
            val mIsBgComplex = mIsBgComplexF.get(param.thisObject).asType<Boolean>() ?: return
            if (!mIsBgComplex && mBgColor == Color.TRANSPARENT)
              mIsBgComplexF.set(param.thisObject, true)
          }
        },
      )
    }
  }

  override fun onHookSettings(lpp: LoadPackageParam) {
    // Fix accessibility
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
    val adaptiveIcon =
      ReflectHelper.findClass("com.android.settingslib.widget.AdaptiveIcon", lpp) ?: return
    val extractOriIcon =
      object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
          val icon = param.result.asType<Drawable?>() ?: return
          if (adaptiveIcon.isAssignableFrom(icon::class.java))
            icon.asType<LayerDrawable>()?.getDrawable(1)?.let { param.result = it }
        }
      }
    ReflectHelper.hookAllMethods(
      ReflectHelper.findClass(
        "com.android.settings.accessibility.AccessibilityActivityPreference",
        lpp,
      ),
      "getA11yActivityIcon",
      extractOriIcon,
    )
    ReflectHelper.hookAllMethods(
      ReflectHelper.findClass(
        "com.android.settings.accessibility.AccessibilityServicePreference",
        lpp,
      ),
      "getA11yServiceIcon",
      extractOriIcon,
    )
  }
}
