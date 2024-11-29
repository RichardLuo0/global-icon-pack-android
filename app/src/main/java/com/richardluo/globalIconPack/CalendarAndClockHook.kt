package com.richardluo.globalIconPack

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_DATE_CHANGED
import android.content.Intent.ACTION_TIMEZONE_CHANGED
import android.content.Intent.ACTION_TIME_CHANGED
import android.os.Process
import android.os.UserManager
import com.richardluo.globalIconPack.reflect.ReflectHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class CalendarAndClockHook : Hook {
  override fun onHookPixelLauncher(lpp: LoadPackageParam) {
    val calendars = mutableSetOf<String>()
    val clocks = mutableSetOf<String>()

    val forceClockAndCalendarFromIconPack =
      WorldPreference.getReadablePref().getBoolean("forceLoadClockAndCalendar", true)

    val iconProvider = ReflectHelper.findClass("com.android.launcher3.icons.IconProvider", lpp)
    val mCalendar = iconProvider?.let { ReflectHelper.findField(it, "mCalendar") }
    val mClock = iconProvider?.let { ReflectHelper.findField(it, "mClock") }
    XposedBridge.hookAllMethods(
      iconProvider,
      "getIconWithOverrides",
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          if (forceClockAndCalendarFromIconPack) {
            mCalendar?.set(param.thisObject, null)
            mClock?.set(param.thisObject, null)
          }

          val cip = getCip() ?: return
          val packageName = param.args[0] as String
          val density = param.args[1] as Int
          val entry = cip.getIconEntry(getComponentName(packageName)) ?: return
          when (entry.type) {
            IconType.Calendar -> {
              calendars.add(packageName)
              param.result = cip.getIcon(entry, density)
            }
            IconType.Clock -> {
              clocks.add(packageName)
              param.result = cip.getIcon(entry, density)
            }
            else -> return
          }
        }
      },
    )

    val iconChangeReceiver =
      ReflectHelper.findClass("com.android.launcher3.icons.IconProvider\$IconChangeReceiver", lpp)
    val mCallbackField = iconChangeReceiver?.let { ReflectHelper.findField(it, "mCallback") }
    XposedBridge.hookAllMethods(
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
