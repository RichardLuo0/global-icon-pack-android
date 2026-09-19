package com.richardluo.globalIconPack.xposed

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import com.richardluo.globalIconPack.utils.IconHelper
import com.richardluo.globalIconPack.utils.TryHookScope
import com.richardluo.globalIconPack.utils.allConstructors
import com.richardluo.globalIconPack.utils.allMethods
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.deoptimize
import com.richardluo.globalIconPack.utils.field
import com.richardluo.globalIconPack.utils.hookCompat
import com.richardluo.globalIconPack.utils.tryHook
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface

class NoForceShape(private val drawWholeIconForTransparentBackgroundInSplashScreen: Boolean) :
  Hook {
context(xposed: XposedInterface)
 override fun onHookSystemUI(param: XposedModuleInterface.PackageReadyParam) {
    // Draw the whole icon even if the background is transparent
    // https://cs.android.com/android/platform/superproject/+/android15-qpr1-release:frameworks/base/libs/WindowManager/Shell/src/com/android/wm/shell/startingsurface/SplashscreenContentDrawer.java;l=676
    if (drawWholeIconForTransparentBackgroundInSplashScreen) {
      tryHook("drawWholeIconForTransparentBackgroundInSplashScreen") {
        tryDo { hookIconColor(param) }
        tryDo { hookBgColorTester(param) }
      }
    }
  }

  /**
   * Patch IconColor after it is constructed. Preferred, because it changes nothing else, but it
   * only works while the constructor still exists as a method.
   */
  private fun TryHookScope<Unit>.hookIconColor(param: XposedModuleInterface.PackageReadyParam) {
    val iconColor =
      classOf(
        $$"com.android.wm.shell.startingsurface.SplashscreenContentDrawer$ColorCache$IconColor",
        param,
      ) ?: return fail()
    // Bail out quietly, so the fallback below does not look like an error in the log.
    if (iconColor.declaredConstructors.isEmpty()) return fail()
    val mBgColorF = iconColor.field("mBgColor") ?: return fail()
    val mIsBgComplexF = iconColor.field("mIsBgComplex") ?: return fail()
    iconColor
      .allConstructors()
      .deoptimize()
      .hookCompat {
        after {
          val mBgColor = mBgColorF.get(thisObject).asType<Int>() ?: return@after
          val mIsBgComplex = mIsBgComplexF.get(thisObject).asType<Boolean>() ?: return@after
          if (!mIsBgComplex && mBgColor == Color.TRANSPARENT) mIsBgComplexF.set(thisObject, true)
        }
      }
      .registerToScopeOrFail()
  }

  /**
   * Fallback for builds where R8 inlined IconColor's constructor away, leaving the class with no
   * methods at all to hook.
   *
   * IconColor is built as `new IconColor(hash, fgTester.getDominateColor(),
   * bgTester.getDominateColor(), bgTester.isComplexColor(), ...)`, so mBgColor and mIsBgComplex
   * both come from the same background DrawableColorTester. Forcing that one isComplexColor() call
   * is therefore equivalent to patching the field afterwards, and isComplexColor() has exactly one
   * caller in SplashscreenContentDrawer, so nothing else changes. Unlike the constructor it also
   * survives optimization, being an override of the ColorTester interface.
   */
  private fun TryHookScope<Unit>.hookBgColorTester(
    param: XposedModuleInterface.PackageReadyParam
  ) {
    val colorTester =
      classOf(
        $$"com.android.wm.shell.startingsurface.SplashscreenContentDrawer$DrawableColorTester",
        param,
      ) ?: return fail()
    // AOSP spells the wrapper getDominateColor(); it can reach us as getDominantColor(), the name
    // the ColorTester interface uses, once the two have been merged.
    val getDominantColorM =
      colorTester.declaredMethods.firstOrNull {
        it.parameterCount == 0 &&
          (it.name == "getDominateColor" || it.name == "getDominantColor")
      }
        ?.apply { isAccessible = true } ?: return fail()
    colorTester
      .allMethods("isComplexColor")
      .deoptimize()
      .hookCompat {
        after {
          if (result.asType<Boolean>() != false) return@after
          if (getDominantColorM.invoke(thisObject).asType<Int>() == Color.TRANSPARENT) result = true
        }
      }
      .registerToScopeOrFail()
  }

context(xposed: XposedInterface)
 override fun onHookSettings(param: XposedModuleInterface.PackageReadyParam) {
    // Fix accessibility
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
    classOf("com.android.settings.Utils", param)
      ?.allMethods("getAdaptiveIcon")
      ?.deoptimize()
      ?.hookCompat {
        before {
          val icon = args[1].asType<Drawable>() ?: return@before
          args[1] = IconHelper.makeAdaptive(icon)
        }
      }
  }
}
