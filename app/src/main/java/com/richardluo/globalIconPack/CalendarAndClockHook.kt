package com.richardluo.globalIconPack

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_DATE_CHANGED
import android.content.Intent.ACTION_TIMEZONE_CHANGED
import android.content.Intent.ACTION_TIME_CHANGED
import android.os.Process
import android.os.UserManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class CalendarAndClockHook : Hook {
  override fun onHookPixelLauncher(lpp: LoadPackageParam) {
    val calendars = mutableSetOf<String>()
    val clocks = mutableSetOf<String>()

    val forceClockAndCalendarFromIconPack =
      WorldPreference.getReadablePref().getBoolean("forceClockAndCalendarFromIconPack", true)

    val iconProvider =
      XposedHelpers.findClass("com.android.launcher3.icons.IconProvider", lpp.classLoader)
    val mCalendar = XposedHelpers.findField(iconProvider, "mCalendar")
    val mClock = XposedHelpers.findField(iconProvider, "mClock")
    XposedBridge.hookAllMethods(
      iconProvider,
      "getIconWithOverrides",
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          if (forceClockAndCalendarFromIconPack) {
            mCalendar[param.thisObject] = null
            mClock[param.thisObject] = null
          }

          val cip = getCip() ?: return
          val packageName = param.args[0] as String
          val density = param.args[1] as Int
          val cn = getComponentName(packageName)
          val entry = cip.getIconEntry(cn) ?: return
          when (entry.type) {
            IconType.Calendar -> {
              calendars.add(packageName)
              param.result = entry.getIcon(cip, density)
            }
            IconType.Clock -> {
              clocks.add(packageName)
              param.result = entry.getIcon(cip, density)
            }
            else -> return
          }
        }
      },
    )

    val iconChangeReceiver =
      XposedHelpers.findClass(
        "com.android.launcher3.icons.IconProvider\$IconChangeReceiver",
        lpp.classLoader,
      )
    val mCallbackField = XposedHelpers.findField(iconChangeReceiver, "mCallback")
    XposedBridge.hookAllMethods(
      iconChangeReceiver,
      "onReceive",
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          val context = param.args[0] as Context
          val intent = param.args[1] as Intent
          val mCallback = mCallbackField.get(param.thisObject) ?: return
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
            XposedHelpers.callMethod(mCallback, "onAppIconChanged", clock, Process.myUserHandle())
          }
        }

        fun changeCalendarIcon(context: Context, mCallback: Any) {
          for (user in context.getSystemService(UserManager::class.java).getUserProfiles()) {
            for (calendar in calendars) {
              XposedHelpers.callMethod(mCallback, "onAppIconChanged", calendar, user)
            }
          }
        }
      },
    )
  }
}
