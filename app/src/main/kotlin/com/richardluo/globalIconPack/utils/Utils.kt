package com.richardluo.globalIconPack.utils

import android.app.Application
import android.content.Context
import android.database.Cursor
import android.widget.Toast
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
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

@Suppress("UNCHECKED_CAST") fun <T> Field.getAs(thisObj: Any?) = get(thisObj) as T

@Suppress("UNCHECKED_CAST")
inline fun <T, R> Field.getAs(thisObj: Any?, block: (T) -> R) = block.invoke(get(thisObj) as T)

@Suppress("UNCHECKED_CAST")
fun <T> Method.call(thisObj: Any?, vararg param: Any?) = invoke(thisObj, *param) as T

@Suppress("UNCHECKED_CAST")
inline fun <T, R> Method.call(thisObj: Any?, vararg param: Any?, block: (T) -> R) =
  block(invoke(thisObj, *param) as T)

infix fun String.rEqual(other: String): Boolean {
  if (length != other.length) return false
  for (i in other.indices) if (this[length - 1 - i] != other[length - 1 - i]) return false
  return true
}

infix fun String.rNEqual(other: String) = !rEqual(other)

fun <T> Array<T>.rGet(i: Int) = getOrNull(if (i >= 0) i else size + i)

fun <T> Array<T>.rSet(i: Int, obj: T) {
  val index = if (i >= 0) i else size + i
  if (index in indices) this[index] = obj
}

@Suppress("UNCHECKED_CAST") fun <T> Any?.asType() = this as? T

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

inline fun <T> Cursor.useFirstRow(block: (Cursor) -> T) =
  this.takeIf { it.moveToFirst() }?.use(block)

fun Cursor.getBlob(name: String) = this.getBlob(getColumnIndexOrThrow(name))

fun Cursor.getLong(name: String) = this.getLong(getColumnIndexOrThrow(name))

fun Cursor.getString(name: String) = this.getString(getColumnIndexOrThrow(name))

fun Cursor.getInt(name: String) = this.getInt(getColumnIndexOrThrow(name))

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

@OptIn(FlowPreview::class)
fun Flow<String>.debounceInput(delay: Long = 300L) = debounce { if (it.isEmpty()) 0L else delay }

fun flowTrigger() =
  MutableSharedFlow<Unit>(1, onBufferOverflow = BufferOverflow.DROP_OLDEST).apply { tryEmit(Unit) }

suspend fun MutableSharedFlow<Unit>.emit() = emit(Unit)

fun MutableSharedFlow<Unit>.tryEmit() = tryEmit(Unit)

suspend inline fun <R> runCatchingToast(context: Context, block: () -> R) =
  runCatching(block).getOrNull {
    withContext(Dispatchers.Main) {
      Toast.makeText(context, it.toString(), Toast.LENGTH_LONG).show()
    }
  }
