package com.richardluo.globalIconPack.xposed

import com.richardluo.globalIconPack.BuildConfig
import com.richardluo.globalIconPack.utils.allMethods
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.hook
import com.richardluo.globalIconPack.utils.rGet
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface

object BypassShortcutPermission {
  context(xposed: XposedInterface)
  fun onHookSystem(param: XposedModuleInterface.PackageReadyParam) {
    classOf($$"com.android.server.pm.LauncherAppsService$LauncherAppsImpl", param)
      ?.allMethods("ensureShortcutPermission")
      ?.hook {
        if (BuildConfig.APPLICATION_ID == args.rGet(-1)) return@hook null
        return@hook proceed()
      }
    classOf("com.android.server.pm.ShortcutService", param)
      ?.allMethods("canSeeAnyPinnedShortcut")
      ?.hook {
        if (BuildConfig.APPLICATION_ID == args.getOrNull(0)) return@hook true
        return@hook proceed()
      }
  }
}
