package com.richardluo.globalIconPack.reflect

import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Field
import java.lang.reflect.Method

// Not cached
object ReflectHelper {
  fun findClass(className: String, lpp: LoadPackageParam? = null): Class<*>? {
    return runCatching { XposedHelpers.findClass(className, lpp?.classLoader) }.getOrNull()
  }

  fun findMethodFirstMatch(
    clazz: Class<*>,
    methodName: String,
    vararg parameterTypes: Class<*>?,
  ): Method? {
    return (runCatching { clazz.getDeclaredMethod(methodName, *parameterTypes) }.getOrNull()
        ?: run {
          clazz.declaredMethods.firstOrNull { method ->
            method.name == methodName &&
              method.parameterTypes.size >= parameterTypes.size &&
              method.parameterTypes.zip(parameterTypes).all { (param, expected) ->
                expected == null || param::class.java.isAssignableFrom(expected)
              }
          }
        })
      ?.apply { isAccessible = true }
  }

  fun findMethodFirstMatch(
    clazzName: String,
    lpp: LoadPackageParam? = null,
    methodName: String,
    vararg parameterTypes: Class<*>?,
  ): Method? =
    findClass(clazzName, lpp)?.let { findMethodFirstMatch(it, methodName, *parameterTypes) }

  fun callMethod(thisObj: Any, methodName: String, vararg args: Any) {
    findMethodFirstMatch(thisObj.javaClass, methodName, *mapToType(*args))?.invoke(thisObj, *args)
  }

  fun findField(clazz: Class<*>, name: String): Field? =
    clazz.getDeclaredField(name).apply { isAccessible = true }

  private fun mapToType(vararg args: Any?): Array<Class<*>> {
    return args.mapNotNull { arg -> arg?.javaClass }.toTypedArray()
  }
}
