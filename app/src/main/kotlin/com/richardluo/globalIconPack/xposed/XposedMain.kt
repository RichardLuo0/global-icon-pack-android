package com.richardluo.globalIconPack.xposed

import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.utils.Logger
import com.richardluo.globalIconPack.utils.WorldPreference
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.ref.WeakReference

class XposedMain : XposedModule() {
  override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
    if (!param.isFirstPackage) return
    Logger.xposed = WeakReference(this)

    if (param.packageName == "android") {
      BypassShortcutPermission.onHookSystem(param)
      BypassQueryPackage.onHookSystem(param)
      BypassCrossUserPermission.onHookSystem(param)
      return
    }

    val pref = WorldPreference.get()
    val hookList =
      listOfNotNull(
        ReplaceIcon(
          pref.get(Pref.SHORTCUT),
          pref.get(Pref.FORCE_ACTIVITY_ICON_FOR_TASK),
          pref.get(Pref.NON_ADAPTIVE_SCALE),
          pref.get(Pref.FORCE_MONOCHROME),
        ),
        NoForceShape(true),
        if (pref.get(Pref.NO_SHADOW)) NoShadow() else null,
        if (pref.get(Pref.FORCE_LOAD_CLOCK_AND_CALENDAR))
          CalendarAndClockHook(pref.get(Pref.CLOCK_USE_FALLBACK_MASK))
        else null,
      )
    hookList.forEach { it.onHookApp(param) }
    when (param.packageName) {
      "com.android.launcher3",
      Pref.PIXEL_LAUNCHER_PACKAGE.def,
      pref.get(Pref.PIXEL_LAUNCHER_PACKAGE) -> hookList.forEach { it.onHookPixelLauncher(param) }
      "com.android.systemui" -> hookList.forEach { with(it) { it.onHookSystemUI(param) } }
      "com.android.settings" -> hookList.forEach { with(it) { it.onHookSettings(param) } }
    }
  }
}
