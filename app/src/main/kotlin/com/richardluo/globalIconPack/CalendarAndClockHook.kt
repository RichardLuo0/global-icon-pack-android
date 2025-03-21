package com.richardluo.globalIconPack

import android.app.AndroidAppHelper
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_DATE_CHANGED
import android.content.Intent.ACTION_TIMEZONE_CHANGED
import android.content.Intent.ACTION_TIME_CHANGED
import android.content.pm.ActivityInfo
import android.content.pm.LauncherActivityInfo
import android.os.Process
import android.os.UserManager
import com.richardluo.globalIconPack.ReplaceIcon.Companion.IN_IP
import com.richardluo.globalIconPack.ReplaceIcon.Companion.IP_DEFAULT
import com.richardluo.globalIconPack.iconPack.getIP
import com.richardluo.globalIconPack.utils.ReflectHelper
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.getAs
import com.richardluo.globalIconPack.utils.isHighTwoByte
import com.richardluo.globalIconPack.utils.withHighByteSet
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.util.Calendar

class CalendarAndClockHook : Hook {

  override fun onHookPixelLauncher(lpp: LoadPackageParam) {
    val calendars = mutableSetOf<String>()
    val clocks = mutableSetOf<String>()

    var oriCalendar = ""
    var oriClock = ""

    val iconProvider =
      ReflectHelper.findClass("com.android.launcher3.icons.IconProvider", lpp) ?: return
    val mCalendar = ReflectHelper.findField(iconProvider, "mCalendar")
    val mClock = ReflectHelper.findField(iconProvider, "mClock")
    ReflectHelper.hookAllConstructors(
      iconProvider,
      object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
          mCalendar?.getAs<ComponentName>(param.thisObject)?.let { oriCalendar = it.packageName }
          mClock?.getAs<ComponentName>(param.thisObject)?.let { oriClock = it.packageName }
        }
      },
    )
    // Collect calendar and clock packages
    class CollectCC(val getActivityInfo: (MethodHookParam) -> ActivityInfo) : XC_MethodHook() {
      override fun beforeHookedMethod(param: MethodHookParam) {
        val ip = getIP() ?: return
        val ai = getActivityInfo(param)
        val resId = ai.iconResource
        val packageName = ai.packageName
        if (!isHighTwoByte(resId, IN_IP)) return
        val entry = ip.getIconEntry(withHighByteSet(resId, IP_DEFAULT))
        if (entry == null) {
          // Not in icon pack, update the original icon
          when (packageName) {
            oriCalendar -> calendars.add(packageName)
            oriClock -> clocks.add(packageName)
          }
          return
        }
        val density =
          param.args.getOrNull(1) as Int?
            ?: AndroidAppHelper.currentApplication().resources.configuration.densityDpi
        when {
          entry.isCalendar() -> calendars.add(packageName)
          entry.isClock() -> clocks.add(ai.packageName)
        }
        param.result = ip.getIcon(entry, density)
      }
    }
    ReflectHelper.hookAllMethodsOrLog(
      iconProvider,
      "getIcon",
      arrayOf(ActivityInfo::class.java),
      CollectCC { it.args[0] as ActivityInfo },
    )
    ReflectHelper.hookAllMethodsOrLog(
      iconProvider,
      "getIcon",
      arrayOf(LauncherActivityInfo::class.java),
      CollectCC { it.args[0].asType<LauncherActivityInfo>().activityInfo },
    )
    // Change calendar state so it updates
    // https://cs.android.com/android/platform/superproject/+/android15-qpr1-release:frameworks/libs/systemui/iconloaderlib/src/com/android/launcher3/icons/IconProvider.java;l=89
    ReflectHelper.hookAllMethodsOrLog(
      iconProvider,
      "getSystemStateForPackage",
      arrayOf(String::class.java, String::class.java),
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          val systemState = param.args[0].asType<String>()
          param.result =
            if (calendars.contains(param.args[1]))
              systemState + (Calendar.getInstance().get(Calendar.DAY_OF_MONTH) - 1)
            else systemState
        }
      },
    )

    val iconChangeReceiver =
      ReflectHelper.findClass("com.android.launcher3.icons.IconProvider\$IconChangeReceiver", lpp)
        ?: return
    val mCallbackField = ReflectHelper.findField(iconChangeReceiver, "mCallback")
    ReflectHelper.hookAllMethodsOrLog(
      iconChangeReceiver,
      "onReceive",
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          val context = param.args[0] as Context
          val intent = param.args[1] as Intent
          val mCallback = mCallbackField?.get(param.thisObject) ?: return
          when (intent.action) {
            ACTION_TIMEZONE_CHANGED -> {
              changeClockIcon(mCallback)
              changeCalendarIcon(context, mCallback)
              param.result = null
            }
            ACTION_DATE_CHANGED,
            ACTION_TIME_CHANGED -> {
              changeCalendarIcon(context, mCallback)
              param.result = null
            }
            else -> return
          }
        }

        fun changeClockIcon(mCallback: Any) {
          for (clock in clocks) {
            ReflectHelper.callMethod(mCallback, "onAppIconChanged", clock, Process.myUserHandle())
          }
        }

        fun changeCalendarIcon(context: Context, mCallback: Any) {
          for (user in context.getSystemService(UserManager::class.java).getUserProfiles()) {
            for (calendar in calendars) {
              ReflectHelper.callMethod(mCallback, "onAppIconChanged", calendar, user)
            }
          }
        }
      },
    )
  }
}
