package com.richardluo.globalIconPack.utils

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import androidx.core.graphics.drawable.toBitmap
import com.richardluo.globalIconPack.UnClipAdaptiveIconDrawable

object IconHelper {
  val ADAPTIVE_ICON_VIEWPORT_SCALE by lazy {
    1 / (1 + 2 * AdaptiveIconDrawable.getExtraInsetFraction())
  }

  class CustomAdaptiveIconDrawable(
    background: Drawable?,
    foreground: Drawable?,
    private val back: Bitmap?,
    private val upon: Bitmap?,
    private val mask: Bitmap?,
    private val forceClip: Boolean = false,
    private val iconScale: Float,
    private val scale: Float,
  ) :
    UnClipAdaptiveIconDrawable(
      background?.let { createScaledDrawable(it, iconScale) },
      foreground?.let { createScaledDrawable(it, iconScale) },
    ) {
    private val paint =
      Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun draw(canvas: Canvas) {
      drawIcon(canvas, paint, bounds, back, upon, mask, scale) {
        if (forceClip) {
          // Use original mask if mask is not presented
          if (mask != null) super.draw(canvas)
          else {
            getMask()
              ?.apply {
                val matrix = Matrix()
                matrix.setScale(iconScale, iconScale, bounds.width() / 2f, bounds.height() / 2f)
                transform(matrix)
              }
              ?.let { super.draw(canvas, it) } ?: super.drawClip(canvas)
          }
        } else super.draw(canvas)
      }
    }

    override fun setAlpha(alpha: Int) {
      super.setAlpha(alpha)
      paint.alpha = alpha
    }
  }

  // For marking only
  class ProcessedBitmapDrawable(res: Resources, bitmap: Bitmap) : BitmapDrawable(res, bitmap)

  fun processIconToBitmap(
    res: Resources,
    drawable: Drawable,
    back: Bitmap?,
    upon: Bitmap?,
    mask: Bitmap?,
    iconScale: Float = 1f,
    scale: Float = 1f,
  ): BitmapDrawable {
    val width = drawable.intrinsicWidth
    val height = drawable.intrinsicHeight
    val bounds = Rect(0, 0, width, height)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
    drawIcon(canvas, paint, bounds, back, upon, mask, scale) {
      val scaledDrawable = createScaledDrawable(drawable, iconScale)
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
    scale: Float = 1f,
  ): Drawable =
    if (drawable is AdaptiveIconDrawable)
      CustomAdaptiveIconDrawable(
        drawable.background,
        drawable.foreground,
        back,
        upon,
        mask,
        true,
        iconScale,
        scale,
      )
    else if (mask != null)
      CustomAdaptiveIconDrawable(
        null,
        createScaledDrawable(drawable),
        back,
        upon,
        mask,
        true,
        iconScale,
        scale,
      )
    else {
      // Let app handle the rest
      processIconToBitmap(res, drawable, back, upon, null, iconScale, scale)
    }

  fun makeAdaptive(drawable: Drawable, scale: Float = 1f) =
    if (drawable is AdaptiveIconDrawable)
      if (scale == 1f) drawable
      else
        CustomAdaptiveIconDrawable(
          drawable.background,
          drawable.foreground,
          null,
          null,
          null,
          true,
          1f,
          scale,
        )
    else
      UnClipAdaptiveIconDrawable(
        null,
        createScaledDrawable(drawable, ADAPTIVE_ICON_VIEWPORT_SCALE * scale),
      )

  fun drawIcon(
    canvas: Canvas,
    paint: Paint,
    bounds: Rect,
    back: Bitmap?,
    upon: Bitmap?,
    mask: Bitmap?,
    scale: Float = 1f,
    drawBaseIcon: () -> Unit,
  ) {
    if (bounds.width() < 0) return
    if (bounds.height() < 0) return

    canvas.clipRect(bounds)
    canvas.scale(scale, scale, bounds.exactCenterX(), bounds.exactCenterY())

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
