package com.richardluo.globalIconPack.iconPack

import android.content.SharedPreferences
import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.get
import me.zhanghai.compose.preference.Preferences

data class IconPackConfig(
  val iconPackAsFallback: Boolean = Pref.ICON_PACK_AS_FALLBACK.second,
  val iconFallback: Boolean = Pref.ICON_FALLBACK.second,
  val scale: Float? = if (Pref.OVERRIDE_ICON_FALLBACK.second) Pref.ICON_PACK_SCALE.second else null,
  val scaleOnlyForeground: Boolean = Pref.SCALE_ONLY_FOREGROUND.second,
) {
  constructor(
    pref: SharedPreferences
  ) : this(
    pref.get(Pref.ICON_PACK_AS_FALLBACK),
    pref.get(Pref.ICON_FALLBACK),
    if (pref.get(Pref.OVERRIDE_ICON_FALLBACK)) pref.get(Pref.ICON_PACK_SCALE) else null,
    pref.get(Pref.SCALE_ONLY_FOREGROUND),
  )

  constructor(
    pref: Preferences
  ) : this(
    pref.get(Pref.ICON_PACK_AS_FALLBACK),
    pref.get(Pref.ICON_FALLBACK),
    if (pref.get(Pref.OVERRIDE_ICON_FALLBACK)) pref.get(Pref.ICON_PACK_SCALE) else null,
    pref.get(Pref.SCALE_ONLY_FOREGROUND),
  )
}

val defaultIconPackConfig = IconPackConfig()
