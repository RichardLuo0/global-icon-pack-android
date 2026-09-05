package com.richardluo.globalIconPack.utils

import com.richardluo.globalIconPack.utils.Logger.log
import com.richardluo.globalIconPack.utils.Logger.logE
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method

private fun Method.match(methodName: String, parameterTypes: Array<out Class<*>?>) =
  this.name == methodName && match(parameterTypes)

private fun Executable.match(parameterTypes: Array<out Class<*>?>) =
  this.parameterTypes.size >= parameterTypes.size &&
    this.parameterTypes.zip(parameterTypes).all { (param, expected) ->
      expected == null || param.isAssignableFrom(expected)
    }

inline fun <reified T> Field.getAs(thisObj: Any? = null) = get(thisObj) as? T

inline fun <reified T> Method.call(thisObj: Any? = null, vararg param: Any?) =
  invoke(thisObj, *param) as? T

fun classOf(name: String, param: XposedModuleInterface.PackageReadyParam? = null) = runCatching {
  Class.forName(name, true, param?.classLoader)
}
  .getOrNull { log("No class $name is found") }

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

fun Class<*>.field(name: String) = runCatching {
  var clazz: Class<*>? = this
  while (clazz != null && clazz != Any::class.java) {
    try {
      return@runCatching clazz.getDeclaredField(name)
    } catch (_: NoSuchFieldException) {
      clazz = clazz.superclass
    }
  }
  null
}
  .getOrNull()
  ?.apply { isAccessible = true }
  .also { if (it == null) log("No field $name is found on class ${this.name}") }

inline fun <reified R> XposedInterface.Chain.proceed(): R? = proceed() as? R

class HookBuilder : XposedInterface.Hooker {
  class ChainProxy(private val chain: XposedInterface.Chain) : XposedInterface.Chain by chain {
    private var _result: Any? = null
    var resultIsSet = false
      private set

    var result: Any?
      get() = _result
      set(value) {
        resultIsSet = true
        _result = value
      }

    @get:JvmName("getMutableArgs") val args = getArgs().toMutableList()

    inline fun <reified R> proceedWithArgs(): R? =
      proceedWith(thisObject, args.toTypedArray()) as? R
  }

  private var beforeAction: (ChainProxy.() -> Unit)? = null
  private var afterAction: (ChainProxy.() -> Unit)? = null

  fun before(block: ChainProxy.() -> Unit) {
    beforeAction = block
  }

  fun after(block: ChainProxy.() -> Unit) {
    afterAction = block
  }

  inline fun replace(crossinline block: XposedInterface.Chain.() -> Any?) = before {
    result = block()
  }

  override fun intercept(chain: XposedInterface.Chain): Any? {
    val chainProxy = ChainProxy(chain)
    beforeAction?.invoke(chainProxy)
    if (!chainProxy.resultIsSet) chainProxy.result = chainProxy.proceedWithArgs()
    afterAction?.invoke(chainProxy)
    return chainProxy.result
  }
}

context(xposed: XposedInterface)
fun Executable.hook(hooker: XposedInterface.Chain.() -> Any?) = runCatching {
  xposed.hook(this).intercept { chain -> hooker(chain) }
}
  .getOrNull { logE(it) }

context(xposed: XposedInterface)
fun List<Executable>.hook(hooker: XposedInterface.Chain.() -> Any?) = runCatching {
  map { xposed.hook(it).intercept { chain -> hooker(chain) } }
}
  .getOrNull { logE(it) }

context(xposed: XposedInterface)
inline fun Executable.hookCompat(crossinline block: HookBuilder.() -> Unit) = runCatching {
  xposed.hook(this).intercept(HookBuilder().apply { block() })
}
  .getOrNull { logE(it) }

context(xposed: XposedInterface)
inline fun List<Executable>.hookCompat(crossinline block: HookBuilder.() -> Unit) = runCatching {
  map { xposed.hook(it).intercept(HookBuilder().apply { block() }) }
}
  .getOrNull { logE(it) }

context(xposed: XposedInterface)
fun Executable.deoptimize() = apply { xposed.deoptimize(this) }

context(xposed: XposedInterface)
fun List<Executable>.deoptimize() = apply { forEach { xposed.deoptimize(it) } }

interface TryHookResult<T> {
  val isDone: Boolean
  val result: T?
  val unhookRegistry: List<XposedInterface.HookHandle>
}

class TryHookScope<T>(xposed: XposedInterface) : TryHookResult<T>, XposedInterface by xposed {
  override var isDone = false
  override var result: T? = null
  override val unhookRegistry = mutableListOf<XposedInterface.HookHandle>()

  fun XposedInterface.HookHandle?.registerToScopeOrFail() {
    this?.let { unhookRegistry.add(it) } ?: fail()
  }

  fun List<XposedInterface.HookHandle>?.registerToScopeOrFail() {
    if (isNullOrEmpty()) fail() else unhookRegistry.addAll(this)
  }

  fun fail() {
    throw Exception("try hook failed")
  }

  fun <T> T?.failOnNull() {
    if (this == null) fail()
  }

  fun <T> TryHookResult<T>.failOnFail() {
    if (!isDone) fail()
    this@TryHookScope.unhookRegistry.addAll(this.unhookRegistry)
  }

  fun tryDo(block: () -> T) {
    if (isDone) return
    try {
      result = block()
      isDone = true
    } catch (_: Exception) {
      unhookRegistry.forEach { it.unhook() }
    } finally {
      unhookRegistry.clear()
    }
  }

  override fun getApiVersion(): Int {
    return super.getApiVersion()
  }
}

context(xposed: XposedInterface)
inline fun <T> tryHook(
  name: String = "tryHook",
  crossinline block: TryHookScope<T>.() -> Unit,
): TryHookScope<T> =
  TryHookScope<T>(xposed).apply { block() }.also { if (!it.isDone) log("$name failed!") }
