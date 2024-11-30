package com.richardluo.globalIconPack.reflect

import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Method

object BaseIconFactory {
  lateinit var clazz: Class<*>
    private set

  var getNormalizer: Method? = null

  fun initWithLauncher3(lpp: LoadPackageParam) {
    clazz = ReflectHelper.findClassThrow("com.android.launcher3.icons.BaseIconFactory", lpp)
    getNormalizer =
      getNormalizer ?: clazz.let { ReflectHelper.findMethodFirstMatch(it, "getNormalizer") }
  }
}
