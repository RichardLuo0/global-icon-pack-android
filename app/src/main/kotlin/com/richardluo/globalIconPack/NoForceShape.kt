package com.richardluo.globalIconPack

import android.app.AndroidAppHelper
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.reflect.BaseIconFactory
import com.richardluo.globalIconPack.reflect.BaseIconFactory.getNormalizer
import com.richardluo.globalIconPack.utils.IconHelper
import com.richardluo.globalIconPack.utils.ReflectHelper
import com.richardluo.globalIconPack.utils.UnClipAdaptiveIconDrawable
import com.richardluo.globalIconPack.utils.WorldPreference.getPrefInMod
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.call
import com.richardluo.globalIconPack.utils.letAll
import com.richardluo.globalIconPack.utils.rGet
import com.richardluo.globalIconPack.utils.rSet
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Method

class NoForceShape : Hook {

  override fun onHookPixelLauncher(lpp: LoadPackageParam) {
    if (!initIfEnabled(lpp)) return

    removeShadow()
    XposedBridge.hookAllMethods(
      BaseIconFactory.clazz,
      "normalizeAndWrapToAdaptiveIcon",
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          param.args[0].asType<Drawable?>()?.let {
            if (it is IconHelper.ProcessedBitmapDrawable) param.args[0] = it.unMask()
          }
        }
      },
    )
    // Fix FloatingIconView and DragView
    ReflectHelper.hookAllMethods(
      BaseIconFactory.clazz,
      "wrapToAdaptiveIcon",
      arrayOf(Drawable::class.java),
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          param.args[0].asType<Drawable?>()?.let {
            if (it is IconHelper.ProcessedBitmapDrawable) param.result = it.unMask()
          }
        }
      },
    )
    // Fix FloatingIconView
    val getScale = getGetScale(lpp)
    ReflectHelper.findClass("com.android.launcher3.views.FloatingIconView", lpp)?.let {
      ReflectHelper.hookAllMethods(
        it,
        "setIcon",
        object : XC_MethodHook() {
          override fun beforeHookedMethod(param: MethodHookParam) {
            val drawable = param.args[0] as Drawable
            if (drawable is UnmaskAdaptiveIconDrawable) {
              // https://cs.android.com/android/platform/superproject/+/android14-qpr3-release:packages/apps/Launcher3/src/com/android/launcher3/views/FloatingIconView.java;l=441
              val original = drawable.foreground
              getScale(null, original, null, null, null)?.let { scale ->
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
  }

  override fun onHookSystemUI(lpp: LoadPackageParam) {
    if (!initIfEnabled(lpp)) return

    removeShadow()
    // Fix splash screen
    // This is used when icon is not adaptive icon
    ReflectHelper.hookAllMethods(
      BaseIconFactory.clazz,
      "normalizeAndWrapToAdaptiveIcon",
      arrayOf(Drawable::class.java),
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          param.args[0].asType<Drawable?>()?.let {
            if (it !is AdaptiveIconDrawable) param.args[0] = createUnMask(it)
          }
        }
      },
    )
  }

  override fun onHookSettings(lpp: LoadPackageParam) {
    if (!initIfEnabled(lpp)) return

    removeShadow()
    // Fix recent app list
    ReflectHelper.hookAllMethods(
      BaseIconFactory.clazz,
      "normalizeAndWrapToAdaptiveIcon",
      arrayOf(Drawable::class.java),
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          param.args[0].asType<Drawable?>()?.let {
            if (it !is AdaptiveIconDrawable) param.args[0] = createUnMask(it)
          }
        }
      },
    )
  }

  @Suppress("LocalVariableName")
  private fun removeShadow() {
    // Remove shadow because icons may have its own shadow
    val MODE_DEFAULT = 0
    val MODE_WITH_SHADOW = 2
    val MODE_HARDWARE = 3
    val MODE_HARDWARE_WITH_SHADOW = 4
    ReflectHelper.hookAllMethods(
      BaseIconFactory.clazz,
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

  private fun getGetScale(
    lpp: LoadPackageParam
  ): (Any?, Drawable, RectF?, Path?, BooleanArray?) -> Float? {
    val getScaleM: Method? =
      ReflectHelper.findMethodFirstMatch(
        "com.android.launcher3.icons.IconNormalizer",
        lpp,
        "getScale",
        Drawable::class.java,
        RectF::class.java,
      )
    val obtainM: Method? =
      ReflectHelper.findMethodFirstMatch("com.android.launcher3.icons.LauncherIcons", lpp, "obtain")
    return {
      thisObj: Any?,
      drawable: Drawable,
      outIconBounds: RectF?,
      path: Path?,
      outMaskShape: BooleanArray? ->
      val factory = thisObj ?: obtainM?.call<Any>(null, AndroidAppHelper.currentApplication())
      letAll(getNormalizer, getScaleM) { getNormalizer, getScale ->
        getScale.call(getNormalizer.call(factory), drawable, outIconBounds, path, outMaskShape)
      }
    }
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

  fun IconHelper.ProcessedBitmapDrawable.unMask() = createUnMask(this)

  fun createUnMask(drawable: Drawable) =
    UnmaskAdaptiveIconDrawable(null, IconHelper.createScaledDrawable(drawable))

  private fun initIfEnabled(lpp: LoadPackageParam): Boolean {
    return getPrefInMod().getBoolean(PrefKey.NO_FORCE_SHAPE, false).also {
      if (it) BaseIconFactory.initWithLauncher3(lpp)
    }
  }
}
