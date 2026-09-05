package com.richardluo.globalIconPack.reflect

import com.richardluo.globalIconPack.utils.call
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.method
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

object BaseIconFactory {
  private var clazz: Class<*>? = null
  private var iconOptionsClazz: Class<*>? = null

  private var getNormalizer: Method? = null

  fun getClass(param: XposedModuleInterface.PackageReadyParam) =
    clazz ?: classOf("com.android.launcher3.icons.BaseIconFactory", param).also { clazz = it }

  fun getIconOptionsClass(param: XposedModuleInterface.PackageReadyParam) =
    iconOptionsClazz
      ?: classOf($$"com.android.launcher3.icons.BaseIconFactory$IconOptions", param).also {
        iconOptionsClazz = it
      }

  fun getNormalizer(param: XposedModuleInterface.PackageReadyParam, factory: Any?): Any? {
    if (getNormalizer == null) getNormalizer = getClass(param)?.method("getNormalizer")
    return getNormalizer?.call(factory)
  }
}
