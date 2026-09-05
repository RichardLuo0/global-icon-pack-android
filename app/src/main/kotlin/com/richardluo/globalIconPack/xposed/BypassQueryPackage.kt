package com.richardluo.globalIconPack.xposed

import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.allMethods
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.hookCompat
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface

object BypassQueryPackage {
  context(xposed: XposedInterface)
  fun onHookSystem(param: XposedModuleInterface.PackageReadyParam) {
    val getPackageNameM =
      classOf("com.android.server.pm.pkg.PackageState", param)?.getMethod("getPackageName")
        ?: return
    classOf("com.android.server.pm.AppsFilterBase", param)
      ?.allMethods("shouldFilterApplication")
      ?.hookCompat {
        after {
          if (result == false) return@after
          val targetPkgSetting = args[3] ?: return@after
          if (getPackageNameM.invoke(targetPkgSetting) == WorldPreference.get().get(Pref.ICON_PACK))
            result = false
        }
      }
  }
}
