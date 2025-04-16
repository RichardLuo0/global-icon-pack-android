package com.richardluo.globalIconPack.xposed

import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.lsposed.hiddenapibypass.HiddenApiBypass

object BypassReflectRestrictions {
  fun onHookSelf(@Suppress("UNUSED") lpp: LoadPackageParam) {
    HiddenApiBypass.addHiddenApiExemptions("")
  }
}
