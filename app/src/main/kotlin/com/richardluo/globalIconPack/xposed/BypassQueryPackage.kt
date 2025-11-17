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
    classOf("com.android.server.pm.ComputerEngine", lpp)?.allMethods("canQueryPackage")?.hook {
      before { if (args[1] == WorldPreference.get().get(Pref.ICON_PACK)) result = true }
    }
  }
}
