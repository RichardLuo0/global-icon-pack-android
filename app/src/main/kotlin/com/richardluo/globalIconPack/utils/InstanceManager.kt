package com.richardluo.globalIconPack.utils

import java.lang.ref.SoftReference
import java.util.WeakHashMap

private val cache = WeakHashMap<Class<*>, SoftReference<Any>>()

@Suppress("UNCHECKED_CAST")
fun <T> getInstance(clazz: Class<T>, create: () -> T) = lazy {
  cache[clazz]?.get() as T? ?: create().also { cache.put(clazz, SoftReference(it)) }
}

inline fun <reified T> getInstance() =
  getInstance(T::class.java) { T::class.java.getConstructor().newInstance() }

inline fun <reified T> getInstance(noinline create: () -> T) = getInstance(T::class.java, create)

class Weak<T, ARG>(private val create: (args: ARG) -> T) {
  private var ref: SoftReference<T>? = null

  fun get(args: ARG) = ref?.get() ?: create(args).also { ref = SoftReference<T>(it) }
}
