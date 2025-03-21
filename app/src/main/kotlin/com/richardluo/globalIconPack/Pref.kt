package com.richardluo.globalIconPack

import android.content.SharedPreferences
import me.zhanghai.compose.preference.Preferences

const val MODE_PROVIDER = "provider"
const val MODE_LOCAL = "local"

object Pref {
  val MODE = "mode" to MODE_PROVIDER
  val ICON_PACK = "iconPack" to ""
  val ICON_PACK_AS_FALLBACK = "iconPackAsFallback" to false
  val SHORTCUT = "shortcut" to true

  val ICON_FALLBACK = "iconFallback" to true
  val SCALE_ONLY_FOREGROUND = "scaleOnlyForeground" to true
  val OVERRIDE_ICON_FALLBACK = "overrideIconFallback" to false
  val ICON_PACK_SCALE = "iconPackScale" to 1f

  val PIXEL_LAUNCHER_PACKAGE = "pixelLauncherPackage" to "com.google.android.apps.nexuslauncher"
  val NO_FORCE_SHAPE = "noForceShape" to false
  val NO_SHADOW = "noShadow" to false
  val FORCE_LOAD_CLOCK_AND_CALENDAR = "forceLoadClockAndCalendar" to true
}

fun SharedPreferences.get(pair: Pair<String, String>) = getString(pair.first, pair.second)

fun SharedPreferences.get(pair: Pair<String, Boolean>) = getBoolean(pair.first, pair.second)

fun SharedPreferences.get(pair: Pair<String, Float>) = getFloat(pair.first, pair.second)

fun <T> Preferences.get(pair: Pair<String, T>) = this[pair.first] ?: pair.second
