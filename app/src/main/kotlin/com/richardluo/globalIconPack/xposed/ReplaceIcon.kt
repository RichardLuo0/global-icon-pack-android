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
import com.richardluo.globalIconPack.iconPack.IconPack
import com.richardluo.globalIconPack.iconPack.getComponentName
import com.richardluo.globalIconPack.iconPack.getIP
import com.richardluo.globalIconPack.reflect.ClockDrawableWrapper
import com.richardluo.globalIconPack.reflect.Resources.getDrawableForDensityM
import com.richardluo.globalIconPack.utils.ReflectHelper
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.callOriginalMethod
import com.richardluo.globalIconPack.utils.getAs
import com.richardluo.globalIconPack.utils.isHighTwoByte
import com.richardluo.globalIconPack.utils.withHighByteSet
import com.richardluo.globalIconPack.xposed.ReplaceIcon.Companion.ANDROID_DEFAULT
import com.richardluo.globalIconPack.xposed.ReplaceIcon.Companion.IN_IP
import com.richardluo.globalIconPack.xposed.ReplaceIcon.Companion.NOT_IN_IP
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import kotlin.to

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

  private val replacerMap = run {
    val iconResourceIdF = ReflectHelper.findField(ResolveInfo::class.java, "iconResourceId")
    val launcherActivityInfo =
      ReflectHelper.findClass("android.content.pm.LauncherActivityInfoInternal")
    val mActivityInfoF = ReflectHelper.findField(launcherActivityInfo, "mActivityInfo")

    val defaultReplacer: Replacer = { list, ip ->
      replaceItemInfo(list.mapNotNull { it.asType<PackageItemInfo>() }, ip)
    }
    val activityInfoReplacer: Replacer = { list, ip ->
      defaultReplacer(list, ip)
      replaceItemInfo(list.mapNotNull { it.asType<ActivityInfo>()?.applicationInfo }, ip)
    }

    listOfNotNull<Pair<Class<*>, Replacer>>(
        ApplicationInfo::class.java to defaultReplacer,
        ActivityInfo::class.java to activityInfoReplacer,
        ResolveInfo::class.java to
          { list, ip ->
            val riList = list.mapNotNull { it.asType<ResolveInfo>() }
            activityInfoReplacer(riList.mapNotNull { it.activityInfo }, ip)
            riList.forEach { ri ->
              val icon = ri.activityInfo?.icon?.takeIf { it != 0 } ?: return@forEach
              ri.icon = icon
              iconResourceIdF?.set(ri, icon)
            }
          },
        run {
          launcherActivityInfo ?: return@run null
          mActivityInfoF ?: return@run null
          launcherActivityInfo to
            Replacer@{ list, ip ->
              activityInfoReplacer(
                list.mapNotNull { it?.let { mActivityInfoF.getAs<ActivityInfo>(it) } },
                ip,
              )
            }
        },
      )
      .toMap()
  }

  override fun onHookApp(lpp: LoadPackageParam) {
    // Resource id always starts with 0x7f, use it to indicate that this is an icon
    // Assume the icon res id is only used in getDrawable()
    val blockReplaceIconResId = ThreadLocal.withInitial { false }
    val replaceIconResId =
      object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
          // Avoid recursive call
          if (blockReplaceIconResId.get() == true) return
          blockReplaceIconResId.set(true)
          afterHookedMethodSafe(param)
          blockReplaceIconResId.set(false)
        }

        fun afterHookedMethodSafe(param: MethodHookParam) {
          val info = param.thisObject as PackageItemInfo
          info.packageName ?: return
          val ip = getIP() ?: return
          replaceIconResIdInInfo(info, ip.getId(getComponentName(info)))
        }
      }
    ReflectHelper.hookAllConstructors(ApplicationInfo::class.java, replaceIconResId)
    ReflectHelper.hookAllConstructors(ActivityInfo::class.java, replaceIconResId)

    val baseParceledListSlice =
      ReflectHelper.findClass("android.content.pm.BaseParceledListSlice") ?: return
    val mListF = ReflectHelper.findField(baseParceledListSlice, "mList") ?: return
    ReflectHelper.hookAllConstructors(
      baseParceledListSlice,
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          val oldBlock = blockReplaceIconResId.get()
          blockReplaceIconResId.set(true)
          replaceIconResId(param)
          blockReplaceIconResId.set(oldBlock)
        }

        fun replaceIconResId(param: MethodHookParam) {
          val ip = getIP() ?: return
          param.callOriginalMethod<Unit>()
          param.result = null
          val list = mListF.getAs<List<Any?>?>(param.thisObject) ?: return
          val clazz = list.getOrNull(0)?.javaClass ?: return
          val replacer = replacerMap[clazz] ?: return
          replacer(list, ip)
        }
      },
    )

    ReflectHelper.hookMethod(
      getDrawableForDensityM ?: return,
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          val resId = param.args[0] as? Int ?: return
          val density = param.args[1] as? Int ?: return
          param.result =
            when {
              isHighTwoByte(resId, IN_IP) ->
                getIP()?.getIcon(withHighByteSet(resId, IP_DEFAULT), density)
              isHighTwoByte(resId, NOT_IN_IP) -> {
                param.args[0] = withHighByteSet(resId, ANDROID_DEFAULT)
                param.callOriginalMethod<Drawable?>()?.let { getIP()?.genIconFrom(it) ?: it }
              }
              else -> return
            }
        }
      },
    )

    // Generate shortcut icon
    if (WorldPreference.getPrefInMod().get(Pref.SHORTCUT))
      ReflectHelper.hookAllMethodsOrLog(
        LauncherApps::class.java,
        "getShortcutIconDrawable",
        object : XC_MethodHook() {
          override fun beforeHookedMethod(param: MethodHookParam) {
            val shortcut = param.args[0] as? ShortcutInfo ?: return
            val density = param.args[1] as? Int ?: return
            val ip = getIP() ?: return
            param.result =
              ip.getIconEntry(getComponentName(shortcut))?.let { ip.getIcon(it, density) }
                ?: param.callOriginalMethod<Drawable?>()?.let { ip.genIconFrom(it) }
          }
        },
      )
  }
}

private fun replaceIconResIdInInfo(info: PackageItemInfo, id: Int?) {
  info.icon =
    id?.let { withHighByteSet(it, IN_IP) }
      ?: if (isHighTwoByte(info.icon, ANDROID_DEFAULT)) withHighByteSet(info.icon, NOT_IN_IP)
      else info.icon
}

fun replaceItemInfo(list: List<PackageItemInfo>, ip: IconPack) {
  ip.getId(list.map { getComponentName(it) }).forEachIndexed { i, it ->
    replaceIconResIdInInfo(list[i], it)
  }
}

typealias Replacer = (list: List<Any?>, ip: IconPack) -> Unit
