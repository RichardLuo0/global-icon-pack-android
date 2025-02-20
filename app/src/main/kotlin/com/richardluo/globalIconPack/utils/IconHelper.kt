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
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.InsetDrawable
import androidx.core.graphics.drawable.toBitmap

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

    override val cState by lazy { CState() }

    private inner class CState : UnClipState() {
      override fun newDrawable() =
        CustomAdaptiveIconDrawable(newBackground(), newForeground(), back, upon, mask)
    }
  }

  interface Adaptively {
    fun makeAdaptive(): AdaptiveIconDrawable
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
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
          Canvas(it).drawIcon(paint, bounds, back, upon, mask) {
            drawBitmap(drawable.toBitmap(), null, bounds, paint)
          }
        }
      },
    ),
    Adaptively {
    private val hasMask = mask != null

    override fun makeAdaptive() =
      createScaledDrawable(this).let {
        if (hasMask) UnClipAdaptiveIconDrawable(null, it)
        else AdaptiveIconDrawable(ColorDrawable(Color.WHITE), it)
      }
  }

  private class CustomDrawable(
    drawable: Drawable,
    private val back: Bitmap?,
    private val upon: Bitmap?,
    private val mask: Bitmap?,
  ) : DrawableWrapper(drawable), Adaptively {
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

    override fun makeAdaptive() =
      createScaledDrawable(this).let {
        if (mask != null) UnClipAdaptiveIconDrawable(null, it)
        else AdaptiveIconDrawable(ColorDrawable(Color.WHITE), it)
      }
  }

  private fun processIconToStatic(
    res: Resources,
    drawable: Drawable,
    back: Bitmap?,
    upon: Bitmap?,
    mask: Bitmap?,
    iconScale: Float = 1f,
    scaleOnlyForeground: Boolean = true,
  ): Drawable =
    (if (scaleOnlyForeground && iconScale != 1f && drawable is AdaptiveIconDrawable)
        UnClipAdaptiveIconDrawable(
          drawable.background,
          createScaledDrawable(drawable.foreground, iconScale),
        )
      else createScaledDrawable(drawable, iconScale))
      .let {
        if (drawable is BitmapDrawable) CustomBitmapDrawable(res, it, back, upon, mask)
        else CustomDrawable(it, back, upon, mask)
      }

  private fun processIconToAdaptive(
    res: Resources,
    drawable: Drawable,
    back: Bitmap?,
    upon: Bitmap?,
    mask: Bitmap?,
    iconScale: Float = 1f,
    scaleOnlyForeground: Boolean = true,
  ): Drawable =
    if (drawable is AdaptiveIconDrawable)
      CustomAdaptiveIconDrawable(
        drawable.background?.let {
          if (scaleOnlyForeground) it else createScaledDrawable(it, iconScale)
        },
        drawable.foreground?.let { createScaledDrawable(it, iconScale) },
        back,
        upon,
        mask,
      )
    else if (mask != null)
      CustomAdaptiveIconDrawable(
        ColorDrawable(Color.TRANSPARENT),
        createScaledDrawable(drawable, ADAPTIVE_ICON_VIEWPORT_SCALE * iconScale),
        back,
        upon,
        mask,
      )
    else processIconToStatic(res, drawable, back, upon, null, iconScale, scaleOnlyForeground)

  fun processIcon(
    res: Resources,
    drawable: Drawable,
    back: Bitmap?,
    upon: Bitmap?,
    mask: Bitmap?,
    iconScale: Float = 1f,
    scaleOnlyForeground: Boolean = true,
    static: Boolean = false,
  ) =
    if (static) processIconToStatic(res, drawable, back, upon, mask, iconScale, scaleOnlyForeground)
    else processIconToAdaptive(res, drawable, back, upon, mask, iconScale, scaleOnlyForeground)

  fun makeAdaptive(drawable: Drawable, static: Boolean = false) =
    if (!static && drawable !is AdaptiveIconDrawable)
      UnClipAdaptiveIconDrawable(ColorDrawable(Color.TRANSPARENT), createScaledDrawable(drawable))
    else drawable

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

  fun createScaledDrawable(
    drawable: Drawable,
    scale: Float = ADAPTIVE_ICON_VIEWPORT_SCALE,
  ): Drawable {
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
