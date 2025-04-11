package com.richardluo.globalIconPack.xposed

import android.app.AndroidAppHelper
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_DATE_CHANGED
import android.content.Intent.ACTION_TIMEZONE_CHANGED
import android.content.Intent.ACTION_TIME_CHANGED
import android.content.pm.ActivityInfo
import android.content.pm.LauncherActivityInfo
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserManager
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.iconPack.getComponentName
import com.richardluo.globalIconPack.iconPack.getIP
import com.richardluo.globalIconPack.utils.MethodReplacement
import com.richardluo.globalIconPack.utils.ReflectHelper
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.call
import com.richardluo.globalIconPack.utils.callOriginalMethod
import com.richardluo.globalIconPack.utils.getAs
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.util.Calendar

class CalendarAndClockHook : Hook {

  override fun onHookPixelLauncher(lpp: LoadPackageParam) {
    val calendars = mutableSetOf<String>()
    val clocks = mutableSetOf<String>()

    val iconProvider =
      ReflectHelper.findClass("com.android.launcher3.icons.IconProvider", lpp) ?: return
    val mCalendar = ReflectHelper.findField(iconProvider, "mCalendar")
    val mClock = ReflectHelper.findField(iconProvider, "mClock")
    // Collect calendar and clock packages
    class CollectCC(val getActivityInfo: (MethodHookParam) -> ActivityInfo?) : MethodReplacement() {
      override fun replaceHookedMethod(param: MethodHookParam): Drawable? {
        val ip = getIP() ?: return param.callOriginalMethod()
        val ai = getActivityInfo(param) ?: return param.callOriginalMethod()
        val packageName = ai.packageName
        val entry = ip.getIconEntry(getComponentName(ai))

        if (entry == null) {
          // Not in icon pack, update the original package
          when (packageName) {
            mCalendar?.getAs<ComponentName>(param.thisObject)?.packageName ->
              calendars.add(packageName)
            mClock?.getAs<ComponentName>(param.thisObject)?.packageName -> clocks.add(packageName)
          }
          return param.callOriginalMethod<Drawable?>()?.let { ip.genIconFrom(it) }
        }

        val density =
          param.args.getOrNull(1) as? Int
            ?: AndroidAppHelper.currentApplication().resources.configuration.densityDpi
        when (entry.type) {
          IconEntry.Type.Calendar -> calendars.add(packageName)
          IconEntry.Type.Clock -> clocks.add(packageName)
          else -> {}
        }
        return ip.getIcon(entry, density)
      }
    }
    ReflectHelper.hookAllMethodsOrLog(
      iconProvider,
      "getIcon",
      arrayOf(ActivityInfo::class.java),
      CollectCC { it.args[0] as? ActivityInfo },
    )
    ReflectHelper.hookAllMethodsOrLog(
      iconProvider,
      "getIcon",
      arrayOf(LauncherActivityInfo::class.java),
      CollectCC { it.args[0].asType<LauncherActivityInfo>()?.activityInfo },
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
    val mCallbackF = ReflectHelper.findField(iconChangeReceiver, "mCallback") ?: return

    val onAppIconChangedM =
      ReflectHelper.findClass("com.android.launcher3.icons.IconProvider\$IconChangeListener", lpp)
        ?.let { ReflectHelper.findMethodFirstMatch(it, "onAppIconChanged") } ?: return

    ReflectHelper.hookAllMethodsOrLog(
      iconChangeReceiver,
      "onReceive",
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          val context = param.args[0] as? Context ?: return
          val intent = param.args[1] as? Intent ?: return
          val mCallback = mCallbackF.get(param.thisObject) ?: return
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
          for (clock in clocks) onAppIconChangedM.call<Unit>(
            mCallback,
            clock,
            Process.myUserHandle(),
          )
        }

        fun changeCalendarIcon(context: Context, mCallback: Any) {
          for (user in context.getSystemService(UserManager::class.java).getUserProfiles()) {
            for (calendar in calendars) onAppIconChangedM.call<Unit>(mCallback, calendar, user)
          }
        }
      },
    )
  }
}
