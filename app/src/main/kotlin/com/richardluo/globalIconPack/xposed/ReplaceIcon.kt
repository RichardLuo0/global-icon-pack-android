package com.richardluo.globalIconPack.xposed

import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageItemInfo
import android.content.pm.ResolveInfo
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.iconPack.getComponentName
import com.richardluo.globalIconPack.iconPack.getIP
import com.richardluo.globalIconPack.reflect.ClockDrawableWrapper
import com.richardluo.globalIconPack.reflect.Resources.getDrawableForDensityM
import com.richardluo.globalIconPack.utils.MethodReplacement
import com.richardluo.globalIconPack.utils.ReflectHelper
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.callOriginalMethod
import com.richardluo.globalIconPack.utils.isHighTwoByte
import com.richardluo.globalIconPack.utils.withHighByteSet
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class ReplaceIcon : Hook {
  companion object {
    const val IN_IP = 0xff000000.toInt()
    const val NOT_IN_IP = 0xfe000000.toInt()
    const val ANDROID_DEFAULT = 0x7f000000
    const val IP_DEFAULT = 0x00000000
  }

  override fun onHookPixelLauncher(lpp: LoadPackageParam) {
    // Find needed class for clock
    ClockDrawableWrapper.initWithPixelLauncher(lpp)
  }

  override fun onHookApp(lpp: LoadPackageParam) {
    // Resource id always starts with 0x7f, use it to indicate that this is an icon
    // Assume the icon res id is only used in getDrawable()
    val isInReplaceIconResId = ThreadLocal.withInitial { false }
    val replaceIconResId =
      object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
          // Avoid recursive call
          if (isInReplaceIconResId.get() == true) return
          isInReplaceIconResId.set(true)
          afterHookedMethodSafe(param)
          isInReplaceIconResId.set(false)
        }

        fun afterHookedMethodSafe(param: MethodHookParam) {
          val info = param.thisObject as PackageItemInfo
          if (info.packageName == null) return
          info.icon =
            getIP()?.getId(getComponentName(info))?.let { withHighByteSet(it, IN_IP) }
              ?: if (isHighTwoByte(info.icon, ANDROID_DEFAULT))
                withHighByteSet(info.icon, NOT_IN_IP)
              else info.icon
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

    ReflectHelper.hookMethod(
      getDrawableForDensityM ?: return,
      object : MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam): Drawable? {
          val resId = param.args[0] as Int
          val density = param.args[1] as Int
          return when {
            isHighTwoByte(resId, IN_IP) ->
              getIP()?.getIcon(withHighByteSet(resId, IP_DEFAULT), density)
            isHighTwoByte(resId, NOT_IN_IP) -> {
              param.args[0] = withHighByteSet(resId, ANDROID_DEFAULT)
              callOriginalMethod<Drawable?>(param)?.let { getIP()?.genIconFrom(it) ?: it }
            }
            else -> callOriginalMethod(param)
          }
        }
      },
    )

    // Generate shortcut icon
    if (WorldPreference.getPrefInMod().get(Pref.SHORTCUT))
      ReflectHelper.hookAllMethodsOrLog(
        LauncherApps::class.java,
        "getShortcutIconDrawable",
        object : MethodReplacement() {
          override fun replaceHookedMethod(param: MethodHookParam): Drawable? {
            val shortcut = param.args[0].asType<ShortcutInfo>()
            val density = param.args[1].asType<Int>()
            val ip = getIP() ?: return callOriginalMethod(param)
            return ip.getIconEntry(getComponentName(shortcut))?.let { ip.getIcon(it, density) }
              ?: callOriginalMethod<Drawable?>(param)?.let { ip.genIconFrom(it) }
          }
        },
      )
  }
}
