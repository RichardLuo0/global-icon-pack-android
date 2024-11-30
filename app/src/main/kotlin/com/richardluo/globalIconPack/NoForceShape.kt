package com.richardluo.globalIconPack

import android.app.AndroidAppHelper
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.WorldPreference.getReadablePref
import com.richardluo.globalIconPack.reflect.BaseIconFactory
import com.richardluo.globalIconPack.reflect.ReflectHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Method

class NoForceShape : Hook {

  override fun onHookPixelLauncher(lpp: LoadPackageParam) {
    if (!initIfEnabled(lpp)) return

    ReflectHelper.hookAllMethods(
      BaseIconFactory.clazz,
      mapOf(
        "normalizeAndWrapToAdaptiveIcon" to
          object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
              param.args[1] = false
            }
          },
        // Fix FloatingIconView and DragView
        "wrapToAdaptiveIcon" to
          object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
              (param.args[0] as Drawable?)?.let {
                if (it !is AdaptiveIconDrawable)
                  param.result =
                    UnmaskAdaptiveIconDrawable(null, IconHelper.createScaledDrawable(it))
              }
            }
          },
      ),
    )
    // Fix FloatingIconView
    val getScaleM: Method? =
      ReflectHelper.findMethodFirstMatch(
        "com.android.launcher3.icons.IconNormalizer",
        lpp,
        "getScale",
        Drawable::class.java,
      )
    val obtainM: Method? =
      ReflectHelper.findMethodFirstMatch("com.android.launcher3.icons.LauncherIcons", lpp, "obtain")
    fun getScale(drawable: Drawable) =
      obtainM?.call<Any, Float?>(null, AndroidAppHelper.currentApplication()) { factory ->
        letAll(BaseIconFactory.getNormalizer, getScaleM) { getNormalizer, getScale ->
          getScale.call(getNormalizer.call(factory), drawable, null, null, null)
        }
      }
    val floatingIconView =
      ReflectHelper.findClassThrow("com.android.launcher3.views.FloatingIconView", lpp)
    ReflectHelper.hookAllMethods(
      floatingIconView,
      "setIcon",
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          val drawable = param.args[0] as Drawable
          if (drawable is UnmaskAdaptiveIconDrawable) {
            // https://cs.android.com/android/platform/superproject/+/android14-qpr3-release:packages/apps/Launcher3/src/com/android/launcher3/views/FloatingIconView.java;l=441
            val original = drawable.foreground
            getScale(original)?.let { scale ->
              val blurSizeOutline = 2
              val bounds =
                Rect(
                  0,
                  0,
                  original.intrinsicWidth + blurSizeOutline,
                  original.intrinsicHeight + blurSizeOutline,
                )
              bounds.inset(blurSizeOutline / 2, blurSizeOutline / 2)
              val cx: Float = bounds.exactCenterX()
              param.args[3] = Math.round(cx + (bounds.left - cx) * scale)
            }
          }
        }
      },
    )
  }

  override fun onHookSystemUI(lpp: LoadPackageParam) {
    if (!initIfEnabled(lpp)) return

    // Fix splash screen
    // This is used when icon is not adaptive icon
    ReflectHelper.hookAllMethods(
      BaseIconFactory.clazz,
      "normalizeAndWrapToAdaptiveIcon",
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          (param.args[0] as Drawable?)?.let {
            if (it !is AdaptiveIconDrawable) {
              param.args[0] = UnmaskAdaptiveIconDrawable(null, IconHelper.createScaledDrawable(it))
            }
          }
        }
      },
    )
  }

  override fun onHookSettings(lpp: LoadPackageParam) {
    if (!initIfEnabled(lpp)) return

    // Fix recent app list
    ReflectHelper.hookAllMethods(
      BaseIconFactory.clazz,
      "normalizeAndWrapToAdaptiveIcon",
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          param.args[1] = false
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

  private fun initIfEnabled(lpp: LoadPackageParam): Boolean {
    return getReadablePref().getBoolean("noForceShape", true).also {
      if (it) BaseIconFactory.initWithLauncher3(lpp)
    }
  }
}
