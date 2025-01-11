package com.richardluo.globalIconPack.utils

import java.util.WeakHashMap

private val cache = WeakHashMap<Class<*>, Any>()

@Suppress("UNCHECKED_CAST")
fun <T> getInstance(clazz: Class<T>) = lazy {
  cache.getOrPut(clazz) { clazz.getConstructor().newInstance() } as T
}

inline fun <reified T> getInstance() = getInstance(T::class.java)

@Suppress("UNCHECKED_CAST")
fun <T> getInstance(clazz: Class<T>, create: () -> T) = lazy {
  cache.getOrPut(clazz) { create() } as T
}

inline fun <reified T> getInstance(noinline create: () -> T) = getInstance(T::class.java, create)
