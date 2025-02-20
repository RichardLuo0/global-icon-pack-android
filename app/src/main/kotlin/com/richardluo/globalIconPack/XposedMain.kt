package com.richardluo.globalIconPack

import com.richardluo.globalIconPack.utils.WorldPreference
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class XposedMain : IXposedHookLoadPackage {
  private val hookList = arrayOf(ReplaceIcon(), NoForceShape(), NoShadow(), CalendarAndClockHook())

  override fun handleLoadPackage(lpp: LoadPackageParam) {
    if (!lpp.isFirstApplication) return
    if (BuildConfig.APPLICATION_ID == lpp.packageName) return

    if (lpp.packageName == "android") {
      BypassShortcutPermission.onHookAndroid(lpp)
      return
    }

    hookList.forEach { it.onHookApp(lpp) }

    when (lpp.packageName) {
      WorldPreference.getPrefInMod().get(Pref.PIXEL_LAUNCHER_PACKAGE) ->
        hookList.forEach { it.onHookPixelLauncher(lpp) }
      "com.android.systemui" -> hookList.forEach { it.onHookSystemUI(lpp) }
      "com.android.settings" -> hookList.forEach { it.onHookSettings(lpp) }
    }
  }
}
