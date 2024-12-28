package com.richardluo.globalIconPack.reflect

import com.richardluo.globalIconPack.utils.ReflectHelper
import com.richardluo.globalIconPack.utils.call
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Method

object BaseIconFactory {
  private lateinit var clazz: Class<*>

  private var getNormalizer: Method? = null

  fun getClazz(lpp: LoadPackageParam): Class<*> {
    if (!::clazz.isInitialized)
      clazz = ReflectHelper.findClassThrow("com.android.launcher3.icons.BaseIconFactory", lpp)
    return clazz
  }

  fun getNormalizer(lpp: LoadPackageParam, factory: Any?): Any? {
    if (getNormalizer == null)
      getNormalizer = getClazz(lpp).let { ReflectHelper.findMethodFirstMatch(it, "getNormalizer") }
    return getNormalizer?.call(factory)
  }
}
