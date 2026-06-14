package com.richardluo.globalIconPack.utils

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toDrawable
import com.richardluo.globalIconPack.utils.IconHelper.scale

@RequiresApi(Build.VERSION_CODES.S)
private class MonoForegroundDrawable(private val mono: Drawable, @ColorInt color: Int) :
  DrawableWrapper(mono) {
  private val paint =
    Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
      this.color = color
      colorFilter = BlendModeColorFilter(color, BlendMode.SRC_IN)
    }
  private val monoBitmapCache = BitmapCache()

  override fun draw(canvas: Canvas) {
    val monoBitmap =
      monoBitmapCache.getBitmap(bounds) {
        mono.draw(this)
      }
    canvas.drawBitmap(monoBitmap, null, bounds, paint)
  }

  override fun setAlpha(alpha: Int) {
    super.setAlpha(alpha)
    paint.alpha = alpha
    invalidateSelf()
  }
}

@RequiresApi(Build.VERSION_CODES.S)
class MonochromeDrawable(
  res: Resources, // app resource
  private val mono: Drawable,
  private val state: CState? = createCSS(mono)?.let { CState(res, it) },
  isDarkTheme: Boolean =
    res.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
      Configuration.UI_MODE_NIGHT_YES,
) :
  AdaptiveIconDrawable(
    if (isDarkTheme) res.getColor(android.R.color.system_accent2_800, null).toDrawable()
    else res.getColor(android.R.color.system_accent1_100, null).toDrawable(),
    MonoForegroundDrawable(
      scale(mono),
      if (isDarkTheme) res.getColor(android.R.color.system_accent1_200, null)
      else res.getColor(android.R.color.system_accent1_700, null),
    ),
  ) {

  override fun getChangingConfigurations(): Int =
    super.getChangingConfigurations() or ActivityInfo.CONFIG_UI_MODE

  override fun getConstantState() = state

  class CState(private val res: Resources, private val css: Array<ConstantState?>) :
    ConstantState() {

    override fun newDrawable() = css.newDrawables().let { MonochromeDrawable(res, it[0]!!, this) }

    override fun getChangingConfigurations(): Int =
      css.getChangingConfigurations() or ActivityInfo.CONFIG_UI_MODE
  }
}
