package com.richardluo.globalIconPack.utils

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import androidx.core.graphics.drawable.toBitmap

object IconHelper {
  val ADAPTIVE_ICON_VIEWPORT_SCALE = 1 / (1 + 2 * AdaptiveIconDrawable.getExtraInsetFraction())

  class CustomAdaptiveIconDrawable(
    background: Drawable?,
    foreground: Drawable?,
    private val back: Bitmap?,
    private val upon: Bitmap?,
    private val mask: Bitmap?,
    private val iconScale: Float,
  ) :
    UnClipAdaptiveIconDrawable(
      background?.let { createScaledDrawable(it, iconScale) },
      foreground?.let { createScaledDrawable(it, iconScale) },
    ) {
    private val paint =
      Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun draw(canvas: Canvas) {
      drawIcon(canvas, paint, bounds, back, upon, mask) {
        // Use original mask if mask is not presented
        if (mask != null) super.draw(canvas)
        else getMask()?.let { super.draw(canvas, it) } ?: super.drawClip(canvas)
      }
    }

    override fun setAlpha(alpha: Int) {
      super.setAlpha(alpha)
      paint.alpha = alpha
    }
  }

  class ProcessedBitmapDrawable(res: Resources, bitmap: Bitmap) : BitmapDrawable(res, bitmap) {

    fun makeAdaptive() = AdaptiveIconDrawable(null, createScaledDrawable(this))
  }

  fun processIconToBitmap(
    res: Resources,
    drawable: Drawable,
    back: Bitmap?,
    upon: Bitmap?,
    mask: Bitmap?,
    iconScale: Float = 1f,
  ): BitmapDrawable {
    val width = drawable.intrinsicWidth
    val height = drawable.intrinsicHeight
    val bounds = Rect(0, 0, width, height)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
    drawIcon(canvas, paint, bounds, back, upon, mask) {
      val scaledDrawable =
        if (mask != null && drawable is AdaptiveIconDrawable)
          UnClipAdaptiveIconDrawable(
            drawable.background,
            createScaledDrawable(drawable.foreground, iconScale),
          )
        else createScaledDrawable(drawable, iconScale)
      canvas.drawBitmap(scaledDrawable.toBitmap(), null, bounds, paint)
    }
    return ProcessedBitmapDrawable(res, bitmap)
  }

  fun processIcon(
    res: Resources,
    drawable: Drawable,
    back: Bitmap?,
    upon: Bitmap?,
    mask: Bitmap?,
    iconScale: Float = 1f,
  ): Drawable =
    if (drawable is AdaptiveIconDrawable)
      CustomAdaptiveIconDrawable(
        drawable.background,
        drawable.foreground,
        back,
        upon,
        mask,
        iconScale,
      )
    else if (mask != null)
      CustomAdaptiveIconDrawable(null, createScaledDrawable(drawable), back, upon, mask, iconScale)
    else processIconToBitmap(res, drawable, back, upon, null, iconScale)

  fun makeAdaptive(drawable: Drawable) =
    if (drawable !is AdaptiveIconDrawable)
      UnClipAdaptiveIconDrawable(null, createScaledDrawable(drawable, ADAPTIVE_ICON_VIEWPORT_SCALE))
    else drawable

  fun drawIcon(
    canvas: Canvas,
    paint: Paint,
    bounds: Rect,
    back: Bitmap?,
    upon: Bitmap?,
    mask: Bitmap?,
    drawBaseIcon: () -> Unit,
  ) {
    if (bounds.width() < 0) return
    if (bounds.height() < 0) return

    canvas.clipRect(bounds)
    drawBaseIcon()

    mask?.let {
      paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
      canvas.drawBitmap(it, null, bounds, paint)
    }

    upon?.let {
      paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
      canvas.drawBitmap(it, null, bounds, paint)
    }

    back?.let {
      paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
      canvas.drawBitmap(it, null, bounds, paint)
    }
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
