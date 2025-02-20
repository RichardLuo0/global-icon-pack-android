package com.richardluo.globalIconPack.utils

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable

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
private val mMaskScaleOnly by lazy {
  ReflectHelper.findField(AdaptiveIconDrawable::class.java, "mMaskScaleOnly")
}

open class UnClipAdaptiveIconDrawable(background: Drawable?, foreground: Drawable?) :
  AdaptiveIconDrawable(background, foreground) {

  override fun draw(canvas: Canvas) {
    if (isInMod)
      draw(
        canvas,
        Path().apply {
          addRect(
            RectF(0f, 0f, bounds.width().toFloat(), bounds.height().toFloat()),
            Path.Direction.CW,
          )
        },
      )
    else {
      canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
      background?.draw(canvas)
      foreground?.draw(canvas)
      canvas.translate(-bounds.left.toFloat(), -bounds.top.toFloat())
    }
  }

  protected fun draw(canvas: Canvas, path: Path) {
    val mPaint = mPaintF?.getAs<Paint>(this) ?: return
    val mLayersShaderF = mLayersShaderF ?: return
    if (mLayersShaderF.get(this) == null) {
      val mLayersBitmap = mLayersBitmapF?.getAs<Bitmap>(this) ?: return
      val mCanvas = mCanvasF?.getAs<Canvas>(this) ?: return
      mCanvas.setBitmap(mLayersBitmap)
      background?.draw(mCanvas)
      foreground?.draw(mCanvas)
      val shader = BitmapShader(mLayersBitmap, Shader.TileMode.DECAL, Shader.TileMode.DECAL)
      mLayersShaderF.set(this, shader)
      mPaint.setShader(shader)
    }
    canvas.apply {
      translate(bounds.left.toFloat(), bounds.top.toFloat())
      drawPath(path, mPaint)
      translate(-bounds.left.toFloat(), -bounds.top.toFloat())
    }
  }

  protected fun drawClip(canvas: Canvas) = getMask()?.let { draw(canvas, it) }

  protected fun getMask() = mMaskScaleOnly?.getAs<Path?>(this)

  protected fun getFullBoundsPath() = Path().apply { addRect(getFullBounds(), Path.Direction.CW) }

  protected fun getFullBounds() =
    RectF(bounds).apply {
      inset(-bounds.width() * getExtraInsetFraction(), -bounds.height() * getExtraInsetFraction())
    }

  open val cState: ConstantState by lazy { UnClipState() }

  override fun getConstantState(): ConstantState = cState

  protected open inner class UnClipState : ConstantState() {
    override fun newDrawable() = UnClipAdaptiveIconDrawable(newBackground(), newForeground())

    override fun getChangingConfigurations(): Int = 0

    protected fun newBackground() = background.constantState?.newDrawable()

    protected fun newForeground() = foreground.constantState?.newDrawable()
  }
}
