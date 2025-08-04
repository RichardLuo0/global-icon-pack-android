package com.richardluo.globalIconPack.xposed

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import com.richardluo.globalIconPack.utils.HookBuilder
import com.richardluo.globalIconPack.utils.allConstructors
import com.richardluo.globalIconPack.utils.allMethods
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.deoptimize
import com.richardluo.globalIconPack.utils.field
import com.richardluo.globalIconPack.utils.hook
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class NoForceShape(private val drawWholeIconForTransparentBackgroundInSplashScreen: Boolean) :
  Hook {
  override fun onHookSystemUI(lpp: LoadPackageParam) {
    // Draw the whole icon even if the background is transparent
    // https://cs.android.com/android/platform/superproject/+/android15-qpr1-release:frameworks/base/libs/WindowManager/Shell/src/com/android/wm/shell/startingsurface/SplashscreenContentDrawer.java;l=676
    if (drawWholeIconForTransparentBackgroundInSplashScreen) {
      val iconColor =
        classOf(
          "com.android.wm.shell.startingsurface.SplashscreenContentDrawer\$ColorCache\$IconColor",
          lpp,
        )
      val mBgColorF = iconColor?.field("mBgColor") ?: return
      val mIsBgComplexF = iconColor.field("mIsBgComplex") ?: return
      iconColor.allConstructors().deoptimize().hook {
        after {
          val mBgColor = mBgColorF.get(thisObject).asType<Int>() ?: return@after
          val mIsBgComplex = mIsBgComplexF.get(thisObject).asType<Boolean>() ?: return@after
          if (!mIsBgComplex && mBgColor == Color.TRANSPARENT) mIsBgComplexF.set(thisObject, true)
        }
      }
    }
  }

  override fun onHookSettings(lpp: LoadPackageParam) {
    // Fix accessibility
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
    val adaptiveIcon = classOf("com.android.settingslib.widget.AdaptiveIcon", lpp) ?: return
    fun HookBuilder.extractOriIcon() {
      after {
        val icon = result.asType<Drawable?>() ?: return@after
        if (adaptiveIcon.isAssignableFrom(icon::class.java))
          icon.asType<LayerDrawable>()?.getDrawable(1)?.let { result = it }
      }
    }
    classOf("com.android.settings.accessibility.AccessibilityActivityPreference", lpp)
      ?.allMethods("getA11yActivityIcon")
      ?.deoptimize()
      ?.hook(HookBuilder::extractOriIcon)
    classOf("com.android.settings.accessibility.AccessibilityServicePreference", lpp)
      ?.allMethods("getA11yServiceIcon")
      ?.deoptimize()
      ?.hook(HookBuilder::extractOriIcon)
  }
}
