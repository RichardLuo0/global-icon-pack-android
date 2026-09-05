package com.richardluo.globalIconPack.xposed

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import com.richardluo.globalIconPack.utils.IconHelper
import com.richardluo.globalIconPack.utils.allConstructors
import com.richardluo.globalIconPack.utils.allMethods
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.deoptimize
import com.richardluo.globalIconPack.utils.field
import com.richardluo.globalIconPack.utils.hookCompat
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface

class NoForceShape(private val drawWholeIconForTransparentBackgroundInSplashScreen: Boolean) :
  Hook {
context(xposed: XposedInterface)
 override fun onHookSystemUI(param: XposedModuleInterface.PackageReadyParam) {
    // Draw the whole icon even if the background is transparent
    // https://cs.android.com/android/platform/superproject/+/android15-qpr1-release:frameworks/base/libs/WindowManager/Shell/src/com/android/wm/shell/startingsurface/SplashscreenContentDrawer.java;l=676
    if (drawWholeIconForTransparentBackgroundInSplashScreen) {
      val iconColor =
        classOf(
          $$"com.android.wm.shell.startingsurface.SplashscreenContentDrawer$ColorCache$IconColor",
          param,
        )
      val mBgColorF = iconColor?.field("mBgColor") ?: return
      val mIsBgComplexF = iconColor.field("mIsBgComplex") ?: return
      iconColor.allConstructors().deoptimize().hookCompat {
        after {
          val mBgColor = mBgColorF.get(thisObject).asType<Int>() ?: return@after
          val mIsBgComplex = mIsBgComplexF.get(thisObject).asType<Boolean>() ?: return@after
          if (!mIsBgComplex && mBgColor == Color.TRANSPARENT) mIsBgComplexF.set(thisObject, true)
        }
      }
    }
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
