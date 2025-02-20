package com.richardluo.globalIconPack

import android.view.View
import com.richardluo.globalIconPack.reflect.BaseIconFactory
import com.richardluo.globalIconPack.utils.MethodReplacement
import com.richardluo.globalIconPack.utils.ReflectHelper
import com.richardluo.globalIconPack.utils.WorldPreference.getPrefInMod
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.rGet
import com.richardluo.globalIconPack.utils.rSet
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class NoShadow : Hook {

  override fun onHookPixelLauncher(lpp: LoadPackageParam) {
    if (!getPrefInMod().get(Pref.NO_SHADOW)) return
    removeIconShadow(lpp)
  }

  override fun onHookSystemUI(lpp: LoadPackageParam) {
    if (!getPrefInMod().get(Pref.NO_SHADOW)) return
    // Remove bubble shadow
    ReflectHelper.hookAllConstructors(
      ReflectHelper.findClass("com.android.wm.shell.bubbles.BadgedImageView", lpp),
      object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
          param.thisObject.asType<View>().outlineProvider = null
        }
      },
    )
    removeIconShadow(lpp)
  }

  override fun onHookSettings(lpp: LoadPackageParam) {
    if (!getPrefInMod().get(Pref.NO_SHADOW)) return
    removeIconShadow(lpp)
  }

  @Suppress("LocalVariableName")
  private fun removeIconShadow(lpp: LoadPackageParam) {
    ReflectHelper.hookAllMethodsOrLog(
      ReflectHelper.findClass("android.util.LauncherIcons"),
      "wrapIconDrawableWithShadow",
      object : MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam) = param.args[0]
      },
    )

    val MODE_DEFAULT = 0
    val MODE_WITH_SHADOW = 2
    val MODE_HARDWARE = 3
    val MODE_HARDWARE_WITH_SHADOW = 4
    ReflectHelper.hookAllMethodsOrLog(
      BaseIconFactory.getClazz(lpp),
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
}
