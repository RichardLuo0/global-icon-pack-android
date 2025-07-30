package com.richardluo.globalIconPack.utils

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Member
import java.lang.reflect.Method

private fun Method.match(methodName: String, parameterTypes: Array<out Class<*>?>) =
  this.name == methodName && match(parameterTypes)

private fun Executable.match(parameterTypes: Array<out Class<*>?>) =
  this.parameterTypes.size >= parameterTypes.size &&
    this.parameterTypes.zip(parameterTypes).all { (param, expected) ->
      expected == null || param.isAssignableFrom(expected)
    }

inline fun <reified R> MethodHookParam.callOriginalMethod() =
  runCatching { XposedBridge.invokeOriginalMethod(method, thisObject, args) as? R }
    .getOrElse { throw if (it is InvocationTargetException) it.cause ?: it else it }

inline fun <reified T> Field.getAs(thisObj: Any? = null) = get(thisObj) as? T

inline fun <reified T> Method.call(thisObj: Any? = null, vararg param: Any?) =
  invoke(thisObj, *param) as? T

fun classOf(name: String, lpp: LoadPackageParam? = null) =
  runCatching { XposedHelpers.findClass(name, lpp?.classLoader) }.getOrNull { log(it) }

fun Class<*>.method(name: String, vararg parameterTypes: Class<*>?) =
  (runCatching { getDeclaredMethod(name, *parameterTypes) }.getOrNull()
      ?: declaredMethods.firstOrNull { it.match(name, parameterTypes) })
    ?.apply { isAccessible = true }
    .also { if (it == null) log("No method $name is found on class ${this.name}") }

fun Class<*>.constructor(vararg parameterTypes: Class<*>?) =
  (runCatching { getDeclaredConstructor(*parameterTypes) }.getOrNull()
      ?: declaredConstructors.firstOrNull { it.match(parameterTypes) })
    ?.apply { isAccessible = true }
    .also { if (it == null) log("No constructor is found on class ${this.name}") }

fun Class<*>.allMethods(methodName: String, vararg parameterTypes: Class<*>?) =
  declaredMethods
    .filter { it.match(methodName, parameterTypes) }
    .apply { forEach { it.isAccessible = true } }
    .also { if (it.isEmpty()) log("No methods $methodName are found on class ${this.name}") }

fun Class<*>.allConstructors(vararg parameterTypes: Class<*>?) =
  declaredConstructors
    .filter { it.match(parameterTypes) }
    .apply { forEach { it.isAccessible = true } }
    .also { if (it.isEmpty()) log("No constructors are found on class ${this.name}") }

fun Class<*>.field(name: String) =
  runCatching { getDeclaredField(name).apply { isAccessible = true } }
    .getOrNull()
    .also { if (it == null) log("No field $name is found on class ${this.name}") }

class HookBuilder : XC_MethodHook() {
  private var beforeAction: (MethodHookParam.() -> Unit)? = null
  private var afterAction: (MethodHookParam.() -> Unit)? = null

  fun before(block: MethodHookParam.() -> Unit) {
    beforeAction = block
  }

  fun after(block: MethodHookParam.() -> Unit) {
    afterAction = block
  }

  inline fun replace(crossinline block: MethodHookParam.() -> Any?) = before {
    runCatching { result = block() }.exceptionOrNull()?.also { throwable = it }
  }

  override fun beforeHookedMethod(param: MethodHookParam) {
    beforeAction?.invoke(param)
  }

  override fun afterHookedMethod(param: MethodHookParam) {
    afterAction?.invoke(param)
  }
}

inline fun Executable.hook(crossinline block: HookBuilder.() -> Unit) =
  runCatching { XposedBridge.hookMethod(this, HookBuilder().apply { block() }) }
    .getOrNull { log(it) }

inline fun List<Executable>.hook(crossinline block: HookBuilder.() -> Unit) =
  runCatching { map { XposedBridge.hookMethod(it, HookBuilder().apply { block() }) } }
    .getOrNull { log(it) }

private val deoptimizeMethodM by lazy {
  XposedBridge::class.java.method("deoptimizeMethod", Member::class.java)
}

fun Executable.deoptimize() = apply { deoptimizeMethodM?.invoke(null, this) }

fun List<Executable>.deoptimize() = apply { forEach { deoptimizeMethodM?.invoke(null, it) } }
