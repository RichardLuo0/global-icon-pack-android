package com.richardluo.globalIconPack

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class XposedMain : IXposedHookLoadPackage, IXposedHookZygoteInit {
  private val hookList = arrayOf(ReplaceIcon(), NoForceShape(), CalendarAndClockHook())

  override fun initZygote(sp: IXposedHookZygoteInit.StartupParam) {
    if (!sp.startsSystemServer) hookList.forEach { it.onInitZygote(sp) }
  }

  override fun handleLoadPackage(lpp: LoadPackageParam) {
    if (!lpp.isFirstApplication) return
    if (BuildConfig.APPLICATION_ID == lpp.packageName) return

    hookList.forEach { it.onHookApp(lpp) }

    when (lpp.packageName) {
      "com.google.android.apps.nexuslauncher" -> hookList.forEach { it.onHookPixelLauncher(lpp) }
      "com.android.systemui" -> hookList.forEach { it.onHookSystemUI(lpp) }
      "com.android.settings" -> hookList.forEach { it.onHookSettings(lpp) }
    }
  }
}
