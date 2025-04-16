package com.richardluo.globalIconPack.xposed

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager.RecentTaskInfo
import android.app.ActivityManager.RunningTaskInfo
import android.app.TaskInfo
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.content.pm.PackageItemInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Parcel
import com.richardluo.globalIconPack.iconPack.IconPack
import com.richardluo.globalIconPack.iconPack.getComponentName
import com.richardluo.globalIconPack.iconPack.getIP
import com.richardluo.globalIconPack.reflect.ClockDrawableWrapper
import com.richardluo.globalIconPack.reflect.Resources.getDrawableForDensityM
import com.richardluo.globalIconPack.utils.ReflectHelper
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

typealias ListReplacer = (list: List<Any?>, ip: IconPack) -> Unit

// Resource id always starts with 0x7f, use it to indicate that this is an icon
// Assume the icon res id is only used in getDrawable()
class ReplaceIcon(val shortcut: Boolean) : Hook {
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

  private val listReplacerMap = run {
    val defaultReplacer: ListReplacer = { list, ip ->
      replaceItemInfo(list.mapNotNull { it.asType<PackageItemInfo>() }, ip)
    }
    val componentInfoReplacer: ListReplacer = { list, ip ->
      defaultReplacer(list, ip)
      replaceItemInfo(list.mapNotNull { it.asType<ComponentInfo>()?.applicationInfo }, ip)
    }
    val resolveInfoReplacer: ListReplacer? = run {
      val iconResourceIdF =
        ReflectHelper.findField(ResolveInfo::class.java, "iconResourceId") ?: return@run null
      { list, ip ->
        val riList = list.mapNotNull { it.asType<ResolveInfo>() }
        componentInfoReplacer(riList.map { it.getComponentInfo() }, ip)
        riList.forEach { ri ->
          val icon = ri.getComponentInfo().icon.takeIf { it != 0 } ?: return@forEach
          ri.icon = icon
          iconResourceIdF.set(ri, icon)
        }
      }
    }

    buildMap<Class<*>, ListReplacer> {
      set(ApplicationInfo::class.java, defaultReplacer)
      set(ActivityInfo::class.java, componentInfoReplacer)
      set(ServiceInfo::class.java, componentInfoReplacer)
      resolveInfoReplacer?.let { set(ResolveInfo::class.java, it) }
      set(PackageInfo::class.java) { list, ip ->
        val piList = list.mapNotNull { it.asType<PackageInfo>() }
        defaultReplacer(piList.mapNotNull { it.applicationInfo }, ip)
        piList.forEach { pi -> pi.activities?.let { componentInfoReplacer(it.toList(), ip) } }
      }
      run {
        val topActivityInfoF =
          ReflectHelper.findField(TaskInfo::class.java, "topActivityInfo") ?: return@run
        val taskInfoReplacer: ListReplacer = { list, ip ->
          defaultReplacer(
            list.mapNotNull { it?.let { topActivityInfoF.getAs<ActivityInfo>(it) } },
            ip,
          )
        }
        set(RunningTaskInfo::class.java, taskInfoReplacer)
        set(RecentTaskInfo::class.java, taskInfoReplacer)
      }
      run {
        val launcherActivityInfo =
          ReflectHelper.findClass("android.content.pm.LauncherActivityInfoInternal") ?: return@run
        val mActivityInfoF =
          ReflectHelper.findField(launcherActivityInfo, "mActivityInfo") ?: return@run
        set(launcherActivityInfo) { list, ip ->
          componentInfoReplacer(
            list.mapNotNull { it?.let { mActivityInfoF.getAs<ActivityInfo>(it) } },
            ip,
          )
        }
      }
      run {
        val launchActivityItem =
          ReflectHelper.findClass("android.app.servertransaction.LaunchActivityItem") ?: return@run
        val mInfoF = ReflectHelper.findField(launchActivityItem, "mInfo") ?: return@run
        set(launchActivityItem) { list, ip ->
          componentInfoReplacer(list.mapNotNull { it?.let { mInfoF.getAs<ActivityInfo>(it) } }, ip)
        }
      }
      resolveInfoReplacer?.let {
        set(AccessibilityServiceInfo::class.java) { list, ip ->
          it(list.mapNotNull { it.asType<AccessibilityServiceInfo>()?.resolveInfo }, ip)
        }
      }
    }
  }

  private val blockReplaceIconResId = ThreadLocal.withInitial { false }

  private val replaceIconResId =
    object : XC_MethodHook() {
      override fun afterHookedMethod(param: MethodHookParam) {
        if (blockReplaceIconResId.get() == true) return
        blockReplaceIconResId.set(true)
        afterHookedMethodSafe(param)
        blockReplaceIconResId.set(false)
      }

      fun afterHookedMethodSafe(param: MethodHookParam) {
        val info = param.result as? PackageItemInfo ?: return
        info.packageName ?: return
        val ip = getIP() ?: return
        replaceIconResIdInInfo(info, ip.getId(getComponentName(info)))
      }
    }

  private abstract inner class ReplaceInList : XC_MethodHook() {
    override fun beforeHookedMethod(param: MethodHookParam) {
      val oldBlock = blockReplaceIconResId.get()
      blockReplaceIconResId.set(true)
      replaceIconResId(param)
      blockReplaceIconResId.set(oldBlock)
    }

    fun replaceIconResId(param: MethodHookParam) {
      val ip = getIP() ?: return
      param.result = param.callOriginalMethod<Unit>()
      val list = getList(param) ?: return
      val clazz = list.getOrNull(0)?.javaClass ?: return
      val replacer = listReplacerMap[clazz] ?: return
      replacer(list, ip)
    }

    abstract fun getList(param: MethodHookParam): List<Any?>?
  }

  override fun onHookApp(lpp: LoadPackageParam) {
    ReflectHelper.hookAllMethods(
      ApplicationInfo.CREATOR.javaClass,
      "createFromParcel",
      replaceIconResId,
    )
    ReflectHelper.hookAllMethods(
      ActivityInfo.CREATOR.javaClass,
      "createFromParcel",
      replaceIconResId,
    )

    ReflectHelper.hookAllMethods(
      Parcel::class.java,
      "readTypedList",
      object : ReplaceInList() {
        override fun getList(param: MethodHookParam) = param.args[0].asType<List<Any?>>()
      },
    )
    ReflectHelper.hookAllMethods(
      Parcel::class.java,
      "createTypedArray",
      object : ReplaceInList() {
        override fun getList(param: MethodHookParam) = param.result.asType<Array<Any?>>()?.toList()
      },
    )
    val baseParceledListSlice =
      ReflectHelper.findClass("android.content.pm.BaseParceledListSlice") ?: return
    val mListF = ReflectHelper.findField(baseParceledListSlice, "mList") ?: return
    ReflectHelper.hookAllConstructors(
      baseParceledListSlice,
      object : ReplaceInList() {
        override fun getList(param: MethodHookParam) = mListF.getAs<List<Any?>?>(param.thisObject)
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
    if (shortcut)
      ReflectHelper.hookAllMethods(
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

private fun ResolveInfo.getComponentInfo(): ComponentInfo {
  if (activityInfo != null) return activityInfo
  if (serviceInfo != null) return serviceInfo
  if (providerInfo != null) return providerInfo
  throw IllegalStateException("Missing ComponentInfo!")
}
