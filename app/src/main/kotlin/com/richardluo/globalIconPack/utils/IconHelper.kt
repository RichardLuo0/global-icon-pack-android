package com.richardluo.globalIconPack.utils

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
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
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
      background,
      foreground?.let { createScaledDrawable(it, iconScale) },
    ) {
    private val paint =
      Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun draw(canvas: Canvas) {
      drawIcon(canvas, paint, bounds, back, upon, mask) {
        // Use original mask if mask is not presented
        if (mask != null) super.draw(canvas) else super.drawClip(canvas)
      }
    }

    override fun setAlpha(alpha: Int) {
      super.setAlpha(alpha)
      paint.alpha = alpha
    }
  }

  interface Adaptively {
    fun makeAdaptive() = AdaptiveIconDrawable(null, createScaledDrawable(this as Drawable))
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
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
        drawIcon(canvas, paint, bounds, back, upon, mask) {
          canvas.drawBitmap(drawable.toBitmap(), null, bounds, paint)
        }
        bitmap
      },
    ),
    Adaptively

  private class CustomDrawable(
    drawable: Drawable,
    private val back: Bitmap?,
    private val upon: Bitmap?,
    private val mask: Bitmap?,
  ) : DrawableWrapper(drawable), Adaptively {
    private val paint =
      Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun draw(canvas: Canvas) {
      drawIcon(canvas, paint, bounds, back, upon, mask) { super.draw(canvas) }
    }
  }

  fun processIconToStatic(
    res: Resources,
    drawable: Drawable,
    back: Bitmap?,
    upon: Bitmap?,
    mask: Bitmap?,
    iconScale: Float = 1f,
  ): Drawable =
    (if (mask != null && drawable is AdaptiveIconDrawable)
        UnClipAdaptiveIconDrawable(
          drawable.background,
          createScaledDrawable(drawable.foreground, iconScale),
        )
      else createScaledDrawable(drawable, iconScale))
      .let {
        if (drawable is BitmapDrawable) CustomBitmapDrawable(res, it, back, upon, mask)
        else CustomDrawable(it, back, upon, mask)
      }

  fun processIconToAdaptive(
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
    else processIconToStatic(res, drawable, back, upon, null, iconScale)

  fun processIcon(
    res: Resources,
    drawable: Drawable,
    back: Bitmap?,
    upon: Bitmap?,
    mask: Bitmap?,
    iconScale: Float = 1f,
  ) =
    if (useUnClipAdaptive) processIconToAdaptive(res, drawable, back, upon, mask, iconScale)
    else processIconToStatic(res, drawable, back, upon, mask, iconScale)

  fun makeAdaptive(drawable: Drawable) =
    if (useUnClipAdaptive && drawable !is AdaptiveIconDrawable)
      UnClipAdaptiveIconDrawable(null, createScaledDrawable(drawable))
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

/**
 * UnClipAdaptiveIconDrawable does not work correctly for some apps. It maybe clipped by adaptive
 * icon mask or shows black background, but we don't know how to efficiently convert Bitmap to Path.
 */
private val useUnClipAdaptive: Boolean by lazy {
  if (!isInMod) false
  else
    when (val packageName = AndroidAppHelper.currentPackageName()) {
      "com.android.settings" -> false
      "com.android.systemui" -> false
      "com.android.intentresolver" -> true
      else -> {
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
