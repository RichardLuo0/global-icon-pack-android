package com.richardluo.globalIconPack

import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageItemInfo
import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.reflect.ClockDrawableWrapper
import com.richardluo.globalIconPack.reflect.ReflectHelper
import com.richardluo.globalIconPack.reflect.Resources.getDrawableForDensityM
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.callbacks.XC_LoadPackage

private const val IS_ICON_PACK = 0xff000000.toInt()
private const val IN_CIP = 0xfe000000.toInt()
private const val NOT_IN_CIP = 0xfd000000.toInt()
private const val ANDROID_DEFAULT = 0x7f000000
private const val CIP_DEFAULT = 0x00000000

class ReplaceIcon : Hook {
  override fun onHookPixelLauncher(lpp: XC_LoadPackage.LoadPackageParam) {
    // Find needed class
    ClockDrawableWrapper.initWithPixelLauncher(lpp)
  }

  override fun onHookApp(lpp: XC_LoadPackage.LoadPackageParam) {
    val packPackageName = WorldPreference.getReadablePref().getString("iconPack", "") ?: ""
    val iconPackAsFallback =
      WorldPreference.getReadablePref().getBoolean("iconPackAsFallback", false)

    // Resource id always starts with 0x7f, use it to indict that this is an icon
    // Assume the icon res id is only used in getDrawable()

    val replaceIconResId =
      object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
          val info = param.thisObject as PackageItemInfo
          if (info.icon != 0)
            if (info.packageName rEqual packPackageName) {
              // Avoid recursively call getCip()
              info.icon = withHighByteSet(info.icon, IS_ICON_PACK)
            } else
              getCip()?.let { cip ->
                val id =
                  cip.getId(getComponentName(info))
                    ?: if (iconPackAsFallback) cip.getId(getComponentName(info.packageName))
                    else null
                info.icon =
                  id?.let { withHighByteSet(it, IN_CIP) } ?: withHighByteSet(info.icon, NOT_IN_CIP)
              }
        }
      }
    ReflectHelper.hookAllConstructors(ApplicationInfo::class.java, replaceIconResId)
    ReflectHelper.hookAllConstructors(ActivityInfo::class.java, replaceIconResId)

    val replaceIcon: XC_MethodReplacement =
      object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam): Drawable? {
          val resId = param.args[0] as Int
          val density = param.args[1] as Int
          return when {
            isHighTwoByte(resId, IS_ICON_PACK) -> {
              param.args[0] = withHighByteSet(resId, ANDROID_DEFAULT)
              getCip()?.getPackIcon(param.args[0] as Int, density) ?: callOriginalMethod(param)
            }
            isHighTwoByte(resId, IN_CIP) -> {
              val id = withHighByteSet(resId, CIP_DEFAULT)
              // Original id has been lost
              getCip()?.getIcon(id, density)
            }
            isHighTwoByte(resId, NOT_IN_CIP) -> {
              param.args[0] = withHighByteSet(resId, ANDROID_DEFAULT)
              callOriginalMethod<Drawable?>(param)?.let { getCip()?.genIconFrom(it) ?: it }
            }
            else -> callOriginalMethod(param)
          }
        }
      }
    getDrawableForDensityM?.let { ReflectHelper.hookMethod(it, replaceIcon) }
  }
}
