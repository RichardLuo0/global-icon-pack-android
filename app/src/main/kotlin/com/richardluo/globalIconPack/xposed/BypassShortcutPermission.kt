package com.richardluo.globalIconPack.xposed

import com.richardluo.globalIconPack.BuildConfig
import com.richardluo.globalIconPack.utils.allMethods
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.hook
import com.richardluo.globalIconPack.utils.rGet
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

object BypassShortcutPermission {
  fun onHookSystem(lpp: LoadPackageParam) {
    classOf("com.android.server.pm.LauncherAppsService\$LauncherAppsImpl", lpp)
      ?.allMethods("ensureShortcutPermission")
      ?.hook { before { if (BuildConfig.APPLICATION_ID == args.rGet(-1)) result = null } }
    classOf("com.android.server.pm.ShortcutService", lpp)
      ?.allMethods("canSeeAnyPinnedShortcut")
      ?.hook { before { if (BuildConfig.APPLICATION_ID == args.getOrNull(0)) result = true } }
  }
}
