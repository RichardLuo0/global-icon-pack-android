package com.richardluo.globalIconPack.utils

import java.lang.ref.SoftReference
import java.util.WeakHashMap

object InstanceManager {
  private val cache = WeakHashMap<Class<*>, SoftReference<Any>>()

  @Suppress("UNCHECKED_CAST")
  fun <T> get(clazz: Class<T>, create: () -> T) = lazy {
    cache[clazz]?.get() as T? ?: create().also { cache[clazz] = SoftReference(it) }
  }

  inline fun <reified T> get() = get(T::class.java) { T::class.java.getConstructor().newInstance() }

  inline fun <reified T> get(noinline create: () -> T) = get(T::class.java, create)

  fun <T> update(clazz: Class<T>, create: () -> T): Lazy<T> {
    cache.remove(clazz)
    return get(clazz, create)
  }

  inline fun <reified T> update(noinline create: () -> T) = update(T::class.java, create)
}

class Weak<T, ARG>(private val create: (args: ARG) -> T) {
  private var ref: SoftReference<T>? = null

  fun get(args: ARG) = ref?.get() ?: create(args).also { ref = SoftReference<T>(it) }
}
