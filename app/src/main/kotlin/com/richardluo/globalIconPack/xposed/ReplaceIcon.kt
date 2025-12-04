package com.richardluo.globalIconPack.xposed

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.app.ActivityManager.RecentTaskInfo
import android.app.ActivityManager.RunningTaskInfo
import android.app.TaskInfo
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.content.pm.PackageItemInfo
import android.content.pm.ProviderInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import androidx.core.graphics.drawable.toDrawable
import com.richardluo.globalIconPack.iconPack.getSC
import com.richardluo.globalIconPack.iconPack.source.Source
import com.richardluo.globalIconPack.iconPack.source.getComponentName
import com.richardluo.globalIconPack.reflect.BaseIconFactory
import com.richardluo.globalIconPack.reflect.Resources.getDrawableForDensityM
import com.richardluo.globalIconPack.utils.HookBuilder
import com.richardluo.globalIconPack.utils.IconHelper
import com.richardluo.globalIconPack.utils.allConstructors
import com.richardluo.globalIconPack.utils.allMethods
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.callOriginalMethod
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.field
import com.richardluo.globalIconPack.utils.getAs
import com.richardluo.globalIconPack.utils.hook
import com.richardluo.globalIconPack.utils.isHighTwoByte
import com.richardluo.globalIconPack.utils.logD
import com.richardluo.globalIconPack.utils.method
import com.richardluo.globalIconPack.utils.runSafe
import com.richardluo.globalIconPack.utils.withHighByteSet
import com.richardluo.globalIconPack.xposed.ReplaceIcon.Companion.ANDROID_DEFAULT
import com.richardluo.globalIconPack.xposed.ReplaceIcon.Companion.IN_SC
import com.richardluo.globalIconPack.xposed.ReplaceIcon.Companion.NOT_IN_SC
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.util.Collections
import java.util.WeakHashMap

// Resource id always starts with 0x7f, use it to indicate that this is an icon
// Assume the icon res id is only used in getDrawable()
class ReplaceIcon(
  private val shortcut: Boolean,
  private val forceActivityIconForTask: Boolean,
  private val taskIconScale: Float,
) : Hook {
  companion object {
    const val IN_SC = 0xff000000.toInt()
    const val NOT_IN_SC = 0xfe000000.toInt()
    const val ANDROID_DEFAULT = 0x7f000000
    const val SC_DEFAULT = 0x00000000
  }

  override fun onHookPixelLauncher(lpp: LoadPackageParam) {
    runSafe {
      // Replace icon in task description
      val taskIconCache = classOf("com.android.quickstep.TaskIconCache", lpp) ?: return@runSafe
      val getIconM =
        taskIconCache.method(
          "getIcon",
          ActivityManager.TaskDescription::class.java,
          Int::class.javaPrimitiveType,
        ) ?: return@runSafe

      if (forceActivityIconForTask) getIconM.hook { replace { null } }
      else {
        val tdBitmapSet = Collections.newSetFromMap<Bitmap>(WeakHashMap())
        getIconM.hook {
          before {
            result = callOriginalMethod()
            tdBitmapSet.add(result.asType() ?: return@before)
          }
        }
        taskIconCache.allMethods("getBitmapInfo").hook {
          before {
            val drawable = args[0].asType<BitmapDrawable>() ?: return@before
            if (tdBitmapSet.contains(drawable.bitmap)) {
              val background =
                args[2].asType<Int>()?.let { Color.valueOf(it).toDrawable() }
                  ?: Color.TRANSPARENT.toDrawable()
              getSC()?.run {
                args[0] = genIconFrom(IconHelper.makeAdaptive(drawable, background, taskIconScale))
              }
            }
          }
        }
      }
    }

    runSafe {
      val iconOptions = BaseIconFactory.getIconOptionsClass(lpp) ?: return@runSafe
      val isFullBleedF = iconOptions.field("isFullBleed") ?: return@runSafe
      BaseIconFactory.getClass(lpp)?.allMethods("createBadgedIconBitmap")?.hook {
        before { isFullBleedF.set(args[1], false) }
      }
    }
  }

  override fun onHookApp(lpp: LoadPackageParam) {
    ApplicationInfo::class.java.allConstructors().hook(HookBuilder::replaceIconHook)
    ActivityInfo::class.java.allConstructors().hook(HookBuilder::replaceIconHook)
    ServiceInfo::class.java.allConstructors().hook(HookBuilder::replaceIconHook)
    ProviderInfo::class.java.allConstructors().hook(HookBuilder::replaceIconHook)
    ResolveInfo::class.java.allConstructors().hook {
      after {
        if (blockReplaceIconResId.get() == true) return@after
        replaceIconInResolveInfo(thisObject.asType() ?: return@after)
      }
    }

    PackageInfo::class.java.allConstructors().hook {
      before {
        if (blockReplaceIconResId.get() == true) return@before
        blockReplaceIconResId.set(true)
        runSafe {
          result = callOriginalMethod<Unit>()
          val info = thisObject as? PackageInfo ?: return@runSafe
          val sc = getSC() ?: return@runSafe
          replaceIconInItemInfos(packageInfoTransform(info), sc)
          logD("Batch replaced PackageInfo: ${info.packageName}")
        }
        blockReplaceIconResId.set(false)
      }
    }

    Parcel::class.java.allMethods("readTypedList").hook {
      batchReplaceIconHook(
        { args.getOrNull(1)?.let { batchReplacerMap[it] } },
        { args[0].asType() },
      )
    }
    Parcel::class.java.allMethods("createTypedArray").hook {
      batchReplaceIconHook(
        { args.getOrNull(0)?.let { batchReplacerMap[it] } },
        { result.asType<Array<Any?>>()?.asIterable() },
      )
    }
    hookParceledListSlice()

    getDrawableForDensityM?.hook {
      before {
        val resId = args[0] as? Int ?: return@before
        val density = args[1] as? Int ?: return@before
        result =
          when {
            isHighTwoByte(resId, IN_SC) ->
              getSC()?.getIcon(withHighByteSet(resId, SC_DEFAULT), density)
            isHighTwoByte(resId, NOT_IN_SC) -> {
              args[0] = withHighByteSet(resId, ANDROID_DEFAULT)
              callOriginalMethod<Drawable?>()?.let { getSC()?.genIconFrom(it) ?: it }
            }
            resId == android.R.drawable.sym_def_app_icon ->
              callOriginalMethod<Drawable?>()?.let { getSC()?.genIconFrom(it) ?: it }
            else -> return@before
          }
      }
    }

    // Generate shortcut icon
    if (shortcut)
      LauncherApps::class.java.allMethods("getShortcutIconDrawable").hook {
        before {
          val shortcut = args[0] as? ShortcutInfo ?: return@before
          val density = args[1] as? Int ?: return@before
          val sc = getSC() ?: return@before
          result =
            sc.getIconEntry(getComponentName(shortcut))?.let { sc.getIcon(it, density) }
              ?: callOriginalMethod<Drawable?>()?.let { sc.genIconFrom(it) }
        }
      }
  }

  private fun hookParceledListSlice() {
    val baseParceledListSlice = classOf("android.content.pm.BaseParceledListSlice") ?: return
    val mListF = baseParceledListSlice.field("mList") ?: return
    val plsMap = ThreadLocal.withInitial { WeakHashMap<Any, BatchReplacer?>() }
    baseParceledListSlice.allConstructors().hook {
      before {
        val oldBlock = blockReplaceIconResId.get()
        blockReplaceIconResId.set(true)
        val localPlsMap = plsMap.get() ?: return@before
        runSafe {
          val sc = getSC() ?: return@runSafe
          result = callOriginalMethod<Unit>()
          val list = mListF.getAs<List<Any?>?>(thisObject) ?: return@runSafe
          val currentReplacer =
            localPlsMap[thisObject]
              ?: if (blockReplaceIconResId.get() == true) {
                batchReplacerMap[list.getOrNull(0)?.javaClass?.field("CREATOR")?.getAs()]
                  ?: return@runSafe
              } else return@runSafe
          currentReplacer(list.asSequence(), sc)
          logD("Batch replaced ParceledListSlice: " + list.size)
        }
        localPlsMap.remove(thisObject)
        blockReplaceIconResId.set(oldBlock)
      }
    }
    classOf("android.content.pm.ParceledListSlice")?.allMethods("readParcelableCreator")?.hook {
      after {
        if (blockReplaceIconResId.get() == false) return@after
        val localPlsMap = plsMap.get() ?: return@after
        val replacer = batchReplacerMap[result]
        // Not a class we can batch replace
        if (replacer == null) blockReplaceIconResId.set(false)
        localPlsMap[thisObject] = replacer
      }
    }
  }
}

private typealias BatchReplacer = (seq: Sequence<Any?>, sc: Source) -> Unit

private val blockReplaceIconResId = ThreadLocal.withInitial { false }

private fun HookBuilder.replaceIconHook() {
  after {
    if (blockReplaceIconResId.get() == true) return@after
    blockReplaceIconResId.set(true)
    runSafe {
      val info = thisObject as? PackageItemInfo ?: return@runSafe
      info.packageName ?: return@runSafe
      val sc = getSC() ?: return@runSafe
      replaceIconInItemInfo(info, sc.getId(getComponentName(info)))
      logD("Single replaced: ${info.packageName}/${info.name}")
    }
    blockReplaceIconResId.set(false)
  }
}

private inline fun HookBuilder.batchReplaceIconHook(
  crossinline getReplacer: MethodHookParam.() -> BatchReplacer?,
  crossinline getList: MethodHookParam.() -> Iterable<Any?>?,
) {
  before {
    val replacer = getReplacer() ?: return@before
    val oldBlock = blockReplaceIconResId.get()
    blockReplaceIconResId.set(true)
    runSafe {
      val sc = getSC() ?: return@runSafe
      result = callOriginalMethod()
      val list = getList() ?: return@runSafe
      replacer(list.asSequence(), sc)
      logD("Batch replaced: " + list.count())
    }
    blockReplaceIconResId.set(oldBlock)
  }
}

private fun replaceIconInItemInfo(info: PackageItemInfo, id: Int?) {
  // logD("Replace in ItemInfo: ${info.packageName}/${info.name}: $id")

  // Bypass quick settings tile icon
  if (
    info is ServiceInfo && info.permission == android.Manifest.permission.BIND_QUICK_SETTINGS_TILE
  )
    return

  if (id != null) info.icon = withHighByteSet(id, IN_SC)
  else if (isHighTwoByte(info.icon, ANDROID_DEFAULT))
    info.icon = withHighByteSet(info.icon, NOT_IN_SC)
}

private val iconResourceIdF by lazy { ResolveInfo::class.java.field("iconResourceId") }

private fun replaceIconInResolveInfo(ri: ResolveInfo) {
  val icon = ri.getComponentInfo()?.icon.takeIf { it != 0 } ?: return
  ri.icon = icon
  iconResourceIdF?.set(ri, icon)
}

private fun replaceIconInItemInfos(seq: Sequence<PackageItemInfo>, sc: Source) {
  val ids = sc.getId(seq.map { getComponentName(it) }.toList())
  seq.forEachIndexed { i, info -> replaceIconInItemInfo(info, ids.getOrNull(i)) }
}

private fun ResolveInfo.getComponentInfo(): ComponentInfo? {
  if (activityInfo != null) return activityInfo
  if (serviceInfo != null) return serviceInfo
  if (providerInfo != null) return providerInfo
  return null
}

fun itemInfosTransform(seq: Sequence<Any?>) = seq.mapNotNull { it.asType<PackageItemInfo>() }

fun componentInfosTransform(seq: Sequence<Any?>) =
  itemInfosTransform(seq) + seq.mapNotNull { it.asType<ComponentInfo>()?.applicationInfo }

fun packageInfoTransform(pi: PackageInfo) = sequence {
  pi.applicationInfo?.let { yield(it) }
  pi.activities?.let { yieldAll(componentInfosTransform(it.asSequence())) }
  pi.services?.let { yieldAll(componentInfosTransform(it.asSequence())) }
  pi.providers?.let { yieldAll(componentInfosTransform(it.asSequence())) }
}

fun resolveInfoReplacer(seq: Sequence<Any?>, sc: Source) {
  val riSeq = seq.mapNotNull { it.asType<ResolveInfo>() }
  replaceIconInItemInfos(componentInfosTransform(riSeq.map { it.getComponentInfo() }), sc)
  riSeq.forEach(::replaceIconInResolveInfo)
}

private val batchReplacerMap =
  buildMap<Parcelable.Creator<*>, BatchReplacer> {
    fun setWithCreator(parcelableC: Class<*>, replacer: BatchReplacer) {
      set(parcelableC.field("CREATOR")?.getAs() ?: return, replacer)
    }

    fun setWithCreatorItemInfo(
      parcelableC: Class<*>,
      transform: (Sequence<Any?>) -> Sequence<PackageItemInfo>,
    ) {
      setWithCreator(parcelableC) { seq, sc -> replaceIconInItemInfos(transform(seq), sc) }
    }

    setWithCreatorItemInfo(ApplicationInfo::class.java, ::itemInfosTransform)
    setWithCreatorItemInfo(ActivityInfo::class.java, ::componentInfosTransform)
    setWithCreatorItemInfo(ServiceInfo::class.java, ::componentInfosTransform)
    setWithCreatorItemInfo(ProviderInfo::class.java, ::componentInfosTransform)

    setWithCreator(ResolveInfo::class.java, ::resolveInfoReplacer)

    setWithCreatorItemInfo(PackageInfo::class.java) { seq ->
      seq.mapNotNull { it.asType<PackageInfo>() }.flatMap { pi -> packageInfoTransform(pi) }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
      runSafe {
        val topActivityInfoF = TaskInfo::class.java.field("topActivityInfo") ?: return@runSafe
        fun taskInfoTransform(seq: Sequence<Any?>) =
          componentInfosTransform(seq.mapNotNull { it?.let { topActivityInfoF.get(it) } })

        setWithCreatorItemInfo(RunningTaskInfo::class.java, ::taskInfoTransform)
        setWithCreatorItemInfo(RecentTaskInfo::class.java, ::taskInfoTransform)
      }

    runSafe {
      val launcherActivityInfo =
        classOf("android.content.pm.LauncherActivityInfoInternal") ?: return@runSafe
      val mActivityInfoF = launcherActivityInfo.field("mActivityInfo") ?: return@runSafe
      setWithCreatorItemInfo(launcherActivityInfo) { list ->
        componentInfosTransform(list.mapNotNull { it?.let { mActivityInfoF.get(it) } })
      }
    }

    runSafe {
      val launchActivityItem =
        classOf("android.app.servertransaction.LaunchActivityItem") ?: return@runSafe
      val mInfoF = launchActivityItem.field("mInfo") ?: return@runSafe
      setWithCreatorItemInfo(launchActivityItem) { list ->
        componentInfosTransform(list.mapNotNull { it?.let { mInfoF.get(it) } })
      }
    }

    setWithCreator(AccessibilityServiceInfo::class.java) { list, sc ->
      resolveInfoReplacer(
        list.mapNotNull { it.asType<AccessibilityServiceInfo>()?.resolveInfo },
        sc,
      )
    }
  }
