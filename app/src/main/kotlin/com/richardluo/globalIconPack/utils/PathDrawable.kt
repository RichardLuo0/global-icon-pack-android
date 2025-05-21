package com.richardluo.globalIconPack.utils

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Path.FillType
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.PathParser

//  fill:
//  -1 inverse fill
//  0 no fill
//  1 fill
class PathDrawable(private val path: Path, color: Int, private val fill: Int = 1) : Drawable() {
  constructor(
    pathData: String,
    color: Int,
    fill: Int = 1,
  ) : this(
    Unit.runCatching { PathParser.createPathFromPathData(pathData) }
      .getOrElse {
        log(it)
        Path()
      },
    color,
    fill,
  )

  constructor(
    pathData: String,
    color: Color = Color.Black,
    fill: Int = 1,
  ) : this(pathData, color.toArgb(), fill)

  private val paint =
    Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
      setColor(color)
      style =
        when (fill) {
          0 -> Paint.Style.STROKE
          else -> Paint.Style.FILL
        }
      strokeWidth = 6f
    }

  init {
    path.fillType =
      when (fill) {
        -1 -> FillType.INVERSE_WINDING
        else -> FillType.WINDING
      }
  }

  private var pathScaled = Path()
  private var lastBounds: Rect = Rect()

  override fun draw(canvas: Canvas) {
    if (lastBounds != bounds) {
      val matrix = Matrix().apply { setScale(bounds.width() / 100f, bounds.height() / 100f) }
      path.transform(matrix, pathScaled)
      lastBounds = bounds
    }
    canvas.drawPath(pathScaled, paint)
  }

  override fun setAlpha(alpha: Int) {
    paint.alpha = alpha
    invalidateSelf()
  }

  override fun getAlpha() = paint.alpha

  override fun setColorFilter(colorFilter: ColorFilter?) {
    paint.colorFilter = colorFilter
    invalidateSelf()
  }

  override fun getColorFilter(): ColorFilter? = paint.colorFilter

  @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT

  private val cState by lazy { CState(path, paint.color, fill) }

  override fun getConstantState(): ConstantState? = cState

  private class CState(private val path: Path, private val color: Int, private val fill: Int) :
    ConstantState() {
    override fun newDrawable(): Drawable = PathDrawable(path, color, fill)

    override fun getChangingConfigurations(): Int = 0
  }
}

class DrawablePainter(private val drawable: Drawable) : Painter() {

  override val intrinsicSize: Size
    get() = Size(drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())

  override fun DrawScope.onDraw() {
    val canvas = drawContext.canvas.nativeCanvas
    val oldBounds = drawable.bounds
    drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
    drawable.draw(canvas)
    drawable.bounds = oldBounds
  }

  override fun applyAlpha(alpha: Float): Boolean {
    drawable.alpha = (alpha * 255).toInt()
    return true
  }

  override fun applyColorFilter(colorFilter: androidx.compose.ui.graphics.ColorFilter?): Boolean {
    drawable.colorFilter = colorFilter?.asAndroidColorFilter()
    return true
  }
}
