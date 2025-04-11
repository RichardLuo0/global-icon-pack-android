package com.richardluo.globalIconPack.utils

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
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
    return (runCatching { clazz.getDeclaredMethod(methodName, *parameterTypes) }.getOrNull()
        ?: clazz.declaredMethods.firstOrNull { it.match(methodName, parameterTypes) })
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
    clazz: Class<*>?,
    methodName: String,
    parameterTypes: Array<Class<*>?>,
    hook: XC_MethodHook,
  ) =
    runCatching {
        clazz?.run {
          declaredMethods
            .filter { it.match(methodName, parameterTypes) }
            .map { XposedBridge.hookMethod(it, hook) }
        }
      }
      .getOrNull { log(it) }

  fun hookAllMethods(clazz: Class<*>?, methodName: String, hook: XC_MethodHook) =
    hookAllMethods(clazz, methodName, arrayOf(), hook)

  fun hookAllMethodsOrLog(
    clazz: Class<*>?,
    methodName: String,
    parameterTypes: Array<Class<*>?>,
    hook: XC_MethodHook,
  ) =
    hookAllMethods(clazz, methodName, parameterTypes, hook).also {
      if (it.isNullOrEmpty()) log("No method $methodName is found on class ${clazz?.name}")
    }

  fun hookAllMethodsOrLog(clazz: Class<*>?, methodName: String, hook: XC_MethodHook) =
    hookAllMethodsOrLog(clazz, methodName, arrayOf(), hook)

  fun hookAllConstructors(clazz: Class<*>?, hook: XC_MethodHook) =
    clazz
      ?.run { XposedBridge.hookAllConstructors(this, hook) }
      .also { if (it.isNullOrEmpty()) log("No constructors are found on class ${clazz?.name}") }

  fun hookMethod(method: Method, hook: XC_MethodHook) =
    runCatching { XposedBridge.hookMethod(method, hook) }.getOrNull { log(it) }

  fun findField(clazz: Class<*>, name: String): Field? =
    runCatching { clazz.getDeclaredField(name).apply { isAccessible = true } }.getOrNull { log(it) }
}

abstract class MethodReplacement : XC_MethodHook() {
  override fun beforeHookedMethod(param: MethodHookParam) {
    runCatching { param.result = replaceHookedMethod(param) }
      .exceptionOrNull()
      ?.also { param.throwable = it }
  }

  abstract fun replaceHookedMethod(param: MethodHookParam): Any?
}

@Suppress("UNCHECKED_CAST")
fun <R> MethodHookParam.callOriginalMethod() =
  runCatching { XposedBridge.invokeOriginalMethod(method, thisObject, args) as? R }
    .getOrElse { throw if (it is InvocationTargetException) it.cause ?: it else it }

@Suppress("UNCHECKED_CAST") fun <T> Field.getAs(thisObj: Any? = null) = get(thisObj) as? T

@Suppress("UNCHECKED_CAST")
fun <T> Method.call(thisObj: Any?, vararg param: Any?) = invoke(thisObj, *param) as? T

fun <T> Any.getValue(name: String, clazz: Class<*> = javaClass) =
  ReflectHelper.findField(clazz, name)?.getAs<T>(this)
