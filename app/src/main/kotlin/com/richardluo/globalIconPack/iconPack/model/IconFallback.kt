package com.richardluo.globalIconPack.iconPack.model

import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.utils.PathDrawable
import kotlin.collections.orEmpty

class IconFallback(
  val iconBacks: List<Drawable>,
  val iconUpons: List<Drawable>,
  val iconMasks: List<Drawable>,
  val iconScale: Float,
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
    config.shape?.let { listOf(PathDrawable(it, config.shapeColor)) }
      ?: fs.iconBacks.mapNotNull { getIcon(it) },
    if (config.enableUpon) fs.iconUpons.mapNotNull { getIcon(it) } else emptyList(),
    config.shape?.let { listOf(PathDrawable(it, fill = -1)) }
      ?: fs.iconMasks.mapNotNull { getIcon(it) },
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
  if (config.iconFallback)
    IconFallback(
      config.shape?.let { listOf(PathDrawable(it, config.shapeColor)) }
        ?: this?.iconBacks.orEmpty(),
      if (config.enableUpon) this?.iconUpons.orEmpty() else emptyList(),
      config.shape?.let { listOf(PathDrawable(it, fill = -1)) } ?: this?.iconMasks.orEmpty(),
      config.scale ?: this?.iconScale ?: 1f,
      config.scaleOnlyForeground,
      config.backAsAdaptiveBack,
      config.nonAdaptiveScale,
      config.convertToAdaptive,
    )
  else null
