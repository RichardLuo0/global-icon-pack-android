package com.richardluo.globalIconPack.reflect

import com.richardluo.globalIconPack.utils.call
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.method
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Method

object BaseIconFactory {
  private var clazz: Class<*>? = null
  private var iconOptionsClazz: Class<*>? = null

  private var getNormalizer: Method? = null

  fun getClass(lpp: LoadPackageParam) =
    clazz ?: classOf("com.android.launcher3.icons.BaseIconFactory", lpp).also { clazz = it }

  fun getIconOptionsClass(lpp: LoadPackageParam) =
    iconOptionsClazz
      ?: classOf($$"com.android.launcher3.icons.BaseIconFactory$IconOptions", lpp).also {
        iconOptionsClazz = it
      }

  fun getNormalizer(lpp: LoadPackageParam, factory: Any?): Any? {
    if (getNormalizer == null) getNormalizer = getClass(lpp)?.method("getNormalizer")
    return getNormalizer?.call(factory)
  }
}
