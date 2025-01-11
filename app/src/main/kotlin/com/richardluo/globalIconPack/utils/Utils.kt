package com.richardluo.globalIconPack.utils

import android.app.Application
import android.database.Cursor
import androidx.annotation.CheckResult
import androidx.collection.LruCache
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.richardluo.globalIconPack.BuildConfig
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.xmlpull.v1.XmlPullParser

@Suppress("UNCHECKED_CAST")
fun <R> callOriginalMethod(param: MethodHookParam): R =
  runCatching { XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args) as R }
    .getOrElse { throw if (it is InvocationTargetException) it.cause ?: it else it }

@CheckResult
fun withHighByteSet(id: Int, flag: Int): Int {
  return id and 0x00ffffff or flag
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

val isInMod by lazy {
  when (Application.getProcessName()) {
    BuildConfig.APPLICATION_ID -> false
    else -> true
  }
}

fun <T> Cursor.getFirstRow(block: (Cursor) -> T) = this.takeIf { it.moveToFirst() }?.use(block)

fun Cursor.getBlob(name: String) = this.getBlob(getColumnIndexOrThrow(name))

fun Cursor.getLong(name: String) = this.getLong(getColumnIndexOrThrow(name))

fun String.ifNotEmpty(block: (String) -> String) = if (isNotEmpty()) block(this) else this

operator fun XmlPullParser.get(key: String): String? = this.getAttributeValue(null, key)

inline fun <K : Any, V : Any> LruCache<K, V>.getOrPut(key: K, defaultValue: () -> V): V {
  val value = get(key)
  return if (value == null) {
    val answer = defaultValue()
    put(key, answer)
    answer
  } else value
}

@Composable fun <T> StateFlow<T>.getState() = collectAsStateWithLifecycle()

@Composable fun <T> StateFlow<T>.getValue() = collectAsStateWithLifecycle().value

@Composable fun <T> Flow<T>.getState(init: T) = collectAsStateWithLifecycle(init)

@Composable fun <T> Flow<T>.getValue(init: T) = collectAsStateWithLifecycle(init).value
