package com.richardluo.globalIconPack.reflect

import com.richardluo.globalIconPack.utils.ReflectHelper
import com.richardluo.globalIconPack.utils.call
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Method

object BaseIconFactory {
  private var clazz: Class<*>? = null

  private var getNormalizer: Method? = null

  fun getClazz(lpp: LoadPackageParam) =
    clazz
      ?: ReflectHelper.findClass("com.android.launcher3.icons.BaseIconFactory", lpp).also {
        clazz = it
      }

  fun getNormalizer(lpp: LoadPackageParam, factory: Any?): Any? {
    if (getNormalizer == null)
      getNormalizer = getClazz(lpp)?.let { ReflectHelper.findMethodFirstMatch(it, "getNormalizer") }
    return getNormalizer?.call(factory)
  }
}
