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
import android.os.Build

private val mLayersBitmapF by lazy { AdaptiveIconDrawable::class.java.field("mLayersBitmap") }
private val mLayersShaderF by lazy { AdaptiveIconDrawable::class.java.field("mLayersShader") }
private val mCanvasF by lazy { AdaptiveIconDrawable::class.java.field("mCanvas") }
private val mPaintF by lazy { AdaptiveIconDrawable::class.java.field("mPaint") }
private val mMaskScaleOnly by lazy { AdaptiveIconDrawable::class.java.field("mMaskScaleOnly") }

open class UnClipAdaptiveIconDrawable(protected open val state: CState) :
  AdaptiveIconDrawable(state.background, state.foreground) {

  constructor(
    background: Drawable?,
    foreground: Drawable?,
  ) : this(CState(arrayOf(background, foreground)))

  override fun draw(canvas: Canvas) {
    draw(
      canvas,
      Path().apply {
        addRect(
          RectF(0f, 0f, bounds.width().toFloat(), bounds.height().toFloat()),
          Path.Direction.CW,
        )
      },
    )
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
      val tileMode =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Shader.TileMode.DECAL
        else Shader.TileMode.CLAMP
      val shader = BitmapShader(mLayersBitmap, tileMode, tileMode)
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

  override fun getConstantState() = state

  open class CState(protected val drawables: Array<Drawable?>) : ConstantState() {
    val background
      get() = drawables[0]

    val foreground
      get() = drawables[1]

    override fun newDrawable() = UnClipAdaptiveIconDrawable(CState(drawables.newDrawables()))

    override fun getChangingConfigurations(): Int = drawables.getChangingConfigurations()
  }
}
