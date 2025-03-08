package com.richardluo.globalIconPack.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.core.graphics.createBitmap

class BitmapCache {
  private var bitmap: Bitmap? = null

  fun getBitmap(bounds: Rect, draw: Canvas.() -> Unit) =
    if (
      bitmap == null ||
        bitmap!!.getWidth() != bounds.width() ||
        bitmap!!.getHeight() != bounds.height()
    ) {
      createBitmap(bounds.width(), bounds.height()).also {
        bitmap = it
        Canvas(it).apply {
          translate(-bounds.left.toFloat(), -bounds.top.toFloat())
          draw()
        }
      }
    } else bitmap!!
}
