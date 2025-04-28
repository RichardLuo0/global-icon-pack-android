package com.richardluo.globalIconPack.utils

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

private fun Method.match(methodName: String, parameterTypes: Array<out Class<*>?>) =
  this.name == methodName && match(parameterTypes)

private fun Executable.match(parameterTypes: Array<out Class<*>?>) =
  this.parameterTypes.size >= parameterTypes.size &&
    this.parameterTypes.zip(parameterTypes).all { (param, expected) ->
      expected == null || param.isAssignableFrom(expected)
    }

@Suppress("UNCHECKED_CAST")
fun <R> MethodHookParam.callOriginalMethod() =
  runCatching { XposedBridge.invokeOriginalMethod(method, thisObject, args) as? R }
    .getOrElse { throw if (it is InvocationTargetException) it.cause ?: it else it }

@Suppress("UNCHECKED_CAST") fun <T> Field.getAs(thisObj: Any? = null) = get(thisObj) as? T

@Suppress("UNCHECKED_CAST")
fun <T> Method.call(thisObj: Any?, vararg param: Any?) = invoke(thisObj, *param) as? T

fun classOf(name: String, lpp: LoadPackageParam? = null) =
  runCatching { XposedHelpers.findClass(name, lpp?.classLoader) }.getOrNull { log(it) }

fun Class<*>?.method(name: String, vararg parameterTypes: Class<*>?) =
  this?.run {
      (runCatching { getDeclaredMethod(name, *parameterTypes) }.getOrNull()
          ?: declaredMethods.firstOrNull { it.match(name, parameterTypes) })
        ?.apply { isAccessible = true }
    }
    .also { if (it == null) log("No method $name is found on class ${this?.name}") }

fun Class<*>?.allMethods(methodName: String, vararg parameterTypes: Class<*>?) =
  this?.run { declaredMethods.filter { it.match(methodName, parameterTypes) } }
    .also { if (it.isNullOrEmpty()) log("No methods $methodName are found on class ${this?.name}") }

fun Class<*>?.allConstructors(vararg parameterTypes: Class<*>?) =
  this?.run { declaredConstructors.filter { it.match(parameterTypes) } }
    .also { if (it.isNullOrEmpty()) log("No constructors are found on class ${this?.name}") }

fun Class<*>?.field(name: String) =
  this?.runCatching { getDeclaredField(name).apply { isAccessible = true } }
    ?.getOrNull()
    .also { if (it == null) log("No field $name is found on class ${this?.name}") }

class HookBuilder {
  private var beforeAction: ((MethodHookParam) -> Unit)? = null
  private var afterAction: ((MethodHookParam) -> Unit)? = null

  fun before(block: (MethodHookParam) -> Unit) {
    beforeAction = block
  }

  fun after(block: (MethodHookParam) -> Unit) {
    afterAction = block
  }

  fun replace(block: (MethodHookParam) -> Any?) = before { param ->
    runCatching { param.result = block(param) }.exceptionOrNull()?.also { param.throwable = it }
  }

  fun callOriginal(param: MethodHookParam) {
    param.result = param.callOriginalMethod()
  }

  fun build() =
    object : XC_MethodHook() {
      override fun beforeHookedMethod(param: MethodHookParam) {
        beforeAction?.invoke(param)
      }

      override fun afterHookedMethod(param: MethodHookParam) {
        afterAction?.invoke(param)
      }
    }
}

inline fun Executable?.hook(crossinline block: HookBuilder.() -> Unit) =
  runCatching {
      this?.run { XposedBridge.hookMethod(this, HookBuilder().apply { block() }.build()) }
    }
    .getOrNull { log(it) }

inline fun List<Executable>?.hook(crossinline block: HookBuilder.() -> Unit) =
  runCatching { this?.map { XposedBridge.hookMethod(it, HookBuilder().apply { block() }.build()) } }
    .getOrNull { log(it) }
