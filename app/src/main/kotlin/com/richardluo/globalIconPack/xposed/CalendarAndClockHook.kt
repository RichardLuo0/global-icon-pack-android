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
import android.os.UserManager
import com.richardluo.globalIconPack.iconPack.getSC
import com.richardluo.globalIconPack.iconPack.model.IconEntry
import com.richardluo.globalIconPack.utils.allMethods
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.callOriginalMethod
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.field
import com.richardluo.globalIconPack.utils.getAs
import com.richardluo.globalIconPack.utils.hook
import com.richardluo.globalIconPack.utils.isHighTwoByte
import com.richardluo.globalIconPack.utils.method
import com.richardluo.globalIconPack.utils.withHighByteSet
import com.richardluo.globalIconPack.xposed.ReplaceIcon.Companion.IN_SC
import com.richardluo.globalIconPack.xposed.ReplaceIcon.Companion.SC_DEFAULT
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.util.Calendar

class CalendarAndClockHook(private val clockUseFallbackMask: Boolean) : Hook {
  override fun onHookPixelLauncher(lpp: LoadPackageParam) {
    val calendars = mutableSetOf<String>()
    val clocks = mutableSetOf<String>()

    val iconProvider = classOf("com.android.launcher3.icons.IconProvider", lpp) ?: return
    val mCalendar = iconProvider.field("mCalendar")
    val mClock = iconProvider.field("mClock")
    // Collect calendar and clock packages
    fun MethodHookParam.collectCCHook(pi: PackageItemInfo?, density: Int?): Drawable? {
      val pi = pi ?: return callOriginalMethod()
      val sc = getSC() ?: return callOriginalMethod()
      val packageName = pi.packageName
      val entry =
        if (isHighTwoByte(pi.icon, IN_SC)) sc.getIconEntry(withHighByteSet(pi.icon, SC_DEFAULT))
        else null

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
        IconEntry.Type.Clock -> clocks.add(packageName)
        else -> {}
      }

      return sc.getIcon(entry, density ?: 0).let {
        if (clockUseFallbackMask && it != null && entry.type == IconEntry.Type.Clock)
          sc.maskIconFrom(it)
        else it
      }
    }

    val getIconHook =
      iconProvider
        .allMethods(
          "getIcon",
          PackageItemInfo::class.java,
          ApplicationInfo::class.java,
          Int::class.javaPrimitiveType,
        )
        .hook { replace { collectCCHook(args[0].asType(), args[2].asType()) } }
    if (getIconHook.isNullOrEmpty()) {
      iconProvider.allMethods("getIcon", ActivityInfo::class.java).hook {
        replace { collectCCHook(args[0].asType(), args.getOrNull(1).asType()) }
      }
      iconProvider.allMethods("getIcon", LauncherActivityInfo::class.java).hook {
        replace {
          val activityInfo = args[0].asType<LauncherActivityInfo>()
          val info =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) activityInfo?.activityInfo
            else activityInfo?.applicationInfo
          collectCCHook(info, args.getOrNull(1).asType())
        }
      }
    }

    // Change calendar state so it updates
    // https://cs.android.com/android/platform/superproject/+/android-15.0.0_r20:frameworks/libs/systemui/iconloaderlib/src/com/android/launcher3/icons/IconProvider.java;l=97
    val getStateForAppHook =
      iconProvider.allMethods("getStateForApp", ApplicationInfo::class.java).hook {
        replace {
          val info = args[0].asType<ApplicationInfo>() ?: return@replace callOriginalMethod()
          return@replace if (calendars.contains(info.packageName))
            (callOriginalMethod<String>() +
              " " +
              (Calendar.getInstance().get(Calendar.DAY_OF_MONTH) - 1))
          else if (mCalendar?.getAs<ComponentName>(thisObject)?.packageName == info.packageName) ""
          else callOriginalMethod()
        }
      }
    if (getStateForAppHook.isNullOrEmpty()) {
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
    }

    val iconChangeReceiver =
      classOf("com.android.launcher3.icons.IconProvider\$IconChangeReceiver", lpp) ?: return
    val mCallbackF = iconChangeReceiver.field("mCallback") ?: return

    val onAppIconChangedM =
      classOf("com.android.launcher3.icons.IconProvider\$IconChangeListener", lpp)
        ?.method("onAppIconChanged") ?: return

    fun changeClockIcon(mCallback: Any) {
      for (clock in clocks) onAppIconChangedM.invoke(mCallback, clock, Process.myUserHandle())
    }

    fun changeCalendarIcon(context: Context, mCallback: Any) {
      for (user in context.getSystemService(UserManager::class.java).getUserProfiles()) {
        for (calendar in calendars) onAppIconChangedM.invoke(mCallback, calendar, user)
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
