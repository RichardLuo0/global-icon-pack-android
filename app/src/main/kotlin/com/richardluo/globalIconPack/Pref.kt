package com.richardluo.globalIconPack

object PrefKey {
  const val MODE = "mode"
  const val ICON_PACK = "iconPack"
  const val ICON_PACK_AS_FALLBACK = "iconPackAsFallback"

  const val ICON_FALLBACK = "iconFallback"
  const val OVERRIDE_ICON_FALLBACK = "overrideIconFallback"
  const val ICON_PACK_SCALE = "iconPackScale"

  const val NO_FORCE_SHAPE = "noForceShape"
  const val NO_SHADOW = "noShadow"
  const val FORCE_LOAD_CLOCK_AND_CALENDAR = "forceLoadClockAndCalendar"
}

object PrefDef {
  const val MODE = MODE_PROVIDER
  const val ICON_PACK = ""
  const val ICON_PACK_AS_FALLBACK = true

  const val ICON_FALLBACK = false
  const val OVERRIDE_ICON_FALLBACK = false
  const val ICON_PACK_SCALE = 1f

  const val NO_FORCE_SHAPE = false
  const val NO_SHADOW = false
  const val FORCE_LOAD_CLOCK_AND_CALENDAR = true
}

const val MODE_PROVIDER = "provider"
const val MODE_LOCAL = "local"
