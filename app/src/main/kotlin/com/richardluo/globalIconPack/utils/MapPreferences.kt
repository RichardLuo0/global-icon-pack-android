package com.richardluo.globalIconPack.utils

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.MutablePreferences
import me.zhanghai.compose.preference.Preferences

@OptIn(DelicateCoroutinesApi::class)
fun SharedPreferences.getPreferenceFlow(): MutableStateFlow<Preferences> =
  MutableStateFlow(preferences).also { GlobalScope.launch { it.collect { preferences = it } } }

class MapMutablePreferences(private val map: MutableMap<String, Any> = mutableMapOf()) :
  MutablePreferences {
  @Suppress("UNCHECKED_CAST") override fun <T> get(key: String): T? = map[key] as T?

  override fun asMap(): Map<String, Any> = map

  override fun toMutablePreferences(): MutablePreferences =
    MapMutablePreferences(map.toMutableMap())

  override fun <T> set(key: String, value: T?) {
    if (value != null) {
      map[key] = value
    } else {
      map -= key
    }
  }

  override fun clear() {
    map.clear()
  }
}

open class MapPreferences(private val map: Map<String, Any> = emptyMap()) : Preferences {
  @Suppress("UNCHECKED_CAST") override fun <T> get(key: String): T? = map[key] as T?

  override fun asMap(): Map<String, Any> = map

  override fun toMutablePreferences(): MutablePreferences =
    MapMutablePreferences(map.toMutableMap())
}

private class InitMapPreferences(map: Map<String, Any> = emptyMap()) : MapPreferences(map)

var SharedPreferences.preferences: Preferences
  get() = @Suppress("UNCHECKED_CAST") InitMapPreferences(all as Map<String, Any>)
  set(value) {
    if (value is InitMapPreferences) return
    edit {
      clear()
      value.asMap().forEach { (key, value) ->
        when (value) {
          is Boolean -> putBoolean(key, value)
          is Int -> putInt(key, value)
          is Long -> putLong(key, value)
          is Float -> putFloat(key, value)
          is String -> putString(key, value)
          is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(key, value as Set<String>)
          else -> throw IllegalArgumentException("Unsupported type for value $value")
        }
      }
    }
  }
