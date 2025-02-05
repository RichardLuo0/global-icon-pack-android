package com.richardluo.globalIconPack.utils

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Field
import java.lang.reflect.Method

// Not cached
object ReflectHelper {
  fun findClass(className: String, lpp: LoadPackageParam? = null) =
    runCatching { XposedHelpers.findClass(className, lpp?.classLoader) }.getOrNull { log(it) }

  private fun Method.match(methodName: String, parameterTypes: Array<out Class<*>?>) =
    this.name == methodName &&
      this.parameterTypes.size >= parameterTypes.size &&
      this.parameterTypes.zip(parameterTypes).all { (param, expected) ->
        expected == null || param.isAssignableFrom(expected)
      }

  fun findMethodFirstMatch(
    clazz: Class<*>,
    methodName: String,
    vararg parameterTypes: Class<*>?,
  ): Method? {
    return runCatching { clazz.getDeclaredMethod(methodName, *parameterTypes) }.getOrNull()
      ?: run { clazz.declaredMethods.firstOrNull { it.match(methodName, parameterTypes) } }
        ?.apply { isAccessible = true }
      ?: run {
        log("No method $methodName is found on class ${clazz.name}")
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
    parameterTypes: Array<Class<*>?>,
    hook: XC_MethodHook,
  ) =
    runCatching {
        clazz.declaredMethods
          .filter { it.match(methodName, parameterTypes) }
          .map { XposedBridge.hookMethod(it, hook) }
      }
      .getOrNull { log(it) }

  fun hookAllMethods(clazz: Class<*>, methodName: String, hook: XC_MethodHook) =
    hookAllMethods(clazz, methodName, arrayOf(), hook)

  fun hookAllMethodsOrLog(
    clazz: Class<*>,
    methodName: String,
    parameterTypes: Array<Class<*>?>,
    hook: XC_MethodHook,
  ) =
    hookAllMethods(clazz, methodName, parameterTypes, hook).also {
      if (it.isNullOrEmpty()) log("No method $methodName is found on class ${clazz.name}")
    }

  fun hookAllMethodsOrLog(clazz: Class<*>, methodName: String, hook: XC_MethodHook) =
    hookAllMethodsOrLog(clazz, methodName, arrayOf(), hook)

  fun hookAllConstructors(clazz: Class<*>, hook: XC_MethodHook) =
    XposedBridge.hookAllConstructors(clazz, hook).also {
      if (it.isEmpty()) log("No constructors are found on class ${clazz.name}")
    }

  fun hookMethod(method: Method, hook: XC_MethodHook) =
    runCatching { XposedBridge.hookMethod(method, hook) }.getOrNull { log(it) }

  // This should be cached, thus call xposed helper version
  fun <T> callMethod(thisObj: Any, methodName: String, vararg args: Any): T? {
    return runCatching {
        XposedHelpers.findMethodBestMatch(thisObj.javaClass, methodName, *args)
          .call<T>(thisObj, *args)
      }
      .getOrNull { log(it) }
  }

  fun callMethod(thisObj: Any, methodName: String, vararg args: Any) =
    callMethod<Unit>(thisObj, methodName, *args)

  fun findField(clazz: Class<*>, name: String): Field? =
    runCatching { clazz.getDeclaredField(name).apply { isAccessible = true } }.getOrNull { log(it) }
}

abstract class MethodReplacement : XC_MethodHook() {
  override fun beforeHookedMethod(param: MethodHookParam) {
    runCatching { param.result = replaceHookedMethod(param) }
      .exceptionOrNull()
      ?.let { param.throwable = it }
  }

  abstract fun replaceHookedMethod(param: MethodHookParam): Any?
}
