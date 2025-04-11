package com.richardluo.globalIconPack.utils

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.annotation.CheckResult
import androidx.collection.LruCache
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.richardluo.globalIconPack.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

@CheckResult
fun withHighByteSet(id: Int, flag: Int): Int {
  return id and 0x00ffffff or flag
}

fun isHighTwoByte(id: Int, flag: Int): Boolean {
  return (id and 0xff000000.toInt()) == flag
}

// Fix when classname is empty
fun unflattenFromString(str: String): ComponentName? {
  val sep = str.indexOf('/')
  if (sep < 0) {
    return null
  }
  val pkg = str.substring(0, sep)
  var cls = str.substring(sep + 1)
  if (cls.isNotEmpty() && cls[0] == '.') {
    cls = pkg + cls
  }
  return ComponentName(pkg, cls)
}

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
