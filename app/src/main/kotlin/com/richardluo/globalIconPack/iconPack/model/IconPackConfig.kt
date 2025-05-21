package com.richardluo.globalIconPack.iconPack.model

import android.content.SharedPreferences
import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.PrefEntry
import me.zhanghai.compose.preference.Preferences

data class IconPackConfig(
  val iconPackAsFallback: Boolean = Pref.ICON_PACK_AS_FALLBACK.def,
  val iconFallback: Boolean = Pref.ICON_FALLBACK.def,
  val scale: Float? = null,
  val shape: String? = null,
  val shapeColor: Int = Pref.ICON_PACK_SHAPE_COLOR.def,
  val enableUpon: Boolean = true,
  val scaleOnlyForeground: Boolean = Pref.SCALE_ONLY_FOREGROUND.def,
  val backAsAdaptiveBack: Boolean = Pref.BACK_AS_ADAPTIVE_BACK.def,
  val nonAdaptiveScale: Float = Pref.NON_ADAPTIVE_SCALE.def,
  val convertToAdaptive: Boolean = Pref.CONVERT_TO_ADAPTIVE.def,
) {
  constructor(
    pref: Map<String, Any>
  ) : this(
    pref.get(Pref.ICON_PACK_AS_FALLBACK),
    pref.get(Pref.ICON_FALLBACK),
    if (pref.get(Pref.OVERRIDE_ICON_FALLBACK)) pref.get(Pref.ICON_PACK_SCALE) else null,
    if (pref.get(Pref.OVERRIDE_ICON_FALLBACK))
      pref.get(Pref.ICON_PACK_SHAPE).takeIf { it.isNotEmpty() }
    else null,
    pref.get(Pref.ICON_PACK_SHAPE_COLOR),
    if (pref.get(Pref.OVERRIDE_ICON_FALLBACK)) pref.get(Pref.ICON_PACK_ENABLE_UPON) else true,
    pref.get(Pref.SCALE_ONLY_FOREGROUND),
    pref.get(Pref.BACK_AS_ADAPTIVE_BACK),
    pref.get(Pref.NON_ADAPTIVE_SCALE),
    pref.get(Pref.CONVERT_TO_ADAPTIVE),
  )

  @Suppress("UNCHECKED_CAST")
  constructor(pref: SharedPreferences) : this(pref.all as Map<String, Any>)

  constructor(pref: Preferences) : this(pref.asMap())
}

val defaultIconPackConfig = IconPackConfig()

private inline fun <reified T> Map<String, Any>.get(pair: PrefEntry<T>) =
  get(pair.key) as? T ?: pair.def
