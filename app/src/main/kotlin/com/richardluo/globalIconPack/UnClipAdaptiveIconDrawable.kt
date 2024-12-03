package com.richardluo.globalIconPack

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.reflect.ReflectHelper

private val mLayersBitmapF by lazy {
  ReflectHelper.findField(AdaptiveIconDrawable::class.java, "mLayersBitmap")
}
private val mLayersShaderF by lazy {
  ReflectHelper.findField(AdaptiveIconDrawable::class.java, "mLayersShader")
}
private val mCanvasF by lazy {
  ReflectHelper.findField(AdaptiveIconDrawable::class.java, "mCanvas")
}
private val mPaintF by lazy { ReflectHelper.findField(AdaptiveIconDrawable::class.java, "mPaint") }

open class UnClipAdaptiveIconDrawable(background: Drawable?, foreground: Drawable?) :
  AdaptiveIconDrawable(background, foreground) {

  override fun draw(canvas: Canvas) {
    draw(canvas, Path().apply { addRect(RectF(bounds), Path.Direction.CW) })
  }

  protected fun draw(canvas: Canvas, path: Path) {
    val mLayersBitmap = mLayersBitmapF?.getAs<Bitmap>(this) ?: return
    val mCanvas = mCanvasF?.getAs<Canvas>(this) ?: return
    val mPaint = mPaintF?.getAs<Paint>(this) ?: return
    if (mLayersShaderF?.get(this) == null) {
      mCanvas.setBitmap(mLayersBitmap)
      background?.draw(mCanvas)
      foreground?.draw(mCanvas)
      BitmapShader(mLayersBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).let {
        mLayersShaderF?.set(this, it)
        mPaint.setShader(it)
      }
    }
    canvas.apply {
      translate(bounds.left.toFloat(), bounds.top.toFloat())
      drawPath(path, mPaint)
      translate(-bounds.left.toFloat(), -bounds.top.toFloat())
    }
  }

  protected fun drawClip(canvas: Canvas) {
    return super.draw(canvas)
  }

  protected fun getFullBoundsPath() = Path().apply { addRect(getFullBounds(), Path.Direction.CW) }

  protected fun getFullBounds() =
    RectF(bounds).apply {
      inset(-bounds.width() * getExtraInsetFraction(), -bounds.height() * getExtraInsetFraction())
    }
}
