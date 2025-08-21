package com.richardluo.globalIconPack.utils

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.withScale

object IconHelper {
  val ADAPTIVE_ICON_VIEWPORT_SCALE = 1 / (1 + 2 * AdaptiveIconDrawable.getExtraInsetFraction())

  private class CustomAdaptiveIconDrawable(override val state: CState) :
    UnClipAdaptiveIconDrawable(state) {

    constructor(
      background: Drawable?,
      foreground: Drawable?,
      back: Drawable?,
      upon: Drawable?,
      mask: Drawable?,
    ) : this(CState(arrayOf(background, foreground, back, upon, mask)))

    private val paint =
      Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val cache = BitmapCache()

    override fun draw(canvas: Canvas) {
      state.run {
        cache
          .getBitmap(bounds) {
            drawIcon(paint, bounds, back, upon, mask) {
              // Use system mask if mask is not presented
              if (mask != null) super.draw(this) else super.drawClip(this)
            }
          }
          .let { canvas.drawBitmap(it, null, bounds, paint) }
      }
    }

    override fun setAlpha(alpha: Int) {
      super.setAlpha(alpha)
      paint.alpha = alpha
      invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
      super.setColorFilter(colorFilter)
      paint.colorFilter = colorFilter
      invalidateSelf()
    }

    private class CState(drawables: Array<Drawable?>) :
      UnClipAdaptiveIconDrawable.CState(drawables) {

      val back
        get() = drawables[2]

      val upon
        get() = drawables[3]

      val mask
        get() = drawables[4]

      override fun newDrawable() = CustomAdaptiveIconDrawable(CState(drawables.newDrawables()))
    }
  }

  private class CustomBitmapDrawable(
    res: Resources,
    drawable: Drawable,
    back: Drawable?,
    upon: Drawable?,
    mask: Drawable?,
  ) :
    BitmapDrawable(
      res,
      run {
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 300
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 300
        val bounds = Rect(0, 0, width, height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
        createBitmap(width, height).also {
          Canvas(it).drawIcon(paint, bounds, back, upon, mask) {
            drawBitmap(drawable.toBitmap(), null, bounds, paint)
          }
        }
      },
    )

  private class CustomDrawable(private val state: CState) : DrawableWrapper(state.drawable) {

    constructor(
      drawable: Drawable,
      back: Drawable?,
      upon: Drawable?,
      mask: Drawable?,
    ) : this(CState(arrayOf(drawable, back, upon, mask)))

    private val paint =
      Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val cache = BitmapCache()

    override fun draw(canvas: Canvas) {
      state.run {
        cache
          .getBitmap(bounds) { drawIcon(paint, bounds, back, upon, mask) { super.draw(this) } }
          .let { canvas.drawBitmap(it, null, bounds, paint) }
      }
    }

    override fun setAlpha(alpha: Int) {
      super.setAlpha(alpha)
      paint.alpha = alpha
      invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
      super.setColorFilter(colorFilter)
      paint.colorFilter = colorFilter
      invalidateSelf()
    }

    override fun getConstantState(): ConstantState? = state

    private class CState(private val drawables: Array<Drawable?>) : ConstantState() {
      val drawable
        get() = drawables[0]

      val back
        get() = drawables[1]

      val upon
        get() = drawables[2]

      val mask
        get() = drawables[3]

      override fun newDrawable() = CustomDrawable(CState(drawables.newDrawables()))

      override fun getChangingConfigurations(): Int = drawables.getChangingConfigurations()
    }
  }

  fun processIcon(
    baseIcon: Drawable,
    res: Resources,
    back: Drawable?,
    upon: Drawable?,
    mask: Drawable?,
    iconScale: Float,
    scaleOnlyForeground: Boolean,
    backAsAdaptiveBack: Boolean,
    nonAdaptiveScale: Float,
    convertToAdaptive: Boolean,
  ): Drawable =
    if (baseIcon is AdaptiveIconDrawable)
      if (scaleOnlyForeground)
        CustomAdaptiveIconDrawable(
          baseIcon.background,
          baseIcon.foreground?.let { scale(it, iconScale) },
          back,
          upon,
          mask,
        )
      else
        makeAdaptiveBack(backAsAdaptiveBack, back) {
          CustomDrawable(scale(baseIcon, iconScale), it, upon, mask)
        }
    else {
      val iconScale = iconScale * nonAdaptiveScale
      fun makeIcon(back: Drawable?) =
        scale(baseIcon, iconScale).let {
          if (baseIcon is BitmapDrawable) CustomBitmapDrawable(res, it, back, upon, mask)
          else CustomDrawable(it, back, upon, mask)
        }
      if (mask != null || convertToAdaptive) makeAdaptiveBack(backAsAdaptiveBack, back, ::makeIcon)
      else makeIcon(back)
    }

  private fun makeAdaptiveBack(
    backAsAdaptiveBack: Boolean,
    back: Drawable?,
    makeBaseIcon: (Drawable?) -> Drawable,
  ) =
    if (backAsAdaptiveBack && back != null)
      UnClipAdaptiveIconDrawable(scale(back), scale(makeBaseIcon(null)))
    else UnClipAdaptiveIconDrawable(Color.TRANSPARENT.toDrawable(), scale(makeBaseIcon(back)))

  fun makeAdaptive(
    drawable: Drawable,
    background: Drawable = Color.TRANSPARENT.toDrawable(),
    iconScale: Float = 1f,
  ) =
    drawable as? AdaptiveIconDrawable
      ?: UnClipAdaptiveIconDrawable(
        background,
        scale(drawable, ADAPTIVE_ICON_VIEWPORT_SCALE * iconScale),
      )

  fun Canvas.drawIcon(
    paint: Paint,
    bounds: Rect,
    back: Drawable?,
    upon: Drawable?,
    mask: Drawable?,
    drawBaseIcon: Canvas.() -> Unit,
  ) {
    if (bounds.width() < 0) return
    if (bounds.height() < 0) return

    clipRect(bounds)
    drawBaseIcon()

    mask?.let {
      paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
      drawBitmap(
        it.toBitmap(bounds.width(), bounds.height(), Bitmap.Config.ALPHA_8),
        null,
        bounds,
        paint,
      )
    }

    upon?.let {
      paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
      drawBitmap(it.toBitmap(bounds.width(), bounds.height()), null, bounds, paint)
    }

    back?.let {
      paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
      drawBitmap(it.toBitmap(bounds.width(), bounds.height()), null, bounds, paint)
    }

    paint.xfermode = null
  }

  class ScaleDrawable(private val state: CState) : DrawableWrapper(state.drawable) {

    constructor(drawable: Drawable, scale: Float) : this(CState(drawable, scale))

    override fun draw(canvas: Canvas) {
      state.run {
        canvas.withScale(scale, scale, bounds.exactCenterX(), bounds.exactCenterY()) {
          super.draw(canvas)
        }
      }
    }

    override fun getIntrinsicWidth() =
      state.run {
        when (val w = super.intrinsicWidth) {
          -1 -> -1
          else -> (w / scale).toInt()
        }
      }

    override fun getIntrinsicHeight() =
      state.run {
        when (val h = super.intrinsicHeight) {
          -1 -> -1
          else -> (h / scale).toInt()
        }
      }

    override fun getConstantState(): ConstantState? = state

    class CState(val drawable: Drawable, val scale: Float) : ConstantState() {
      override fun newDrawable(): Drawable = ScaleDrawable(drawable.newDrawable(), scale)

      override fun getChangingConfigurations(): Int = drawable.changingConfigurations
    }
  }

  fun scale(drawable: Drawable, scale: Float = ADAPTIVE_ICON_VIEWPORT_SCALE) =
    if (scale == 1f) drawable else ScaleDrawable(drawable, scale)
}
