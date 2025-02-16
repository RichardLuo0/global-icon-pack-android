package com.richardluo.globalIconPack

import android.content.SharedPreferences
import me.zhanghai.compose.preference.Preferences

const val MODE_PROVIDER = "provider"
const val MODE_LOCAL = "local"

object Pref {
  val MODE = Pair("mode", MODE_PROVIDER)
  val ICON_PACK = Pair("iconPack", "")
  val ICON_PACK_AS_FALLBACK = Pair("iconPackAsFallback", false)
  val SHORTCUT = Pair("shortcut", true)

  val ICON_FALLBACK = Pair("iconFallback", true)
  val OVERRIDE_ICON_FALLBACK = Pair("overrideIconFallback", false)
  val ICON_PACK_SCALE = Pair("iconPackScale", 1f)

  val PIXEL_LAUNCHER_PACKAGE = Pair("pixelLauncherPackage", "com.google.android.apps.nexuslauncher")
  val NO_FORCE_SHAPE = Pair("noForceShape", false)
  val NO_SHADOW = Pair("noShadow", false)
  val FORCE_LOAD_CLOCK_AND_CALENDAR = Pair("forceLoadClockAndCalendar", true)
}

fun SharedPreferences.get(pair: Pair<String, String>) = getString(pair.first, pair.second)

fun SharedPreferences.get(pair: Pair<String, Boolean>) = getBoolean(pair.first, pair.second)

fun SharedPreferences.get(pair: Pair<String, Float>) = getFloat(pair.first, pair.second)

fun <T> Preferences.get(pair: Pair<String, T>) = this[pair.first] ?: pair.second
