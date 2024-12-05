package com.richardluo.globalIconPack

import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageItemInfo
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.reflect.ClockDrawableWrapper
import com.richardluo.globalIconPack.reflect.ReflectHelper
import com.richardluo.globalIconPack.reflect.Resources.getDrawableForDensityM
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

private const val IN_CIP = 0xff000000.toInt()
private const val NOT_IN_CIP = 0xfe000000.toInt()
private const val ANDROID_DEFAULT = 0x7f000000
private const val CIP_DEFAULT = 0x00000000

class ReplaceIcon : Hook {
  override fun onHookPixelLauncher(lpp: LoadPackageParam) {
    // Find needed class
    ClockDrawableWrapper.initWithPixelLauncher(lpp)
  }

  override fun onHookApp(lpp: LoadPackageParam) {
    val packPackageName = WorldPreference.getReadablePref().getString(PrefKey.ICON_PACK, "") ?: ""
    val iconPackAsFallback =
      WorldPreference.getReadablePref().getBoolean(PrefKey.ICON_PACK_AS_FALLBACK, false)

    // Resource id always starts with 0x7f, use it to indict that this is an icon
    // Assume the icon res id is only used in getDrawable()
    val replaceIconResId =
      object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
          val info = param.thisObject as PackageItemInfo
          if (info.icon == 0) return
          // Avoid recursively call getCip()
          if (isCipInitialized() || info.packageName rNEqual packPackageName)
            getCip()?.let { cip ->
              val id =
                cip.getId(getComponentName(info))
                  ?: if (iconPackAsFallback) cip.getId(getComponentName(info.packageName))
                  else return
              info.icon =
                id?.let { withHighByteSet(it, IN_CIP) } ?: withHighByteSet(info.icon, NOT_IN_CIP)
            }
        }
      }
    ReflectHelper.hookAllConstructors(ApplicationInfo::class.java, replaceIconResId)
    ReflectHelper.hookAllConstructors(ActivityInfo::class.java, replaceIconResId)

    // Hook resolve info icon
    val iconResourceIdF = ReflectHelper.findField(ResolveInfo::class.java, "iconResourceId")
    ReflectHelper.hookAllConstructors(
      ResolveInfo::class.java,
      object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
          val info = param.thisObject as ResolveInfo
          // Simply set to be the same as ActivityInfo
          info.activityInfo?.let {
            if (info.icon != 0) info.icon = it.icon
            iconResourceIdF?.set(info, it.icon)
          }
        }
      },
    )

    val replaceIcon: XC_MethodHook =
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          runCatching { param.result = replaceHookedMethod(param) }
            .exceptionOrNull()
            ?.let { param.throwable = it }
        }

        fun replaceHookedMethod(param: MethodHookParam): Drawable? {
          val resId = param.args[0] as Int
          val density = param.args[1] as Int
          return when {
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
