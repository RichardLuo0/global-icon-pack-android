package com.richardluo.globalIconPack.xposed

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface

interface Hook {
  context(xposed: XposedInterface)
  fun onHookApp(param: XposedModuleInterface.PackageReadyParam) {}

  context(xposed: XposedInterface)
  fun onHookPixelLauncher(param: XposedModuleInterface.PackageReadyParam) {}

  context(xposed: XposedInterface)
  fun onHookSystemUI(param: XposedModuleInterface.PackageReadyParam) {}

  context(xposed: XposedInterface)
  fun onHookSettings(param: XposedModuleInterface.PackageReadyParam) {}
}
