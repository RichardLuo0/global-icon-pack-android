package com.richardluo.globalIconPack

import android.content.SharedPreferences
import android.graphics.Color
import me.zhanghai.compose.preference.Preferences

data class PrefEntry<out V>(val key: String, val def: V)

infix fun <V> String.defaultTo(def: V): PrefEntry<V> = PrefEntry(this, def)

const val MODE_SHARE = "share"
const val MODE_PROVIDER = "provider"
const val MODE_LOCAL = "local"

object Pref {
  val MODE = "mode" defaultTo MODE_LOCAL
  val ICON_PACK = "iconPack" defaultTo ""
  val ICON_PACK_AS_FALLBACK = "iconPackAsFallback" defaultTo false
  val SHORTCUT = "shortcut" defaultTo true

  val ICON_FALLBACK = "iconFallback" defaultTo true
  val SCALE_ONLY_FOREGROUND = "scaleOnlyForeground" defaultTo false
  val BACK_AS_ADAPTIVE_BACK = "backAsAdaptiveBack" defaultTo false
  val NON_ADAPTIVE_SCALE = "nonAdaptiveScale" defaultTo 1f
  val CONVERT_TO_ADAPTIVE = "convertToAdaptive" defaultTo true
  val OVERRIDE_ICON_FALLBACK = "overrideIconFallback" defaultTo false
  val ICON_PACK_SCALE = "iconPackScale" defaultTo 1f
  val ICON_PACK_SHAPE = "iconPackShapeMask" defaultTo ""
  val ICON_PACK_SHAPE_COLOR = "iconPackShapeColor" defaultTo Color.WHITE
  val ICON_PACK_ENABLE_UPON = "iconPackEnableUpon" defaultTo true

  val PIXEL_LAUNCHER_PACKAGE =
    "pixelLauncherPackage" defaultTo "com.google.android.apps.nexuslauncher"
  val NO_SHADOW = "noShadow" defaultTo false
  val FORCE_LOAD_CLOCK_AND_CALENDAR = "forceLoadClockAndCalendar" defaultTo true
  val CLOCK_USE_FALLBACK_MASK = "clockUseFallbackMask" defaultTo false
  val DISABLE_CLOCK_SECONDS = "disableClockSeconds" defaultTo true
  val FORCE_ACTIVITY_ICON_FOR_TASK = "forceActivityIconForTask" defaultTo false
}

object AppPref {
  val NEED_SETUP = "needSetup" defaultTo true
  val PATH = "PATH" defaultTo "iconPack.db"
}

fun SharedPreferences.get(pair: PrefEntry<String>) = getString(pair.key, pair.def) as String

fun SharedPreferences.get(pair: PrefEntry<Boolean>) = getBoolean(pair.key, pair.def)

fun SharedPreferences.get(pair: PrefEntry<Float>) = getFloat(pair.key, pair.def)

fun <T> Preferences.get(pair: PrefEntry<T>) = this[pair.key] ?: pair.def
