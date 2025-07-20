package com.richardluo.globalIconPack.xposed

import android.os.Build
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.lsposed.hiddenapibypass.HiddenApiBypass

object BypassReflectRestrictions {
  fun onHookSelf(@Suppress("UNUSED") lpp: LoadPackageParam) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      HiddenApiBypass.addHiddenApiExemptions("")
    }
  }
}
