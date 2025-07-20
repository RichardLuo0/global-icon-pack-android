package com.richardluo.globalIconPack.utils

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.Drawable.ConstantState
import android.os.Build
import android.widget.Toast
import androidx.annotation.CheckResult
import androidx.collection.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.richardluo.globalIconPack.BuildConfig
import com.richardluo.globalIconPack.ui.MyApplication.Companion.context
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
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

inline fun <reified T> Any?.asType() = this as? T

inline fun <T> Result<T>.getOrNull(block: (Throwable) -> Unit) = getOrElse {
  block(it)
  null
}

inline fun <K, V> MutableMap<K, V?>.getOrPutNullable(key: K, defaultValue: () -> V?) =
  if (containsKey(key)) get(key) else defaultValue().also { put(key, it) }

val isInMod by lazy {
  val currentProcessName =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName()
    else {
      // Fallback for API < 28
      val pid = android.os.Process.myPid()
      val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
      manager.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName ?: ""
    }

  currentProcessName != BuildConfig.APPLICATION_ID
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

inline fun <R> runSafe(crossinline block: () -> R) = run { block() }

inline fun <T, R> T.runSafe(crossinline block: T.() -> R) = run { block() }

suspend inline fun <R> runCatchingToast(
  context: Context,
  crossinline message: (Throwable) -> String = { it.toString() },
  onFailure: (Throwable) -> Unit = {},
  block: suspend () -> R,
) =
  try {
      Result.success(block())
    } catch (e: Throwable) {
      Result.failure(e)
    }
    .onFailure {
      log(it)
      withContext(Dispatchers.Main) {
        Toast.makeText(context, message(it), Toast.LENGTH_LONG).show()
      }
      onFailure(it)
    }

inline fun <R> runCatchingToastOnMain(
  context: Context,
  crossinline message: (Throwable) -> String = { it.toString() },
  block: () -> R,
) =
  runCatching(block).onFailure {
    log(it)
    Toast.makeText(context, message(it), Toast.LENGTH_LONG).show()
  }

inline fun <T, reified R> Array<T>.map(transform: (T) -> R): Array<R> {
  return Array(size) { i -> transform(this[i]) }
}

inline fun <T, reified R> Array<T>.mapIndexed(transform: (Int, T) -> R): Array<R> {
  return Array(size) { i -> transform(i, this[i]) }
}

inline fun <K, reified V> MutableMap<K, V>.getOrPut(
  keys: List<K>,
  fetch: (List<K>, getKey: (Int) -> K) -> Array<V>,
): List<V?> {
  val (hits, misses) = keys.indices.partition { contains(keys[it]) }
  val array = arrayOfNulls<V>(keys.size)
  hits.forEach { array[it] = this[keys[it]] }
  if (misses.isEmpty()) return array.asList()
  fetch(misses.map { keys[it] }) { keys[misses[it]] }
    .forEachIndexed { i, value ->
      val index = misses[i]
      this[keys[index]] = value
      array[index] = value
    }
  return array.asList()
}

inline fun <T> MutableState<T>.update(crossinline transform: T.() -> T) {
  value = value.transform()
}

inline fun <T> T.chain(block: T.() -> T?): T = block() ?: this

inline fun <T> Flow<List<T>?>.filter(
  searchText: Flow<String>,
  crossinline predicate: (T, String) -> Boolean,
) =
  combineTransform(searchText.debounceInput()) { items, text ->
      emit(null)
      items ?: return@combineTransform
      emit(if (text.isEmpty()) items else items.filter { predicate(it, text) })
    }
    .conflate()
    .flowOn(Dispatchers.Default)

fun getChangingConfigurations(init: Int = 0, vararg css: ConstantState?): Int =
  css.fold(init) { last, cs -> last or (cs?.changingConfigurations ?: 0) }

abstract class CSSWrapper(protected val css: Array<ConstantState?>) : ConstantState() {
  protected fun newDrawables() = css.map { it?.newDrawable() }

  override fun getChangingConfigurations(): Int = getChangingConfigurations(css = css)
}

fun createCSS(vararg drawables: Drawable?): Array<ConstantState?>? {
  return drawables.map { if (it == null) null else it.constantState ?: return@createCSS null }
}

val Shell.Result.msg: String
  get() = "code: $code err: ${err.joinToString("\n")} out: ${out.joinToString("\n")}"

fun Shell.Result.throwOnFail() {
  if (!isSuccess) throw Exception("Database permission setting failed: $msg")
}
