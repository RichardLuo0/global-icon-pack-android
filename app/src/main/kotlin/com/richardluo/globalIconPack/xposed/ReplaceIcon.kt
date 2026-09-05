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
import android.graphics.drawable.AdaptiveIconDrawable
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
import com.richardluo.globalIconPack.utils.Logger.logD
import com.richardluo.globalIconPack.utils.MonochromeDrawable
import com.richardluo.globalIconPack.utils.allConstructors
import com.richardluo.globalIconPack.utils.allMethods
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.field
import com.richardluo.globalIconPack.utils.getAs
import com.richardluo.globalIconPack.utils.highByte
import com.richardluo.globalIconPack.utils.hook
import com.richardluo.globalIconPack.utils.hookCompat
import com.richardluo.globalIconPack.utils.isHighByte
import com.richardluo.globalIconPack.utils.method
import com.richardluo.globalIconPack.utils.proceed
import com.richardluo.globalIconPack.utils.runSafe
import com.richardluo.globalIconPack.utils.withHighByte
import com.richardluo.globalIconPack.xposed.ReplaceIcon.Companion.ANDROID_DEFAULT
import com.richardluo.globalIconPack.xposed.ReplaceIcon.Companion.IN_SC
import com.richardluo.globalIconPack.xposed.ReplaceIcon.Companion.NOT_IN_SC
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface
import java.util.Collections
import java.util.WeakHashMap

// Resource id always starts with 0x7f, use it to indicate that this is an icon
// Assume the icon res id is only used in getDrawable()
class ReplaceIcon(
  private val shortcut: Boolean,
  private val forceActivityIconForTask: Boolean,
  private val taskIconScale: Float,
  private val forceMonochrome: Boolean,
) : Hook {
  companion object {
    const val IN_SC = 0x6f000000
    const val NOT_IN_SC = 0x6e000000
    const val ANDROID_DEFAULT = 0x7f000000
    const val SC_DEFAULT = 0x00000000
  }

  context(xposed: XposedInterface)
  override fun onHookPixelLauncher(param: XposedModuleInterface.PackageReadyParam) {
    runSafe {
      // Replace icon in task description
      val taskIconCache = classOf("com.android.quickstep.TaskIconCache", param) ?: return@runSafe
      val getIconM =
        taskIconCache.method(
          "getIcon",
          ActivityManager.TaskDescription::class.java,
          Int::class.javaPrimitiveType,
        ) ?: return@runSafe

      if (forceActivityIconForTask) getIconM.hook { null }
      else {
        val tdBitmapSet = Collections.newSetFromMap<Bitmap>(WeakHashMap())
        getIconM.hookCompat {
          after {
            tdBitmapSet.add(result.asType() ?: return@after)
          }
        }
        taskIconCache.allMethods("getBitmapInfo").hookCompat {
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
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return@runSafe
      val iconOptions = BaseIconFactory.getIconOptionsClass(param) ?: return@runSafe
      val drawFullBleedF = iconOptions.field("drawFullBleed") ?: return@runSafe
      BaseIconFactory.getClass(param)?.allMethods("createBadgedIconBitmap")?.hookCompat {
        before { drawFullBleedF.set(args[1], false) }
      }
    }
  }

  context(xposed: XposedInterface)
  override fun onHookApp(param: XposedModuleInterface.PackageReadyParam) {
    // Find the drawable corresponding to the replaced icon
    getDrawableForDensityM?.hookCompat {
      before {
        val resId = args[0] as? Int ?: return@before
        val density = args[1] as? Int ?: return@before
        if (resId == android.R.drawable.sym_def_app_icon) {
          result = proceed<Drawable?>()?.let { getSC()?.genIconFrom(it) ?: it }
          return@before
        }
        result =
          when (resId.highByte()) {
            IN_SC -> getSC()?.getIcon(resId.withHighByte(SC_DEFAULT), density)
            NOT_IN_SC -> {
              args[0] = resId.withHighByte(ANDROID_DEFAULT)
              var drawable = proceedWithArgs<Drawable?>()

              if (
                forceMonochrome &&
                  Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                  drawable is AdaptiveIconDrawable
              )
                drawable.monochrome?.let {
                  drawable = MonochromeDrawable(thisObject.asType()!!, it)
                }

              drawable?.let { getSC()?.genIconFrom(it) ?: it }
            }
            else -> return@before
          }
      }
    }

    // ArchivedAppIcon
    classOf("android.app.ApplicationPackageManager")?.allMethods("getArchivedAppIcon")?.hookCompat {
      before {
        val packageName = args[0] as? String ?: return@before
        val sc = getSC() ?: return@before
        val entry = sc.getIconEntry(getComponentName(packageName)) ?: return@before
        val icon = sc.getIcon(entry, 0)
        if (icon != null) result = icon
      }

      after {
        result = result.asType<Drawable>()?.let { getSC()?.genIconFrom(it) ?: it }
      }
    }

    hookSingleReplaceIcon()
    hookBatchReplaceIcon()
    hookPackageInfoCommonUtils()

    // Generate shortcut icon
    if (shortcut)
      LauncherApps::class.java.allMethods("getShortcutIconDrawable").hookCompat {
        before {
          val shortcut = args[0] as? ShortcutInfo ?: return@before
          val density = args[1] as? Int ?: return@before
          val sc = getSC() ?: return@before
          result =
            sc.getIconEntry(getComponentName(shortcut))?.let { sc.getIcon(it, density) }
              ?: proceed<Drawable?>()?.let { sc.genIconFrom(it) }
        }
      }
  }

  context(xposed: XposedInterface)
  private fun hookSingleReplaceIcon() {
    fun HookBuilder.replaceIconHook() {
      after {
        runBlockReplaceIconResId {
          val info = thisObject as? PackageItemInfo ?: return@runBlockReplaceIconResId
          info.packageName ?: return@runBlockReplaceIconResId
          val sc = getSC() ?: return@runBlockReplaceIconResId
          replaceIconInItemInfo(info, sc.getId(getComponentName(info)), sc)
          logD("Single replaced: ${info.packageName}/${info.name}")
        }
      }
    }

    ApplicationInfo::class.java.allConstructors().hookCompat(HookBuilder::replaceIconHook)
    ActivityInfo::class.java.allConstructors().hookCompat(HookBuilder::replaceIconHook)
    ServiceInfo::class.java.allConstructors().hookCompat(HookBuilder::replaceIconHook)
    ProviderInfo::class.java.allConstructors().hookCompat(HookBuilder::replaceIconHook)
    ResolveInfo::class.java.allConstructors().hookCompat {
      after {
        runBlockReplaceIconResId {
          replaceIconInResolveInfo(thisObject.asType() ?: return@runBlockReplaceIconResId)
        }
      }
    }
    PackageInfo::class.java.allConstructors().hookCompat {
      before {
        runBlockReplaceIconResId {
          result = proceed()
          val info = thisObject as? PackageInfo ?: return@runBlockReplaceIconResId
          val sc = getSC() ?: return@runBlockReplaceIconResId
          replaceIconInItemInfos(packageInfoTransform(info), sc)
          logD("Batch replaced PackageInfo: ${info.packageName}")
        }
      }
    }
  }

  context(xposed: XposedInterface)
  private fun hookBatchReplaceIcon() {
    Parcel::class.java.allMethods("readTypedList").hookCompat {
      batchReplaceIconHook(
        { args.getOrNull(1)?.let { batchReplacerMap[it] } },
        { args[0].asType() },
      )
    }
    Parcel::class.java.allMethods("createTypedArray").hookCompat {
      batchReplaceIconHook(
        { args.getOrNull(0)?.let { batchReplacerMap[it] } },
        { result.asType<Array<Any?>>()?.asIterable() },
      )
    }
    hookParceledListSlice()
  }

  context(xposed: XposedInterface)
  private fun hookPackageInfoCommonUtils() {
    val packageInfoCommonUtils =
      classOf("com.android.internal.pm.parsing.PackageInfoCommonUtils") ?: return
    packageInfoCommonUtils.allMethods("generate").hookCompat {
      before {
        runBlockReplaceIconResId {
          result = proceed()
          val info = result as? PackageInfo ?: return@runBlockReplaceIconResId
          val sc = getSC() ?: return@runBlockReplaceIconResId
          replaceIconInItemInfos(packageInfoTransform(info), sc)
          logD("Batch replaced PackageInfo: ${info.packageName}")
        }
      }
    }

    fun HookBuilder.replaceIconHook() {
      after {
        runBlockReplaceIconResId {
          val info = result as? PackageItemInfo ?: return@runBlockReplaceIconResId
          info.packageName ?: return@runBlockReplaceIconResId
          val sc = getSC() ?: return@runBlockReplaceIconResId
          replaceIconInItemInfo(info, sc.getId(getComponentName(info)), sc)
          logD("Single replaced: ${info.packageName}/${info.name}")
        }
      }
    }

    packageInfoCommonUtils
      .allMethods("generateApplicationInfo")
      .hookCompat(HookBuilder::replaceIconHook)
    packageInfoCommonUtils
      .allMethods("generateActivityInfo")
      .hookCompat(HookBuilder::replaceIconHook)
    packageInfoCommonUtils
      .allMethods("generateServiceInfo")
      .hookCompat(HookBuilder::replaceIconHook)
    packageInfoCommonUtils
      .allMethods("generateProviderInfo")
      .hookCompat(HookBuilder::replaceIconHook)
  }

  context(xposed: XposedInterface)
  private fun hookParceledListSlice() {
    val baseParceledListSlice = classOf("android.content.pm.BaseParceledListSlice") ?: return
    val parceledListSlice = classOf("android.content.pm.ParceledListSlice") ?: return
    val mListF = baseParceledListSlice.field("mList") ?: return
    val replacer = ThreadLocal.withInitial<BatchReplacer?> { null }
    baseParceledListSlice.allConstructors().hookCompat {
      before {
        runBlockReplaceIconResId {
          val sc = getSC() ?: return@runBlockReplaceIconResId
          result = proceed()
          val list = mListF.getAs<List<Any?>?>(thisObject) ?: return@runBlockReplaceIconResId
          val curReplacer =
            replacer.get()
              ?: batchReplacerMap[list.getOrNull(0)?.javaClass?.field("CREATOR")?.getAs()]
              ?: return@runBlockReplaceIconResId
          curReplacer(list.asSequence(), sc)
          replacer.set(null)
          logD("Batch replaced ParceledListSlice: " + list.size)
        }
      }
    }
    // BaseParceledListSlice constructor will call ParceledListSlice.readParcelableCreator
    parceledListSlice.allMethods("readParcelableCreator").hookCompat {
      after {
        val curReplacer = batchReplacerMap[result] ?: return@after
        replacer.set(curReplacer)
      }
    }
  }
}

private fun replaceIconInItemInfo(info: PackageItemInfo, id: Int?, sc: Source) {
  // logD("Replace in ItemInfo: ${info.packageName}/${info.name}: $id")

  // Bypass quick settings tile icon
  if (
    info is ServiceInfo && info.permission == android.Manifest.permission.BIND_QUICK_SETTINGS_TILE
  )
    return

  if (id != null) info.icon = id.withHighByte(IN_SC)
  else if (info.icon.isHighByte(ANDROID_DEFAULT)) info.icon = info.icon.withHighByte(NOT_IN_SC)

  // Populate clock metadata
  id?.let { sc.getIconEntry(it) }?.addExtraTo(info, info.icon)
}

private typealias BatchReplacer = (seq: Sequence<Any?>, sc: Source) -> Unit

private val blockReplaceIconResId = ThreadLocal.withInitial { false }

private inline fun runBlockReplaceIconResId(crossinline block: () -> Unit) {
  if (blockReplaceIconResId.get() == true) return
  blockReplaceIconResId.set(true)
  block()
  blockReplaceIconResId.set(false)
}

context(xposed: XposedInterface)
private inline fun HookBuilder.batchReplaceIconHook(
  crossinline getReplacer: HookBuilder.ChainProxy.() -> BatchReplacer?,
  crossinline getList: HookBuilder.ChainProxy.() -> Iterable<Any?>?,
) {
  before {
    val replacer = getReplacer() ?: return@before
    runBlockReplaceIconResId {
      val sc = getSC() ?: return@runBlockReplaceIconResId
      result = proceed()
      val list = getList() ?: return@runBlockReplaceIconResId
      replacer(list.asSequence(), sc)
      logD("Batch replaced: " + list.count())
    }
  }
}

private val iconResourceIdF by lazy { ResolveInfo::class.java.field("iconResourceId") }

private fun replaceIconInResolveInfo(ri: ResolveInfo) {
  val icon = ri.getComponentInfo()?.icon.takeIf { it != 0 } ?: return
  ri.icon = icon
  iconResourceIdF?.set(ri, icon)
}

private fun replaceIconInItemInfos(seq: Sequence<PackageItemInfo>, sc: Source) {
  val ids = sc.getId(seq.map { getComponentName(it) }.toList())
  seq.forEachIndexed { i, info -> replaceIconInItemInfo(info, ids.getOrNull(i), sc) }
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
