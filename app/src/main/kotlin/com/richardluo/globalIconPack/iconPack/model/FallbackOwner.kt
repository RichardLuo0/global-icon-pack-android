package com.richardluo.globalIconPack.iconPack.model

import android.graphics.drawable.Drawable

class FallbackOwner(
  val config: IconPackConfig,
  val fallbackSettings: FallbackSettings?,
  val getIcon: (String) -> Drawable?,
) {
  var iconFallback: IconFallback? = null

  init {
    rebuild()
  }

  fun rebuild() {
    if (!config.iconFallback) return
    iconFallback = fallbackSettings?.let {
      IconFallback(it, getIcon, config).orNullIfEmpty()
    }
  }
}
