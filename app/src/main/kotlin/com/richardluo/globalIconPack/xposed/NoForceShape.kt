package com.richardluo.globalIconPack.xposed

import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import com.richardluo.globalIconPack.reflect.BaseIconFactory
import com.richardluo.globalIconPack.utils.IconHelper
import com.richardluo.globalIconPack.utils.ReflectHelper
import com.richardluo.globalIconPack.utils.UnClipAdaptiveIconDrawable
import com.richardluo.globalIconPack.utils.asType
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class NoForceShape : Hook {

  override fun onHookPixelLauncher(lpp: LoadPackageParam) {
    // May not be presented on android >= 15
    ReflectHelper.hookAllMethods(
      BaseIconFactory.getClazz(lpp),
      "normalizeAndWrapToAdaptiveIcon",
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          param.args[0].asType<Drawable?>()?.let {
            if (it is IconHelper.Adaptively) param.args[0] = it.makeAdaptive()
          }
        }
      },
    )
    // Fix FloatingIconView and DragView
    ReflectHelper.hookAllMethodsOrLog(
      BaseIconFactory.getClazz(lpp),
      "wrapToAdaptiveIcon",
      arrayOf(Drawable::class.java),
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          param.args[0].asType<Drawable?>()?.let {
            if (it is IconHelper.Adaptively) param.result = it.makeAdaptive()
          }
        }
      },
    )
  }

  override fun onHookSystemUI(lpp: LoadPackageParam) {
    // Fix splash screen
    ReflectHelper.hookAllMethodsOrLog(
      BaseIconFactory.getClazz(lpp),
      "normalizeAndWrapToAdaptiveIcon",
      arrayOf(Drawable::class.java),
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          param.args[0].asType<Drawable?>()?.let {
            param.args[0] =
              when (it) {
                is IconHelper.Adaptively -> it.makeAdaptive()
                !is AdaptiveIconDrawable -> makeUnMask(it)
                else -> it
              }
          }
        }
      },
    )
  }

  override fun onHookSettings(lpp: LoadPackageParam) {
    // Fix recent app list
    ReflectHelper.hookAllMethodsOrLog(
      BaseIconFactory.getClazz(lpp),
      "normalizeAndWrapToAdaptiveIcon",
      arrayOf(Drawable::class.java),
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          param.args[0].asType<Drawable?>()?.let {
            param.args[0] =
              when (it) {
                is IconHelper.Adaptively -> it.makeAdaptive()
                !is AdaptiveIconDrawable -> makeUnMask(it)
                else -> it
              }
          }
        }
      },
    )
    // Fix accessibility
    val adaptiveIcon =
      ReflectHelper.findClass("com.android.settingslib.widget.AdaptiveIcon", lpp) ?: return
    val extractOriIcon =
      object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
          param.result.asType<Drawable?>()?.let { icon ->
            if (adaptiveIcon.isAssignableFrom(icon::class.java))
              icon.asType<LayerDrawable>().getDrawable(1)?.let { param.result = it }
          }
        }
      }
    ReflectHelper.hookAllMethodsOrLog(
      ReflectHelper.findClass(
        "com.android.settings.accessibility.AccessibilityActivityPreference",
        lpp,
      ),
      "getA11yActivityIcon",
      extractOriIcon,
    )
    ReflectHelper.hookAllMethodsOrLog(
      ReflectHelper.findClass(
        "com.android.settings.accessibility.AccessibilityServicePreference",
        lpp,
      ),
      "getA11yServiceIcon",
      extractOriIcon,
    )
  }

  class UnmaskAdaptiveIconDrawable(background: Drawable?, foreground: Drawable?) :
    UnClipAdaptiveIconDrawable(background, foreground) {
    override fun getIconMask(): Path = getFullBoundsPath()

    override val cState: ConstantState by lazy { CState() }

    private inner class CState : UnClipState() {
      override fun newDrawable() = UnmaskAdaptiveIconDrawable(newBackground(), newForeground())
    }
  }

  fun makeUnMask(drawable: Drawable) =
    UnmaskAdaptiveIconDrawable(null, IconHelper.createScaledDrawable(drawable))
}
