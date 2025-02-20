package com.richardluo.globalIconPack.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect

class BitmapCache {
  private var bitmap: Bitmap? = null

  fun getBitmap(bounds: Rect, draw: Canvas.() -> Unit) =
    if (
      bitmap == null ||
        bitmap!!.getWidth() != bounds.width() ||
        bitmap!!.getHeight() != bounds.height()
    ) {
      Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888).also {
        bitmap = it
        Canvas(it).apply {
          translate(-bounds.left.toFloat(), -bounds.top.toFloat())
          draw()
        }
      }
    } else bitmap!!
}
