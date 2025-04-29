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
import com.richardluo.globalIconPack.iconPack.getSC
import com.richardluo.globalIconPack.iconPack.model.IconEntry
import com.richardluo.globalIconPack.iconPack.source.getComponentName
import com.richardluo.globalIconPack.utils.HookBuilder
import com.richardluo.globalIconPack.utils.allMethods
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.call
import com.richardluo.globalIconPack.utils.callOriginalMethod
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.field
import com.richardluo.globalIconPack.utils.getAs
import com.richardluo.globalIconPack.utils.hook
import com.richardluo.globalIconPack.utils.method
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.util.Calendar

class CalendarAndClockHook : Hook {

  override fun onHookPixelLauncher(lpp: LoadPackageParam) {
    val calendars = mutableSetOf<String>()
    val clocks = mutableSetOf<String>()

    val iconProvider = classOf("com.android.launcher3.icons.IconProvider", lpp) ?: return
    val mCalendar = iconProvider.field("mCalendar")
    val mClock = iconProvider.field("mClock")
    // Collect calendar and clock packages
    fun HookBuilder.collectCCHook(getComponentName: MethodHookParam.() -> ComponentName?) {
      replace {
        val sc = getSC() ?: return@replace callOriginalMethod()
        val cn = getComponentName() ?: return@replace callOriginalMethod()
        val packageName = cn.packageName
        val entry = sc.getIconEntry(cn)

        if (entry == null) {
          // Not in icon pack, update the original package
          when (packageName) {
            mCalendar?.getAs<ComponentName>(thisObject)?.packageName -> calendars.add(packageName)
            mClock?.getAs<ComponentName>(thisObject)?.packageName -> clocks.add(packageName)
          }
          return@replace callOriginalMethod<Drawable?>()?.let { sc.genIconFrom(it) }
        }

        val density =
          args.getOrNull(1) as? Int
            ?: AndroidAppHelper.currentApplication().resources.configuration.densityDpi
        when (entry.type) {
          IconEntry.Type.Calendar -> calendars.add(packageName)
          IconEntry.Type.Clock -> clocks.add(packageName)
          else -> {}
        }
        return@replace sc.getIcon(entry, density)
      }
    }
    iconProvider.allMethods("getIcon", ActivityInfo::class.java).hook {
      collectCCHook { args[0].asType<ActivityInfo>()?.let { info -> getComponentName(info) } }
    }
    iconProvider.allMethods("getIcon", LauncherActivityInfo::class.java).hook {
      collectCCHook { args[0].asType<LauncherActivityInfo>()?.componentName }
    }
    // Change calendar state so it updates
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

    val iconChangeReceiver =
      classOf("com.android.launcher3.icons.IconProvider\$IconChangeReceiver", lpp) ?: return
    val mCallbackF = iconChangeReceiver.field("mCallback") ?: return

    val onAppIconChangedM =
      classOf("com.android.launcher3.icons.IconProvider\$IconChangeListener", lpp)
        .method("onAppIconChanged") ?: return

    fun changeClockIcon(mCallback: Any) {
      for (clock in clocks) onAppIconChangedM.call<Unit>(mCallback, clock, Process.myUserHandle())
    }

    fun changeCalendarIcon(context: Context, mCallback: Any) {
      for (user in context.getSystemService(UserManager::class.java).getUserProfiles()) {
        for (calendar in calendars) onAppIconChangedM.call<Unit>(mCallback, calendar, user)
      }
    }

    iconChangeReceiver.allMethods("onReceive").hook {
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
          else -> return@before
        }
      }
    }
  }
}
