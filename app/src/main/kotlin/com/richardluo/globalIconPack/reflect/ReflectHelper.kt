package com.richardluo.globalIconPack.reflect

import com.richardluo.globalIconPack.call
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Field
import java.lang.reflect.Method

// Not cached
object ReflectHelper {
  fun findClass(className: String, lpp: LoadPackageParam? = null): Class<*>? {
    return runCatching { XposedHelpers.findClass(className, lpp?.classLoader) }
      .getOrElse {
        XposedBridge.log(it)
        null
      }
  }

  fun findClassThrow(className: String, lpp: LoadPackageParam? = null): Class<*> {
    return XposedHelpers.findClass(className, lpp?.classLoader)
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
      ?: run {
        XposedBridge.log("No method $methodName is found on class ${clazz.name}")
        null
      }
  }

  fun findMethodFirstMatch(
    clazzName: String,
    lpp: LoadPackageParam? = null,
    methodName: String,
    vararg parameterTypes: Class<*>?,
  ): Method? =
    findClass(clazzName, lpp)?.let { findMethodFirstMatch(it, methodName, *parameterTypes) }

  fun hookAllMethods(
    clazz: Class<*>,
    methodName: String,
    hook: XC_MethodHook,
  ): MutableSet<XC_MethodHook.Unhook>? {
    return XposedBridge.hookAllMethods(clazz, methodName, hook).also {
      if (it.size <= 0) XposedBridge.log("No methods $methodName are found on class ${clazz.name}")
    }
  }

  fun hookAllMethods(
    clazz: Class<*>,
    hookMap: Map<String, XC_MethodHook>,
  ): MutableSet<XC_MethodHook.Unhook> {
    val unhooks = mutableSetOf<XC_MethodHook.Unhook>()
    val notHooked = hookMap.keys.toMutableSet()
    for (method in clazz.getDeclaredMethods()) hookMap[method.name]?.let {
      unhooks.add(XposedBridge.hookMethod(method, it))
      notHooked.remove(method.name)
    }
    for (methodName in notHooked) XposedBridge.log(
      "No methods $methodName are found on class ${clazz.name}"
    )
    return unhooks
  }

  fun hookAllConstructors(clazz: Class<*>, hook: XC_MethodHook) =
    XposedBridge.hookAllConstructors(clazz, hook).also {
      if (it.size <= 0) XposedBridge.log("No constructors are found on class ${clazz.name}")
    }

  fun hookMethod(method: Method, hook: XC_MethodHook) = XposedBridge.hookMethod(method, hook)

  // This should be cached, thus call xposed helper version
  fun <T> callMethod(thisObj: Any, methodName: String, vararg args: Any): T? {
    return runCatching {
        XposedHelpers.findMethodBestMatch(thisObj.javaClass, methodName, *args)
          .call<T>(thisObj, *args)
      }
      .getOrElse {
        XposedBridge.log(it)
        null
      }
  }

  fun callMethod(thisObj: Any, methodName: String, vararg args: Any) =
    callMethod<Unit>(thisObj, methodName, *args)

  fun findField(clazz: Class<*>, name: String): Field? =
    clazz.getDeclaredField(name).apply { isAccessible = true }
}
