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
import com.richardluo.globalIconPack.iconPack.source.Source
import com.richardluo.globalIconPack.iconPack.source.getComponentName
import com.richardluo.globalIconPack.iconPack.getSC
import com.richardluo.globalIconPack.reflect.ClockDrawableWrapper
import com.richardluo.globalIconPack.reflect.Resources.getDrawableForDensityM
import com.richardluo.globalIconPack.utils.HookBuilder
import com.richardluo.globalIconPack.utils.allConstructors
import com.richardluo.globalIconPack.utils.allMethods
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.callOriginalMethod
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.field
import com.richardluo.globalIconPack.utils.getAs
import com.richardluo.globalIconPack.utils.hook
import com.richardluo.globalIconPack.utils.isHighTwoByte
import com.richardluo.globalIconPack.utils.runSafe
import com.richardluo.globalIconPack.utils.withHighByteSet
import com.richardluo.globalIconPack.xposed.ReplaceIcon.Companion.ANDROID_DEFAULT
import com.richardluo.globalIconPack.xposed.ReplaceIcon.Companion.IN_SC
import com.richardluo.globalIconPack.xposed.ReplaceIcon.Companion.NOT_IN_SC
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

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

  override fun onHookApp(lpp: LoadPackageParam) {
    ApplicationInfo::class.java.allConstructors().hook(HookBuilder::replaceIconHook)
    ActivityInfo::class.java.allConstructors().hook(HookBuilder::replaceIconHook)
    ResolveInfo::class.java.allConstructors().hook {
      after {
        if (blockReplaceIconResId.get() == true) return@after
        replaceIconInResolveInfo(it.thisObject.asType() ?: return@after)
      }
    }

    Parcel::class.java.allMethods("readTypedList").hook {
      batchReplaceIconHook(
        { it.args.getOrNull(1)?.let { listReplacerMap[it] } },
        { it.args[0].asType() },
      )
    }
    Parcel::class.java.allMethods("createTypedArray").hook {
      batchReplaceIconHook(
        { it.args.getOrNull(0)?.let { listReplacerMap[it] } },
        { it.result.asType<Array<Any?>>()?.asIterable() },
      )
    }
    hookParceledListSlice()

    getDrawableForDensityM.hook {
      before {
        val resId = it.args[0] as? Int ?: return@before
        val density = it.args[1] as? Int ?: return@before
        it.result =
          when {
            isHighTwoByte(resId, IN_SC) ->
              getSC()?.getIcon(withHighByteSet(resId, SC_DEFAULT), density)
            isHighTwoByte(resId, NOT_IN_SC) -> {
              it.args[0] = withHighByteSet(resId, ANDROID_DEFAULT)
              it.callOriginalMethod<Drawable?>()?.let { getSC()?.genIconFrom(it) ?: it }
            }
            else -> return@before
          }
      }
    }

    // Generate shortcut icon
    if (shortcut)
      LauncherApps::class.java.allMethods("getShortcutIconDrawable").hook {
        before {
          val shortcut = it.args[0] as? ShortcutInfo ?: return@before
          val density = it.args[1] as? Int ?: return@before
          val sc = getSC() ?: return@before
          it.result =
            sc.getIconEntry(getComponentName(shortcut))?.let { sc.getIcon(it, density) }
              ?: it.callOriginalMethod<Drawable?>()?.let { sc.genIconFrom(it) }
        }
      }
  }

  private fun hookParceledListSlice() {
    val baseParceledListSlice = classOf("android.content.pm.BaseParceledListSlice")
    val mListF = baseParceledListSlice.field("mList") ?: return
    val replacer = ThreadLocal.withInitial<ListReplacer?> { null }
    baseParceledListSlice.allConstructors().hook {
      before {
        val oldBlock = blockReplaceIconResId.get()
        blockReplaceIconResId.set(true)
        runSafe {
          val sc = getSC() ?: return@runSafe
          it.result = it.callOriginalMethod<Unit>()
          val currentReplacer = replacer.get()
          if (currentReplacer == null) return@runSafe
          val list = mListF.getAs<List<Any?>?>(it.thisObject) ?: return@runSafe
          currentReplacer.invoke(list, sc)
          replacer.set(null)
        }
        blockReplaceIconResId.set(oldBlock)
      }
    }
    classOf("android.content.pm.ParceledListSlice").allMethods("readParcelableCreator").hook {
      after {
        if (blockReplaceIconResId.get() == false) return@after
        val currentReplacer = listReplacerMap[it.result]
        // Not a class we can batch replace
        if (currentReplacer == null) blockReplaceIconResId.set(false)
        replacer.set(currentReplacer)
      }
    }
  }
}

private typealias ListReplacer = (list: Iterable<Any?>, sc: Source) -> Unit

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
      set(parcelableC.field("CREATOR")?.getAs() ?: return, replacer)
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
    runSafe {
      val topActivityInfoF = TaskInfo::class.java.field("topActivityInfo") ?: return@runSafe
      val taskInfoReplacer: ListReplacer = { list, sc ->
        defaultReplacer(
          list.mapNotNull { it?.let { topActivityInfoF.getAs<ActivityInfo>(it) } },
          sc,
        )
      }
      setCreator(RunningTaskInfo::class.java, taskInfoReplacer)
      setCreator(RecentTaskInfo::class.java, taskInfoReplacer)
    }
    runSafe {
      val launcherActivityInfo =
        classOf("android.content.pm.LauncherActivityInfoInternal") ?: return@runSafe
      val mActivityInfoF = launcherActivityInfo.field("mActivityInfo") ?: return@runSafe
      setCreator(launcherActivityInfo) { list, sc ->
        componentInfoReplacer(
          list.mapNotNull { it?.let { mActivityInfoF.getAs<ActivityInfo>(it) } },
          sc,
        )
      }
    }
    runSafe {
      val launchActivityItem =
        classOf("android.app.servertransaction.LaunchActivityItem") ?: return@runSafe
      val mInfoF = launchActivityItem.field("mInfo") ?: return@runSafe
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

private fun HookBuilder.replaceIconHook() {
  after {
    if (blockReplaceIconResId.get() == true) return@after
    blockReplaceIconResId.set(true)
    runSafe {
      val info = it.thisObject as? PackageItemInfo ?: return@runSafe
      info.packageName ?: return@runSafe
      val sc = getSC() ?: return@runSafe
      replaceIconInItemInfo(info, sc.getId(getComponentName(info)))
    }
    blockReplaceIconResId.set(false)
  }
}

private fun HookBuilder.batchReplaceIconHook(
  getReplacer: (param: MethodHookParam) -> ListReplacer?,
  getList: (param: MethodHookParam) -> Iterable<Any?>?,
) {
  before {
    val replacer = getReplacer(it) ?: return@before
    val oldBlock = blockReplaceIconResId.get()
    blockReplaceIconResId.set(true)
    runSafe {
      val sc = getSC() ?: return@runSafe
      callOriginal(it)
      val list = getList(it) ?: return@runSafe
      replacer(list, sc)
    }
    blockReplaceIconResId.set(oldBlock)
  }
}

private fun replaceIconInItemInfo(info: PackageItemInfo, id: Int?) {
  info.icon =
    id?.let { withHighByteSet(it, IN_SC) }
      ?: if (isHighTwoByte(info.icon, ANDROID_DEFAULT)) withHighByteSet(info.icon, NOT_IN_SC)
      else info.icon
}

private val iconResourceIdF by lazy { ResolveInfo::class.java.field("iconResourceId") }

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
