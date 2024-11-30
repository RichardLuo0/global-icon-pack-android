package com.richardluo.globalIconPack

import androidx.annotation.CheckResult
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Field
import java.lang.reflect.Method

@Suppress("UNCHECKED_CAST")
fun <R> callOriginalMethod(param: MethodHookParam): R {
  return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args) as R
}

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
fun <T, R> Field.getAs(thisObj: Any?, block: (T) -> R) = block.invoke(get(thisObj) as T)

@Suppress("UNCHECKED_CAST")
fun <T> Method.call(thisObj: Any?, vararg param: Any?) = invoke(thisObj, *param) as T

@Suppress("UNCHECKED_CAST")
fun <T, R> Method.call(thisObj: Any?, vararg param: Any?, block: (T) -> R) =
  block(invoke(thisObj, *param) as T)

fun Method.call(thisObj: Any?, vararg param: Any?, block: () -> Unit) =
  call<Unit, Unit>(thisObj, param) { block() }
