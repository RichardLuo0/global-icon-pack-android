package com.richardluo.globalIconPack.iconPack.model

import android.content.SharedPreferences
import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.get
import me.zhanghai.compose.preference.Preferences

data class IconPackConfig(
  val iconPackAsFallback: Boolean = Pref.ICON_PACK_AS_FALLBACK.second,
  val iconFallback: Boolean = Pref.ICON_FALLBACK.second,
  val scale: Float? = if (Pref.OVERRIDE_ICON_FALLBACK.second) Pref.ICON_PACK_SCALE.second else null,
  val scaleOnlyForeground: Boolean = Pref.SCALE_ONLY_FOREGROUND.second,
  val backAsAdaptiveBack: Boolean = Pref.BACK_AS_ADAPTIVE_BACK.second,
  val nonAdaptiveScale: Float = Pref.NON_ADAPTIVE_SCALE.second,
  val convertToAdaptive: Boolean = Pref.CONVERT_TO_ADAPTIVE.second,
) {
  constructor(
    pref: SharedPreferences
  ) : this(
    pref.get(Pref.ICON_PACK_AS_FALLBACK),
    pref.get(Pref.ICON_FALLBACK),
    if (pref.get(Pref.OVERRIDE_ICON_FALLBACK)) pref.get(Pref.ICON_PACK_SCALE) else null,
    pref.get(Pref.SCALE_ONLY_FOREGROUND),
    pref.get(Pref.BACK_AS_ADAPTIVE_BACK),
    pref.get(Pref.NON_ADAPTIVE_SCALE),
    pref.get(Pref.CONVERT_TO_ADAPTIVE),
  )

  constructor(
    pref: Preferences
  ) : this(
    pref.get(Pref.ICON_PACK_AS_FALLBACK),
    pref.get(Pref.ICON_FALLBACK),
    if (pref.get(Pref.OVERRIDE_ICON_FALLBACK)) pref.get(Pref.ICON_PACK_SCALE) else null,
    pref.get(Pref.SCALE_ONLY_FOREGROUND),
    pref.get(Pref.BACK_AS_ADAPTIVE_BACK),
    pref.get(Pref.NON_ADAPTIVE_SCALE),
    pref.get(Pref.CONVERT_TO_ADAPTIVE),
  )
}

val defaultIconPackConfig = IconPackConfig()
