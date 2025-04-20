package com.richardluo.globalIconPack.utils

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.InsetDrawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable

object IconHelper {
  val ADAPTIVE_ICON_VIEWPORT_SCALE = 1 / (1 + 2 * AdaptiveIconDrawable.getExtraInsetFraction())

  private class CustomAdaptiveIconDrawable(
    background: Drawable?,
    foreground: Drawable?,
    private val back: Bitmap?,
    private val upon: Bitmap?,
    private val mask: Bitmap?,
  ) : UnClipAdaptiveIconDrawable(background, foreground) {
    private val paint =
      Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val cache = BitmapCache()

    override fun draw(canvas: Canvas) {
      cache
        .getBitmap(bounds) {
          drawIcon(paint, bounds, back, upon, mask) {
            // Use original mask if mask is not presented
            if (mask != null) super.draw(this) else super.drawClip(this)
          }
        }
        .let { canvas.drawBitmap(it, null, bounds, paint) }
    }

    override fun setAlpha(alpha: Int) {
      super.setAlpha(alpha)
      paint.alpha = alpha
    }

    override val cState by lazy {
      CState(background?.constantState, foreground?.constantState, back, upon, mask)
    }

    private class CState(
      backgroundCS: ConstantState?,
      foregroundCS: ConstantState?,
      private val back: Bitmap?,
      private val upon: Bitmap?,
      private val mask: Bitmap?,
    ) : UnClipState(backgroundCS, foregroundCS) {
      override fun newDrawable() =
        CustomAdaptiveIconDrawable(
          backgroundCS?.newDrawable(),
          foregroundCS?.newDrawable(),
          back,
          upon,
          mask,
        )
    }
  }

  private class CustomBitmapDrawable(
    res: Resources,
    drawable: Drawable,
    back: Bitmap?,
    upon: Bitmap?,
    mask: Bitmap?,
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

  private class CustomDrawable(
    drawable: Drawable,
    private val back: Bitmap?,
    private val upon: Bitmap?,
    private val mask: Bitmap?,
  ) : DrawableWrapper(drawable) {
    private val paint =
      Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val cache = BitmapCache()

    override fun draw(canvas: Canvas) {
      cache
        .getBitmap(bounds) { drawIcon(paint, bounds, back, upon, mask) { super.draw(this) } }
        .let { canvas.drawBitmap(it, null, bounds, paint) }
    }

    override fun setAlpha(alpha: Int) {
      super.setAlpha(alpha)
      paint.alpha = alpha
    }

    val cState by lazy { drawable.constantState?.let { CState(it, back, upon, mask) } }

    override fun getConstantState(): ConstantState? = cState

    private class CState(
      private val cs: ConstantState,
      private val back: Bitmap?,
      private val upon: Bitmap?,
      private val mask: Bitmap?,
    ) : ConstantState() {
      override fun newDrawable(): Drawable = CustomDrawable(cs.newDrawable(), back, upon, mask)

      override fun getChangingConfigurations(): Int = cs.changingConfigurations
    }
  }

  fun processIcon(
    baseIcon: Drawable,
    res: Resources,
    back: Bitmap?,
    upon: Bitmap?,
    mask: Bitmap?,
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
        makeAdaptiveBack(res, backAsAdaptiveBack, back) {
          CustomDrawable(scale(baseIcon, iconScale), it, upon, mask)
        }
    else {
      val iconScale = iconScale * nonAdaptiveScale
      val makeIcon = { back: Bitmap? ->
        scale(baseIcon, iconScale).let {
          if (baseIcon is BitmapDrawable) CustomBitmapDrawable(res, it, back, upon, null)
          else CustomDrawable(it, back, upon, null)
        }
      }
      if (mask != null) makeAdaptiveBack(res, backAsAdaptiveBack, back, makeIcon)
      else {
        if (convertToAdaptive) makeAdaptiveBack(res, backAsAdaptiveBack, back, makeIcon)
        else makeIcon(back)
      }
    }

  private fun makeAdaptiveBack(
    res: Resources,
    backAsAdaptiveBack: Boolean,
    back: Bitmap?,
    makeBaseIcon: (Bitmap?) -> Drawable,
  ) =
    if (backAsAdaptiveBack && back != null)
      UnClipAdaptiveIconDrawable(scale(back.toDrawable(res)), scale(makeBaseIcon(null)))
    else UnClipAdaptiveIconDrawable(Color.TRANSPARENT.toDrawable(), scale(makeBaseIcon(back)))

  fun makeAdaptive(drawable: Drawable) =
    drawable as? AdaptiveIconDrawable
      ?: UnClipAdaptiveIconDrawable(Color.TRANSPARENT.toDrawable(), scale(drawable))

  fun Canvas.drawIcon(
    paint: Paint,
    bounds: Rect,
    back: Bitmap?,
    upon: Bitmap?,
    mask: Bitmap?,
    drawBaseIcon: Canvas.() -> Unit,
  ) {
    if (bounds.width() < 0) return
    if (bounds.height() < 0) return

    clipRect(bounds)
    drawBaseIcon()

    mask?.let {
      paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
      drawBitmap(it, null, bounds, paint)
    }

    upon?.let {
      paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
      drawBitmap(it, null, bounds, paint)
    }

    back?.let {
      paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
      drawBitmap(it, null, bounds, paint)
    }

    paint.xfermode = null
  }

  fun scale(drawable: Drawable, scale: Float = ADAPTIVE_ICON_VIEWPORT_SCALE): Drawable {
    if (scale == 1f) return drawable
    val h = drawable.intrinsicHeight.toFloat()
    val w = drawable.intrinsicWidth.toFloat()
    var scaleX = scale
    var scaleY = scale
    if (h > w && w > 0) {
      scaleX *= w / h
    } else if (w > h && h > 0) {
      scaleY *= h / w
    }
    scaleX = (1 - scaleX) / 2
    scaleY = (1 - scaleY) / 2
    return InsetDrawable(drawable, scaleX, scaleY, scaleX, scaleY)
  }
}
