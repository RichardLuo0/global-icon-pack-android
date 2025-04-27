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
import android.os.Parcelable
import com.richardluo.globalIconPack.iconPack.Source
import com.richardluo.globalIconPack.iconPack.getComponentName
import com.richardluo.globalIconPack.iconPack.getSC
import com.richardluo.globalIconPack.reflect.ClockDrawableWrapper
import com.richardluo.globalIconPack.reflect.Resources.getDrawableForDensityM
import com.richardluo.globalIconPack.utils.ReflectHelper
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.callOriginalMethod
import com.richardluo.globalIconPack.utils.getAs
import com.richardluo.globalIconPack.utils.isHighTwoByte
import com.richardluo.globalIconPack.utils.withHighByteSet
import com.richardluo.globalIconPack.xposed.ReplaceIcon.Companion.ANDROID_DEFAULT
import com.richardluo.globalIconPack.xposed.ReplaceIcon.Companion.IN_SC
import com.richardluo.globalIconPack.xposed.ReplaceIcon.Companion.NOT_IN_SC
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

typealias ListReplacer = (list: Iterable<Any?>, sc: Source) -> Unit

// Resource id always starts with 0x7f, use it to indicate that this is an icon
// Assume the icon res id is only used in getDrawable()
class ReplaceIcon(val shortcut: Boolean) : Hook {
  companion object {
    const val IN_SC = 0xff000000.toInt()
    const val NOT_IN_SC = 0xfe000000.toInt()
    const val ANDROID_DEFAULT = 0x7f000000
    const val SC_DEFAULT = 0x00000000
  }

  override fun onHookPixelLauncher(lpp: LoadPackageParam) {
    // Find needed class for clock
    ClockDrawableWrapper.initWithPixelLauncher(lpp)
  }

  private val listReplacerMap = run {
    val defaultReplacer: ListReplacer = { list, sc ->
      replaceIconInItemInfoList(list.mapNotNull { it.asType<PackageItemInfo>() }, sc)
    }
    val componentInfoReplacer: ListReplacer = { list, sc ->
      defaultReplacer(list, sc)
      replaceIconInItemInfoList(list.mapNotNull { it.asType<ComponentInfo>()?.applicationInfo }, sc)
    }
    val resolveInfoReplacer: ListReplacer? = run {
      { list, sc ->
        val riList = list.mapNotNull { it.asType<ResolveInfo>() }
        componentInfoReplacer(riList.map { it.getComponentInfo() }, sc)
        riList.forEach(::replaceIconInResolveInfo)
      }
    }

    buildMap<Parcelable.Creator<*>, ListReplacer> {
      fun setCreator(parcelableC: Class<*>, replacer: ListReplacer) {
        set(ReflectHelper.findField(parcelableC, "CREATOR")?.getAs() ?: return, replacer)
      }

      setCreator(ApplicationInfo::class.java, defaultReplacer)
      setCreator(ActivityInfo::class.java, componentInfoReplacer)
      setCreator(ServiceInfo::class.java, componentInfoReplacer)
      resolveInfoReplacer?.let { setCreator(ResolveInfo::class.java, it) }
      setCreator(PackageInfo::class.java) { list, sc ->
        val piList = list.mapNotNull { it.asType<PackageInfo>() }
        defaultReplacer(piList.mapNotNull { it.applicationInfo }, sc)
        piList.forEach { pi -> pi.activities?.let { componentInfoReplacer(it.toList(), sc) } }
      }
      run {
        val topActivityInfoF =
          ReflectHelper.findField(TaskInfo::class.java, "topActivityInfo") ?: return@run
        val taskInfoReplacer: ListReplacer = { list, sc ->
          defaultReplacer(
            list.mapNotNull { it?.let { topActivityInfoF.getAs<ActivityInfo>(it) } },
            sc,
          )
        }
        setCreator(RunningTaskInfo::class.java, taskInfoReplacer)
        setCreator(RecentTaskInfo::class.java, taskInfoReplacer)
      }
      run {
        val launcherActivityInfo =
          ReflectHelper.findClass("android.content.pm.LauncherActivityInfoInternal") ?: return@run
        val mActivityInfoF =
          ReflectHelper.findField(launcherActivityInfo, "mActivityInfo") ?: return@run
        setCreator(launcherActivityInfo) { list, sc ->
          componentInfoReplacer(
            list.mapNotNull { it?.let { mActivityInfoF.getAs<ActivityInfo>(it) } },
            sc,
          )
        }
      }
      run {
        val launchActivityItem =
          ReflectHelper.findClass("android.app.servertransaction.LaunchActivityItem") ?: return@run
        val mInfoF = ReflectHelper.findField(launchActivityItem, "mInfo") ?: return@run
        setCreator(launchActivityItem) { list, sc ->
          componentInfoReplacer(list.mapNotNull { it?.let { mInfoF.getAs<ActivityInfo>(it) } }, sc)
        }
      }
      resolveInfoReplacer?.let {
        setCreator(AccessibilityServiceInfo::class.java) { list, sc ->
          it(list.mapNotNull { it.asType<AccessibilityServiceInfo>()?.resolveInfo }, sc)
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
        val info = param.thisObject as? PackageItemInfo ?: return
        info.packageName ?: return
        val sc = getSC() ?: return
        replaceIconInItemInfo(info, sc.getId(getComponentName(info)))
      }
    }

  private abstract inner class ReplaceInList : XC_MethodHook() {
    private var replacer: ListReplacer? = null

    override fun beforeHookedMethod(param: MethodHookParam) {
      replacer = getReplacer(param) ?: return
      val oldBlock = blockReplaceIconResId.get()
      blockReplaceIconResId.set(true)
      beforeHookedMethodSafe(param)
      blockReplaceIconResId.set(oldBlock)
    }

    fun beforeHookedMethodSafe(param: MethodHookParam) {
      val sc = getSC() ?: return
      param.result = param.callOriginalMethod<Unit>()
      val list = getList(param) ?: return
      replacer?.invoke(list, sc)
    }

    abstract fun getReplacer(param: MethodHookParam): ListReplacer?

    abstract fun getList(param: MethodHookParam): Iterable<Any?>?
  }

  override fun onHookApp(lpp: LoadPackageParam) {
    ReflectHelper.hookAllConstructors(ApplicationInfo::class.java, replaceIconResId)
    ReflectHelper.hookAllConstructors(ActivityInfo::class.java, replaceIconResId)
    ReflectHelper.hookAllConstructors(
      ResolveInfo::class.java,
      object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
          if (blockReplaceIconResId.get() == true) return
          replaceIconInResolveInfo(param.thisObject.asType() ?: return)
        }
      },
    )

    ReflectHelper.hookAllMethods(
      Parcel::class.java,
      "readTypedList",
      object : ReplaceInList() {
        override fun getReplacer(param: MethodHookParam): ListReplacer? =
          param.args.getOrNull(1)?.let { listReplacerMap[it] }

        override fun getList(param: MethodHookParam): Iterable<Any?>? = param.args[0].asType()
      },
    )
    ReflectHelper.hookAllMethods(
      Parcel::class.java,
      "createTypedArray",
      object : ReplaceInList() {
        override fun getReplacer(param: MethodHookParam): ListReplacer? =
          param.args.getOrNull(0)?.let { listReplacerMap[it] }

        override fun getList(param: MethodHookParam) =
          param.result.asType<Array<Any?>>()?.asIterable()
      },
    )
    hookParceledListSlice()

    ReflectHelper.hookMethod(
      getDrawableForDensityM ?: return,
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          val resId = param.args[0] as? Int ?: return
          val density = param.args[1] as? Int ?: return
          param.result =
            when {
              isHighTwoByte(resId, IN_SC) ->
                getSC()?.getIcon(withHighByteSet(resId, SC_DEFAULT), density)
              isHighTwoByte(resId, NOT_IN_SC) -> {
                param.args[0] = withHighByteSet(resId, ANDROID_DEFAULT)
                param.callOriginalMethod<Drawable?>()?.let { getSC()?.genIconFrom(it) ?: it }
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
            val sc = getSC() ?: return
            param.result =
              sc.getIconEntry(getComponentName(shortcut))?.let { sc.getIcon(it, density) }
                ?: param.callOriginalMethod<Drawable?>()?.let { sc.genIconFrom(it) }
          }
        },
      )
  }

  private fun hookParceledListSlice() {
    val baseParceledListSlice =
      ReflectHelper.findClass("android.content.pm.BaseParceledListSlice") ?: return
    val mListF = ReflectHelper.findField(baseParceledListSlice, "mList") ?: return
    val replacer = ThreadLocal.withInitial<ListReplacer?> { null }
    ReflectHelper.hookAllConstructors(
      baseParceledListSlice,
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          val oldBlock = blockReplaceIconResId.get()
          blockReplaceIconResId.set(true)
          beforeHookedMethodSafe(param)
          blockReplaceIconResId.set(oldBlock)
        }

        fun beforeHookedMethodSafe(param: MethodHookParam) {
          val sc = getSC() ?: return
          param.result = param.callOriginalMethod<Unit>()
          val currentReplacer = replacer.get()
          if (currentReplacer == null) return
          val list = mListF.getAs<List<Any?>?>(param.thisObject) ?: return
          currentReplacer.invoke(list, sc)
          replacer.set(null)
        }
      },
    )
    val parceledListSlice =
      ReflectHelper.findClass("android.content.pm.ParceledListSlice") ?: return
    ReflectHelper.hookAllMethods(
      parceledListSlice,
      "readParcelableCreator",
      object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
          if (blockReplaceIconResId.get() == false) return
          val currentReplacer = listReplacerMap[param.result]
          // Not the class we can batch replace, replaced by replaceIconResId
          if (currentReplacer == null) blockReplaceIconResId.set(false)
          replacer.set(currentReplacer)
        }
      },
    )
  }
}

private fun replaceIconInItemInfo(info: PackageItemInfo, id: Int?) {
  info.icon =
    id?.let { withHighByteSet(it, IN_SC) }
      ?: if (isHighTwoByte(info.icon, ANDROID_DEFAULT)) withHighByteSet(info.icon, NOT_IN_SC)
      else info.icon
}

private val iconResourceIdF = ReflectHelper.findField(ResolveInfo::class.java, "iconResourceId")

private fun replaceIconInResolveInfo(ri: ResolveInfo) {
  val icon = ri.getComponentInfo().icon.takeIf { it != 0 } ?: return
  ri.icon = icon
  iconResourceIdF?.set(ri, icon)
}

private fun replaceIconInItemInfoList(list: List<PackageItemInfo>, sc: Source) {
  sc.getId(list.map { getComponentName(it) }).forEachIndexed { i, it ->
    replaceIconInItemInfo(list[i], it)
  }
}

private fun ResolveInfo.getComponentInfo(): ComponentInfo {
  if (activityInfo != null) return activityInfo
  if (serviceInfo != null) return serviceInfo
  if (providerInfo != null) return providerInfo
  throw IllegalStateException("Missing ComponentInfo!")
}
