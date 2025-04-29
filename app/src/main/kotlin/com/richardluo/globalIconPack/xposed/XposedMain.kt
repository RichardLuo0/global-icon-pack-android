package com.richardluo.globalIconPack.xposed

import com.richardluo.globalIconPack.BuildConfig
import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.utils.WorldPreference.getPrefInMod
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class XposedMain : IXposedHookLoadPackage {
  override fun handleLoadPackage(lpp: LoadPackageParam) {
    if (!lpp.isFirstApplication) return
    if (BuildConfig.APPLICATION_ID == lpp.packageName) {
      BypassReflectRestrictions.onHookSelf(lpp)
      return
    }

    if (lpp.packageName == "android") {
      BypassShortcutPermission.onHookSystem(lpp)
      return
    }

    val pref = getPrefInMod()
    val hookList =
      listOfNotNull(
        ReplaceIcon(pref.get(Pref.SHORTCUT), pref.get(Pref.FORCE_ACTIVITY_ICON_FOR_TASK)),
        NoForceShape(true),
        if (pref.get(Pref.NO_SHADOW)) NoShadow() else null,
        if (pref.get(Pref.FORCE_LOAD_CLOCK_AND_CALENDAR)) CalendarAndClockHook() else null,
      )

    hookList.forEach { it.onHookApp(lpp) }
    when (lpp.packageName) {
      pref.get(Pref.PIXEL_LAUNCHER_PACKAGE) -> hookList.forEach { it.onHookPixelLauncher(lpp) }
      "com.android.systemui" -> hookList.forEach { it.onHookSystemUI(lpp) }
      "com.android.settings" -> hookList.forEach { it.onHookSettings(lpp) }
    }
  }
}
