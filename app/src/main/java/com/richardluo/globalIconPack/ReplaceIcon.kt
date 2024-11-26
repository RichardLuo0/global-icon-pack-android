package com.richardluo.globalIconPack

import android.app.Instrumentation
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageItemInfo
import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.reflect.ClockDrawableWrapper
import com.richardluo.globalIconPack.reflect.Resources.getDrawableForDensity
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

private const val IN_CIP = 0xff000000.toInt()
private const val NOT_IN_CIP = 0xfe000000.toInt()
private const val ANDROID_DEFAULT = 0x7f000000
private const val CIP_DEFAULT = 0x00000000

class ReplaceIcon : Hook {
  override fun onHookPixelLauncher(lpp: XC_LoadPackage.LoadPackageParam) {
    // Find needed class
    ClockDrawableWrapper.initWithPixelLauncher(lpp)
  }

  override fun onHookApp(lpp: XC_LoadPackage.LoadPackageParam) {
    var cip: CustomIconPack? = null

    // Pre init to avoid ApplicationInfo constructor creates loop with getResourcesForApplication()
    XposedBridge.hookAllMethods(
      Instrumentation::class.java,
      "callApplicationOnCreate",
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam?) {
          cip = getCip()
        }
      },
    )

    // Resource id always starts with 0x7f, use it to indict that this is an icon
    // Assume the icon res id is only used in getDrawable()
    val replaceIconResId =
      object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
          val info = param.thisObject as PackageItemInfo
          if (info.icon != 0)
            info.icon =
              cip?.getId(getComponentName(info))?.let { withHighByteSet(it, IN_CIP) }
                ?: withHighByteSet(info.icon, NOT_IN_CIP)
        }
      }
    XposedBridge.hookAllConstructors(ApplicationInfo::class.java, replaceIconResId)
    XposedBridge.hookAllConstructors(ActivityInfo::class.java, replaceIconResId)

    val replaceIcon: XC_MethodReplacement =
      object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam): Drawable? {
          val resId = param.args[0] as Int
          val density = param.args[1] as Int
          return when {
            isHighTwoByte(resId, IN_CIP) -> {
              param.args[0] = withHighByteSet(resId, CIP_DEFAULT)
              // TODO Better way to do this? Original id has been lost
              cip?.getIcon(param.args[0] as Int, density)
            }
            isHighTwoByte(resId, NOT_IN_CIP) -> {
              param.args[0] = withHighByteSet(resId, ANDROID_DEFAULT)
              cip?.let { generateIcon(it, param, density) }
            }
            else -> callOriginalMethod(param)
          }
        }

        fun generateIcon(cip: CustomIconPack, param: MethodHookParam, density: Int): Drawable? {
          return callOriginalMethod<Drawable?>(param)?.let { cip.genIconFrom(it, density) }
        }
      }
    XposedBridge.hookMethod(getDrawableForDensity, replaceIcon)
  }
}
