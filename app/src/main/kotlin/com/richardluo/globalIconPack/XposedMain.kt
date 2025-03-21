package com.richardluo.globalIconPack

import com.richardluo.globalIconPack.utils.WorldPreference.getPrefInMod
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class XposedMain : IXposedHookLoadPackage {
  private val hookList = run {
    val pref = getPrefInMod()
    listOfNotNull(
      ReplaceIcon(),
      if (pref.get(Pref.NO_FORCE_SHAPE)) NoForceShape() else null,
      if (pref.get(Pref.NO_SHADOW)) NoShadow() else null,
      if (pref.get(Pref.FORCE_LOAD_CLOCK_AND_CALENDAR)) CalendarAndClockHook() else null,
    )
  }

  override fun handleLoadPackage(lpp: LoadPackageParam) {
    if (!lpp.isFirstApplication) return
    if (BuildConfig.APPLICATION_ID == lpp.packageName) return

    if (lpp.packageName == "android") {
      BypassShortcutPermission.onHookAndroid(lpp)
      return
    }

    hookList.forEach { it.onHookApp(lpp) }

    when (lpp.packageName) {
      getPrefInMod().get(Pref.PIXEL_LAUNCHER_PACKAGE) ->
        hookList.forEach { it.onHookPixelLauncher(lpp) }
      "com.android.systemui" -> hookList.forEach { it.onHookSystemUI(lpp) }
      "com.android.settings" -> hookList.forEach { it.onHookSettings(lpp) }
    }
  }
}
