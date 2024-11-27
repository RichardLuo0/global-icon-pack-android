package com.richardluo.globalIconPack

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt

object IconHelper {
  fun processIcon(
    res: Resources,
    baseIcon: Drawable,
    size: Int,
    back: Drawable?,
    upon: Drawable?,
    mask: Drawable?,
    scale: Float = 1f,
  ): Drawable {
    val width = if (size == 0) baseIcon.intrinsicWidth else size
    val height = if (size == 0) baseIcon.intrinsicHeight else size

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    val canvas = Canvas(bitmap)
    val paint = Paint()
    paint.isAntiAlias = true
    paint.isFilterBitmap = true
    paint.isDither = true

    var inBounds: Rect
    var outBounds: Rect

    val icon = baseIcon.toBitmap(width, height)

    inBounds = Rect(0, 0, icon.width, icon.height)
    outBounds =
      Rect(
        (bitmap.width * (1 - scale) * 0.5).roundToInt(),
        (bitmap.height * (1 - scale) * 0.5).roundToInt(),
        (bitmap.width - bitmap.width * (1 - scale) * 0.5).roundToInt(),
        (bitmap.height - bitmap.height * (1 - scale) * 0.5).roundToInt(),
      )
    canvas.drawBitmap(icon, inBounds, outBounds, paint)

    if (mask != null) {
      paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
      val maskBmp = mask.toBitmap(width, height)
      inBounds = Rect(0, 0, maskBmp.width, maskBmp.height)
      outBounds = Rect(0, 0, bitmap.width, bitmap.height)
      canvas.drawBitmap(maskBmp, inBounds, outBounds, paint)
    }
    if (upon != null) {
      paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
      val maskBmp = upon.toBitmap(width, height)
      inBounds = Rect(0, 0, maskBmp.width, maskBmp.height)
      outBounds = Rect(0, 0, bitmap.width, bitmap.height)
      canvas.drawBitmap(maskBmp, inBounds, outBounds, paint)
    }
    if (back != null) {
      paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
      val maskBmp = back.toBitmap(width, height)
      inBounds = Rect(0, 0, maskBmp.width, maskBmp.height)
      outBounds = Rect(0, 0, bitmap.width, bitmap.height)
      canvas.drawBitmap(maskBmp, inBounds, outBounds, paint)
    }

    return BitmapDrawable(res, bitmap)
  }
}
