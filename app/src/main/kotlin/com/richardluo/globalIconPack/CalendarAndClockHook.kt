package com.richardluo.globalIconPack

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_DATE_CHANGED
import android.content.Intent.ACTION_TIMEZONE_CHANGED
import android.content.Intent.ACTION_TIME_CHANGED
import android.os.Process
import android.os.UserManager
import com.richardluo.globalIconPack.iconPack.IconType
import com.richardluo.globalIconPack.iconPack.getComponentName
import com.richardluo.globalIconPack.iconPack.getIp
import com.richardluo.globalIconPack.reflect.ReflectHelper
import com.richardluo.globalIconPack.utils.PrefKey
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.getAs
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class CalendarAndClockHook : Hook {
  override fun onHookPixelLauncher(lpp: LoadPackageParam) {
    if (!WorldPreference.getReadablePref().getBoolean(PrefKey.NO_FORCE_SHAPE, true)) return

    val calendars = mutableSetOf<String>()
    val clocks = mutableSetOf<String>()

    val iconProvider = ReflectHelper.findClassThrow("com.android.launcher3.icons.IconProvider", lpp)
    val mCalendar = ReflectHelper.findField(iconProvider, "mCalendar")
    val mClock = ReflectHelper.findField(iconProvider, "mClock")
    ReflectHelper.hookAllConstructors(
      iconProvider,
      object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
          mCalendar?.getAs<ComponentName>(param.thisObject)?.let { calendars.add(it.packageName) }
          mClock?.getAs<ComponentName>(param.thisObject)?.let { clocks.add(it.packageName) }
          mCalendar?.set(param.thisObject, null)
          mClock?.set(param.thisObject, null)
        }
      },
    )
    ReflectHelper.hookAllMethods(
      iconProvider,
      "getIconWithOverrides",
      arrayOf(String::class.java, Int::class.java),
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          val ip = getIp() ?: return
          val packageName = param.args[0] as String
          val iconDpi = param.args[1] as Int
          val entry = ip.getIconEntry(getComponentName(packageName)) ?: return
          when (entry.type) {
            IconType.Calendar -> {
              calendars.add(packageName)
              param.result = ip.getIcon(entry, iconDpi)
            }
            IconType.Clock -> {
              clocks.add(packageName)
              param.result = ip.getIcon(entry, iconDpi)
            }
            else -> return
          }
        }
      },
    )

    val iconChangeReceiver =
      ReflectHelper.findClassThrow(
        "com.android.launcher3.icons.IconProvider\$IconChangeReceiver",
        lpp,
      )
    val mCallbackField = iconChangeReceiver.let { ReflectHelper.findField(it, "mCallback") }
    ReflectHelper.hookAllMethods(
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
