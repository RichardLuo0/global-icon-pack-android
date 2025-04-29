package com.richardluo.globalIconPack.iconPack.model

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap

class IconFallback(
  val iconBacks: List<Bitmap>,
  val iconUpons: List<Bitmap>,
  val iconMasks: List<Bitmap>,
  val iconScale: Float = 1f,
  val scaleOnlyForeground: Boolean,
  val backAsAdaptiveBack: Boolean,
  val nonAdaptiveScale: Float,
  val convertToAdaptive: Boolean,
) {
  constructor(
    fs: FallbackSettings,
    getIcon: (String) -> Drawable?,
    config: IconPackConfig,
  ) : this(
    fs.iconBacks.mapNotNull { getIcon(it)?.toBitmap() },
    fs.iconUpons.mapNotNull { getIcon(it)?.toBitmap() },
    fs.iconMasks.mapNotNull { getIcon(it)?.toBitmap() },
    config.scale ?: fs.iconScale,
    config.scaleOnlyForeground,
    config.backAsAdaptiveBack,
    config.nonAdaptiveScale,
    config.convertToAdaptive,
  )

  fun isEmpty() =
    iconBacks.isEmpty() &&
      iconUpons.isEmpty() &&
      iconMasks.isEmpty() &&
      iconScale == 1f &&
      backAsAdaptiveBack == false &&
      nonAdaptiveScale == 1f &&
      convertToAdaptive == false

  fun orNullIfEmpty() = if (isEmpty()) null else this
}

fun IconFallback?.withConfig(config: IconPackConfig) =
  IconFallback(
    this?.iconBacks.orEmpty(),
    this?.iconUpons.orEmpty(),
    this?.iconMasks.orEmpty(),
    config.scale ?: this?.iconScale ?: 1f,
    config.scaleOnlyForeground,
    config.backAsAdaptiveBack,
    config.nonAdaptiveScale,
    config.convertToAdaptive,
  )
