package com.richardluo.globalIconPack.xposed

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_DATE_CHANGED
import android.content.Intent.ACTION_TIMEZONE_CHANGED
import android.content.Intent.ACTION_TIME_CHANGED
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.PackageItemInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.richardluo.globalIconPack.iconPack.getSC
import com.richardluo.globalIconPack.iconPack.model.IconEntry
import com.richardluo.globalIconPack.iconPack.source.getComponentName
import com.richardluo.globalIconPack.utils.TryHookScope
import com.richardluo.globalIconPack.utils.allMethods
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.call
import com.richardluo.globalIconPack.utils.callOriginalMethod
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.constructor
import com.richardluo.globalIconPack.utils.field
import com.richardluo.globalIconPack.utils.getAs
import com.richardluo.globalIconPack.utils.hook
import com.richardluo.globalIconPack.utils.isHighByte
import com.richardluo.globalIconPack.utils.method
import com.richardluo.globalIconPack.utils.tryHook
import com.richardluo.globalIconPack.utils.withHighByte
import com.richardluo.globalIconPack.xposed.ReplaceIcon.Companion.IN_SC
import com.richardluo.globalIconPack.xposed.ReplaceIcon.Companion.SC_DEFAULT
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.util.Calendar

class CalendarAndClockHook(private val clockUseFallbackMask: Boolean) : Hook {
  private val calendars = mutableSetOf<String>()
  private val clocks = mutableSetOf<String>()

  override fun onHookPixelLauncher(lpp: LoadPackageParam) {
    tryHook("CalendarAndClockHook") {
      tryDo {
        val iconProvider =
          classOf("com.android.launcher3.icons.IconProvider", lpp) ?: return@tryDo fail()
        hookCollectCC(iconProvider)
        hookGetState(iconProvider)

        tryHook("hook calendar and clock change") {
            tryDo { hookCCChange16QPR2(lpp) }
            tryDo { hookCCChangePre16QPR2(lpp) }
          }
          .failOnFail()
      }
    }
  }

  fun TryHookScope<Unit>.hookCCChange16QPR2(lpp: LoadPackageParam) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) fail()

    val iconChangeTracker =
      classOf("com.android.launcher3.icons.IconChangeTracker", lpp) ?: return fail()
    val changesF = iconChangeTracker.field("_changes") ?: return fail()
    val userCacheF = iconChangeTracker.field("userCache") ?: return fail()

    val dispatchValueF =
      classOf("com.android.launcher3.util.MutableListenableStream", lpp)?.method("dispatchValue")
        ?: return fail()
    val packageUserKeyConstructor =
      classOf("com.android.launcher3.util.PackageUserKey")
        ?.constructor(String::class.java, UserHandle::class.java) ?: return fail()

    val getUserProfilesF =
      classOf("com.android.launcher3.pm.UserCache", lpp)?.method("getUserProfiles") ?: return fail()

    val iconChangeTrackerReceiver =
      classOf($$"com.android.launcher3.icons.IconChangeTracker$receiver$1", lpp) ?: return fail()
    val iconChangeTrackerF = iconChangeTrackerReceiver.field($$"this$0") ?: return fail()

    fun changeClockIcon(changes: Any) {
      for (clock in clocks) dispatchValueF.invoke(
        changes,
        packageUserKeyConstructor.newInstance(clock, Process.myUserHandle()),
      )
    }

    fun changeCalendarIcon(changes: Any, userCache: Any) {
      val userHandles = getUserProfilesF.call<List<UserHandle>>(userCache) ?: return
      for (user in userHandles) {
        for (calendar in calendars) dispatchValueF.invoke(
          changes,
          packageUserKeyConstructor.newInstance(calendar, user),
        )
      }
    }

    iconChangeTrackerReceiver
      .method("accept")
      ?.hook {
        before {
          val iconChangeTracker = iconChangeTrackerF.get(thisObject) ?: return@before
          val changes = changesF.get(iconChangeTracker) ?: return@before
          val userCache = userCacheF.get(iconChangeTracker) ?: return@before
          val intent = args[0] as? Intent ?: return@before
          when (intent.action) {
            ACTION_TIMEZONE_CHANGED -> {
              changeClockIcon(changes)
              changeCalendarIcon(changes, userCache)
              result = null
            }
            ACTION_DATE_CHANGED,
            ACTION_TIME_CHANGED -> {
              changeCalendarIcon(changes, userCache)
              result = null
            }
          }
        }
      }
      .registerToScopeOrFail()
  }

  fun TryHookScope<Unit>.hookCCChangePre16QPR2(lpp: LoadPackageParam) {
    val iconChangeReceiver =
      classOf($$"com.android.launcher3.icons.IconProvider$IconChangeReceiver", lpp) ?: return fail()
    val mCallbackF = iconChangeReceiver.field("mCallback") ?: return fail()

    val onAppIconChangedM =
      classOf($$"com.android.launcher3.icons.IconProvider$IconChangeListener", lpp)
        ?.method("onAppIconChanged") ?: return fail()

    fun changeClockIcon(mCallback: Any) {
      for (clock in clocks) onAppIconChangedM.invoke(mCallback, clock, Process.myUserHandle())
    }

    fun changeCalendarIcon(context: Context, mCallback: Any) {
      for (user in context.getSystemService(UserManager::class.java).getUserProfiles()) {
        for (calendar in calendars) onAppIconChangedM.invoke(mCallback, calendar, user)
      }
    }

    iconChangeReceiver
      .allMethods("onReceive")
      .hook {
        before {
          val context = args[0] as? Context ?: return@before
          val intent = args[1] as? Intent ?: return@before
          val mCallback = mCallbackF.get(thisObject) ?: return@before
          when (intent.action) {
            ACTION_TIMEZONE_CHANGED -> {
              changeClockIcon(mCallback)
              changeCalendarIcon(context, mCallback)
              result = null
            }
            ACTION_DATE_CHANGED,
            ACTION_TIME_CHANGED -> {
              changeCalendarIcon(context, mCallback)
              result = null
            }
          }
        }
      }
      .registerToScopeOrFail()
  }

  private fun TryHookScope<Unit>.hookCollectCC(iconProvider: Class<*>) {
    val mCalendar = iconProvider.field("mCalendar")
    val mClock = iconProvider.field("mClock")

    // Collect calendar and clock packages
    fun MethodHookParam.collectCCHook(pi: PackageItemInfo?, density: Int?): Drawable? {
      val pi = pi ?: return callOriginalMethod()
      val sc = getSC() ?: return callOriginalMethod()
      val packageName = pi.packageName
      val entry =
        if (pi.icon.isHighByte(IN_SC)) sc.getIconEntry(pi.icon.withHighByte(SC_DEFAULT)) else null

      if (entry == null) {
        // Not in icon pack, update the original package
        when (packageName) {
          mCalendar?.getAs<ComponentName>(thisObject)?.packageName -> calendars.add(packageName)
          mClock?.getAs<ComponentName>(thisObject)?.packageName -> clocks.add(packageName)
        }
        return callOriginalMethod()
      }

      when (entry.type) {
        IconEntry.Type.Calendar -> calendars.add(packageName)
        IconEntry.Type.Clock -> {
          clocks.add(packageName)
          // https://cs.android.com/android/platform/superproject/+/android-latest-release:frameworks/libs/systemui/iconloaderlib/src/com/android/launcher3/icons/ClockDrawableWrapper.kt;l=45?q=ClockDrawableWrapper&sq=
          // Run the ClockDrawableWrapper.forPackage() logic
          val oldClock = mClock?.getAs<ComponentName>(thisObject)
          return try {
            mClock?.set(thisObject, getComponentName(packageName))
            callOriginalMethod<Drawable>()?.let {
              if (clockUseFallbackMask) sc.maskIconFrom(it) else it
            }
          } finally {
            mClock?.set(thisObject, oldClock)
          }
        }
        else -> {}
      }

      return sc.getIcon(entry, density ?: 0)
    }

    tryHook("hookGetIcon") {
        tryDo {
          iconProvider
            .allMethods(
              "getIcon",
              PackageItemInfo::class.java,
              ApplicationInfo::class.java,
              Int::class.javaPrimitiveType,
            )
            .hook { replace { collectCCHook(args[0].asType(), args[2].asType()) } }
            .registerToScopeOrFail()
        }

        tryDo {
          iconProvider
            .allMethods("getIcon", ActivityInfo::class.java)
            .hook { replace { collectCCHook(args[0].asType(), args.getOrNull(1).asType()) } }
            .registerToScopeOrFail()
          iconProvider
            .allMethods("getIcon", LauncherActivityInfo::class.java)
            .hook {
              replace {
                val activityInfo = args[0].asType<LauncherActivityInfo>()
                val info =
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) activityInfo?.activityInfo
                  else activityInfo?.applicationInfo
                collectCCHook(info, args.getOrNull(1).asType())
              }
            }
            .registerToScopeOrFail()
        }
      }
      .failOnFail()
  }

  private fun TryHookScope<Unit>.hookGetState(iconProvider: Class<*>) {
    // Change calendar state so it updates
    tryHook("hookGetState") {
        tryDo {
          // https://cs.android.com/android/platform/superproject/+/android-15.0.0_r20:frameworks/libs/systemui/iconloaderlib/src/com/android/launcher3/icons/IconProvider.java;l=97
          // getStateForApp may return String (old) or PersistedItemState (new >= Android 16)
          // The original only adds day-of-month for mCalendar.getPackageName() (hardcoded
          // com.google.android.calendar). For icon-pack calendar icons mapped to other
          // packages, we must append the day offset ourselves.
          iconProvider
            .allMethods("getStateForApp", ApplicationInfo::class.java)
            .hook {
              after {
                val info = args[0].asType<ApplicationInfo>() ?: return@after
                if (!calendars.contains(info.packageName)) return@after
                val dayOffset = Calendar.getInstance().get(Calendar.DAY_OF_MONTH) - 1
                val originalResult = result ?: return@after
                result = when (originalResult) {
                  is String -> "$originalResult $dayOffset"
                  // PersistedItemState: call withAdditionalValues to append the day
                  else -> originalResult.javaClass.method(
                    "withAdditionalValues",
                    Array<String>::class.java
                  )?.invoke(originalResult, arrayOf(dayOffset.toString())) ?: originalResult
                }
              }
            }
            .registerToScopeOrFail()
        }

        tryDo {
          // https://cs.android.com/android/platform/superproject/+/android15-qpr1-release:frameworks/libs/systemui/iconloaderlib/src/com/android/launcher3/icons/IconProvider.java;l=89
          iconProvider
            .allMethods("getSystemStateForPackage", String::class.java, String::class.java)
            .hook {
              before {
                val systemState = args[0].asType<String>()
                result =
                  if (calendars.contains(args[1]))
                    systemState + (Calendar.getInstance().get(Calendar.DAY_OF_MONTH) - 1)
                  else systemState
              }
            }
            .registerToScopeOrFail()
        }
      }
      .failOnFail()
  }
}
