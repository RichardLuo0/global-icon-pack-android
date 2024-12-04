package com.richardluo.globalIconPack

import de.robv.android.xposed.IXposedHookZygoteInit.StartupParam
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

interface Hook {
  fun onInitZygote(sp: StartupParam) {}

  fun onHookApp(lpp: LoadPackageParam) {}

  fun onHookPixelLauncher(lpp: LoadPackageParam) {}

  fun onHookSystemUI(lpp: LoadPackageParam) {}

  fun onHookSettings(lpp: LoadPackageParam) {}
}
