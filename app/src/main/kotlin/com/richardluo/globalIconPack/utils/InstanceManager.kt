package com.richardluo.globalIconPack.utils

import java.lang.ref.SoftReference
import java.lang.ref.WeakReference
import java.util.WeakHashMap

object InstanceManager {
  private val cache = WeakHashMap<Class<*>, SoftReference<Any>>()

  @Suppress("UNCHECKED_CAST")
  fun <T> get(clazz: Class<T>, create: () -> T) =
    lazy(cache) { cache[clazz]?.get() as T? ?: create().also { cache[clazz] = SoftReference(it) } }

  inline fun <reified T> get() = get(T::class.java) { T::class.java.getConstructor().newInstance() }

  inline fun <reified T> get(noinline create: () -> T) = get(T::class.java, create)

  fun <T> update(clazz: Class<T>, create: () -> T): Lazy<T> {
    cache.remove(clazz)
    return get(clazz, create)
  }

  inline fun <reified T> update(noinline create: () -> T) = update(T::class.java, create)
}

class Weak<T, Arg>(private val create: (args: Arg) -> T) {
  private var ref: WeakReference<T>? = null

  fun get(args: Arg) = ref?.get() ?: create(args).also { ref = WeakReference<T>(it) }
}
