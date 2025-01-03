package com.richardluo.globalIconPack.utils

import android.app.Application
import android.content.SharedPreferences
import androidx.annotation.CheckResult
import com.richardluo.globalIconPack.BuildConfig
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

@Suppress("UNCHECKED_CAST")
fun <R> callOriginalMethod(param: MethodHookParam): R =
  runCatching { XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args) as R }
    .getOrElse { throw if (it is InvocationTargetException) it.cause ?: it else it }

@CheckResult
fun withHighByteSet(id: Int, flag: Int): Int {
  return id and 0x00FFFFFF or flag
}

fun isHighTwoByte(id: Int, flag: Int): Boolean {
  return (id and 0xff000000.toInt()) == flag
}

inline fun <T1 : Any, T2 : Any, R> letAll(p1: T1?, p2: T2?, block: (T1, T2) -> R) =
  if (p1 != null && p2 != null) block(p1, p2) else null

inline fun <T1 : Any, T2 : Any, T3 : Any, R> letAll(
  p1: T1?,
  p2: T2?,
  p3: T3?,
  block: (T1, T2, T3) -> R,
) = if (p1 != null && p2 != null && p3 != null) block(p1, p2, p3) else null

inline fun <T1 : Any, T2 : Any, T3 : Any, T4 : Any, R> letAll(
  p1: T1?,
  p2: T2?,
  p3: T3?,
  p4: T4?,
  block: (T1, T2, T3, T4) -> R,
) = if (p1 != null && p2 != null && p3 != null && p4 != null) block(p1, p2, p3, p4) else null

@Suppress("UNCHECKED_CAST") fun <T> Field.getAs(thisObj: Any?) = get(thisObj) as T

@Suppress("UNCHECKED_CAST")
inline fun <T, R> Field.getAs(thisObj: Any?, block: (T) -> R) = block.invoke(get(thisObj) as T)

@Suppress("UNCHECKED_CAST")
fun <T> Method.call(thisObj: Any?, vararg param: Any?) = invoke(thisObj, *param) as T

@Suppress("UNCHECKED_CAST")
inline fun <T, R> Method.call(thisObj: Any?, vararg param: Any?, block: (T) -> R) =
  block(invoke(thisObj, *param) as T)

inline fun Method.call(thisObj: Any?, vararg param: Any?, block: () -> Unit) =
  call<Unit, Unit>(thisObj, param) { block() }

infix fun String.rEqual(other: String): Boolean {
  if (length != other.length) return false
  for (i in other.indices) if (this[length - 1 - i] != other[length - 1 - i]) return false
  return true
}

infix fun String.rNEqual(other: String) = !rEqual(other)

fun <T> Array<T>.rGet(i: Int) = this[if (i >= 0) i else size + i]

fun <T> Array<T>.rSet(i: Int, obj: T) {
  this[if (i >= 0) i else size + i] = obj
}

@Suppress("UNCHECKED_CAST") fun <T> Any.asType() = this as T

inline fun <T> Result<T>.getOrNull(block: (Throwable) -> Unit) = getOrElse {
  block(it)
  null
}

inline fun <K, V> MutableMap<K, V?>.getOrPutNullable(key: K, defaultValue: () -> V?) =
  if (containsKey(key)) get(key) else defaultValue().also { put(key, it) }

fun SharedPreferences.registerAndCallOnSharedPreferenceChangeListener(
  listener: SharedPreferences.OnSharedPreferenceChangeListener,
  key: String,
) =
  also { listener.onSharedPreferenceChanged(it, key) }
    .registerOnSharedPreferenceChangeListener(listener)

class ReAssignable<T>(var value: T? = null) {

  fun reset() {
    value = null
  }

  inline fun orAssign(block: () -> T) = value ?: block().also { value = it }
}

val isInMod by lazy {
  when (Application.getProcessName()) {
    BuildConfig.APPLICATION_ID -> false
    else -> true
  }
}
