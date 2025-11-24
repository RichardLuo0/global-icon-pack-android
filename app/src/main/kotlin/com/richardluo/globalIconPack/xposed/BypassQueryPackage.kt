package com.richardluo.globalIconPack.xposed

import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.allMethods
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.hook
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

object BypassQueryPackage {
  fun onHookSystem(lpp: LoadPackageParam) {
    val getPackageNameM =
      classOf("com.android.server.pm.pkg.PackageState", lpp)?.getMethod("getPackageName") ?: return
    classOf("com.android.server.pm.AppsFilterBase", lpp)
      ?.allMethods("shouldFilterApplication")
      ?.hook {
        after {
          if (result == false) return@after
          val targetPkgSetting = args[3] ?: return@after
          if (getPackageNameM.invoke(targetPkgSetting) == WorldPreference.get().get(Pref.ICON_PACK))
            result = false
        }
      }
  }
}
