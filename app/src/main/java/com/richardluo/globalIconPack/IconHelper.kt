package com.richardluo.globalIconPack

import android.app.AndroidAppHelper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.AdaptiveIconDrawable.getExtraInsetFraction
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import androidx.core.graphics.drawable.toBitmap

object IconHelper {
  /**
   * CustomAdaptiveIconDrawable only works correctly for launcher and system ui. Otherwise it maybe
   * clipped by adaptive icon mask, but we don't know how to efficiently convert Bitmap to Path.
   * Also there will be a black background in settings app list. I don't know why.
   */
  class CustomAdaptiveIconDrawable(
    background: Drawable?,
    foreground: Drawable?,
    private val back: Bitmap?,
    private val upon: Bitmap?,
    private val mask: Bitmap?,
    private val scale: Float = 1f,
  ) : UnClipAdaptiveIconDrawable(background, foreground) {
    private val paint =
      Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun draw(canvas: Canvas) {
      drawIcon(canvas, paint, bounds, back, upon, mask, scale) { super.draw(canvas) }
    }

    override fun setAlpha(alpha: Int) {
      super.setAlpha(alpha)
      paint.alpha = alpha
    }
  }

  private val isUseAdaptiveIcon: Boolean by lazy {
    when (val packageName = AndroidAppHelper.currentPackageName()) {
      "com.android.systemui" -> true
      "com.android.settings" -> false
      else -> {
        // Query if it is a launcher app
        val intent =
          Intent().apply {
            setPackage(packageName)
            setAction(Intent.ACTION_MAIN)
            addCategory(Intent.CATEGORY_HOME)
          }
        AndroidAppHelper.currentApplication()
          .packageManager
          .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
          .let { it?.activityInfo != null }
      }
    }
  }

  fun processIcon(
    res: Resources,
    drawable: Drawable,
    back: Bitmap?,
    upon: Bitmap?,
    mask: Bitmap?,
    scale: Float = 1f,
  ): Drawable =
    if (!isUseAdaptiveIcon) {
      val width = drawable.intrinsicWidth
      val height = drawable.intrinsicHeight
      val bounds = Rect(0, 0, width, height)
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bitmap)
      val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
      // Do not pass scale because BitmapDrawable will scale anyway
      drawIcon(canvas, paint, bounds, back, upon, mask) {
        val maskBmp = drawable.toBitmap()
        canvas.drawBitmap(maskBmp, null, bounds, paint)
      }
      BitmapDrawable(res, bitmap)
    } else if (drawable is AdaptiveIconDrawable)
      CustomAdaptiveIconDrawable(drawable.background, drawable.foreground, back, upon, mask, scale)
    else CustomAdaptiveIconDrawable(null, createScaledDrawable(drawable), back, upon, mask, scale)

  fun makeAdaptive(drawable: Drawable, scale: Float = 1f) =
    if (!isUseAdaptiveIcon || drawable is AdaptiveIconDrawable) drawable
    else
      CustomAdaptiveIconDrawable(
        null,
        createScaledDrawable(drawable, (1 - getExtraInsetFraction()) * scale),
        null,
        null,
        null,
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

    canvas.scale(scale, scale, bounds.width() / 2f, bounds.height() / 2f)
  }

  fun createScaledDrawable(main: Drawable, scale: Float = 1 - getExtraInsetFraction()): Drawable {
    val h = main.intrinsicHeight.toFloat()
    val w = main.intrinsicWidth.toFloat()
    var scaleX = scale
    var scaleY = scale
    if (h > w && w > 0) {
      scaleX *= w / h
    } else if (w > h && h > 0) {
      scaleY *= h / w
    }
    scaleX = (1 - scaleX) / 2
    scaleY = (1 - scaleY) / 2
    return InsetDrawable(main, scaleX, scaleY, scaleX, scaleY)
  }
}
