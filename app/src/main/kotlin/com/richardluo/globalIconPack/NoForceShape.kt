package com.richardluo.globalIconPack

import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import com.richardluo.globalIconPack.reflect.BaseIconFactory
import com.richardluo.globalIconPack.utils.IconHelper
import com.richardluo.globalIconPack.utils.ReflectHelper
import com.richardluo.globalIconPack.utils.UnClipAdaptiveIconDrawable
import com.richardluo.globalIconPack.utils.WorldPreference.getPrefInMod
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.rGet
import com.richardluo.globalIconPack.utils.rSet
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class NoForceShape : Hook {

  override fun onHookPixelLauncher(lpp: LoadPackageParam) {
    removeShadow(lpp)

    if (!getPrefInMod().get(Pref.NO_FORCE_SHAPE)) return
    // May not be presented on android >= 15
    ReflectHelper.hookAllMethods(
      BaseIconFactory.getClazz(lpp) ?: return,
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
      BaseIconFactory.getClazz(lpp) ?: return,
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
    removeShadow(lpp)
    // Remove bubble shadow
    if (getPrefInMod().get(Pref.NO_SHADOW)) {
      ReflectHelper.hookAllConstructors(
        ReflectHelper.findClass("com.android.wm.shell.bubbles.BadgedImageView", lpp) ?: return,
        object : XC_MethodHook() {
          override fun afterHookedMethod(param: MethodHookParam) {
            param.thisObject.asType<View>().outlineProvider = null
          }
        },
      )
    }

    if (!getPrefInMod().get(Pref.NO_FORCE_SHAPE)) return
    // Fix splash screen
    ReflectHelper.hookAllMethodsOrLog(
      BaseIconFactory.getClazz(lpp) ?: return,
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
    removeShadow(lpp)

    if (!getPrefInMod().get(Pref.NO_FORCE_SHAPE)) return
    // Fix recent app list
    ReflectHelper.hookAllMethodsOrLog(
      BaseIconFactory.getClazz(lpp) ?: return,
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
      ) ?: return,
      "getA11yActivityIcon",
      extractOriIcon,
    )
    ReflectHelper.hookAllMethodsOrLog(
      ReflectHelper.findClass(
        "com.android.settings.accessibility.AccessibilityServicePreference",
        lpp,
      ) ?: return,
      "getA11yServiceIcon",
      extractOriIcon,
    )
  }

  @Suppress("LocalVariableName")
  private fun removeShadow(lpp: LoadPackageParam) {
    if (!getPrefInMod().get(Pref.NO_SHADOW)) return
    val MODE_DEFAULT = 0
    val MODE_WITH_SHADOW = 2
    val MODE_HARDWARE = 3
    val MODE_HARDWARE_WITH_SHADOW = 4
    ReflectHelper.hookAllMethodsOrLog(
      BaseIconFactory.getClazz(lpp) ?: return,
      "drawIconBitmap",
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          val bitmapGenerationMode = param.args.rGet(-2) as Int
          param.args.rSet(
            -2,
            when (bitmapGenerationMode) {
              MODE_WITH_SHADOW -> MODE_DEFAULT
              MODE_HARDWARE_WITH_SHADOW -> MODE_HARDWARE
              else -> bitmapGenerationMode
            },
          )
        }
      },
    )
  }

  class UnmaskAdaptiveIconDrawable(background: Drawable?, foreground: Drawable?) :
    UnClipAdaptiveIconDrawable(background, foreground) {
    override fun getIconMask(): Path = getFullBoundsPath()

    override fun getConstantState(): ConstantState {
      return UnmaskState(background, foreground)
    }

    class UnmaskState(private val background: Drawable?, private val foreground: Drawable?) :
      ConstantState() {
      override fun newDrawable(): Drawable = UnmaskAdaptiveIconDrawable(background, foreground)

      override fun getChangingConfigurations(): Int = 0
    }
  }

  fun makeUnMask(drawable: Drawable) =
    UnmaskAdaptiveIconDrawable(null, IconHelper.createScaledDrawable(drawable))
}
