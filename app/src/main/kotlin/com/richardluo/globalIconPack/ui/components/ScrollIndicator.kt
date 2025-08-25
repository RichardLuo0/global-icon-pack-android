package com.richardluo.globalIconPack.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection.Ltr

@Composable
fun <T : ScrollableState> ScrollIndicator(
  modifier: Modifier = Modifier,
  state: T,
  radius: Float = 12f,
  shape: CornerBasedShape =
    RoundedCornerShape(CornerSize(0), CornerSize(0), CornerSize(0), CornerSize(0)),
  content: @Composable BoxScope.(T) -> Unit,
) {
  val darkTheme = isSystemInDarkTheme()
  val offset = 4f
  val paint =
    remember(darkTheme) {
      Paint().apply {
        color = if (darkTheme) Color.Black else Color.DarkGray
        style = PaintingStyle.Stroke
        asFrameworkPaint().apply { maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL) }
      }
    }

  val animatedTopShadow by
    animateFloatAsState(targetValue = if (state.canScrollBackward) 1f else 0f)
  val animatedBottomShadow by
    animateFloatAsState(targetValue = if (state.canScrollForward) 1f else 0f)

  Box(
    modifier =
      modifier.fillMaxWidth().clip(shape).drawWithCache {
        val density = Density(density, fontScale)
        var topStart = shape.topStart.toPx(size, density)
        var topEnd = shape.topEnd.toPx(size, density)
        var bottomEnd = shape.bottomEnd.toPx(size, density)
        var bottomStart = shape.bottomStart.toPx(size, density)
        val minDimension = size.minDimension
        if (topStart + bottomStart > minDimension) {
          val scale = minDimension / (topStart + bottomStart)
          topStart *= scale
          bottomStart *= scale
        }
        if (topEnd + bottomEnd > minDimension) {
          val scale = minDimension / (topEnd + bottomEnd)
          topEnd *= scale
          bottomEnd *= scale
        }

        val topShadowPath =
          Path().apply {
            val topLeft = if (layoutDirection == Ltr) topStart else topEnd
            val topRight = if (layoutDirection == Ltr) topEnd else topStart
            arcTo(
              rect = Rect(left = 0f, top = 0f, right = 2 * topLeft, bottom = 2 * topLeft),
              startAngleDegrees = 180f,
              sweepAngleDegrees = 90f,
              forceMoveTo = true,
            )
            lineTo(size.width - topRight, 0f)
            arcTo(
              rect =
                Rect(
                  left = size.width - 2 * topRight,
                  top = 0f,
                  right = size.width,
                  bottom = 2 * topRight,
                ),
              startAngleDegrees = 270f,
              sweepAngleDegrees = 90f,
              forceMoveTo = false,
            )
          }
        val bottomShadowPath =
          Path().apply {
            val bottomLeft = if (layoutDirection == Ltr) bottomStart else bottomEnd
            val bottomRight = if (layoutDirection == Ltr) bottomEnd else bottomStart
            arcTo(
              rect =
                Rect(
                  left = 0f,
                  top = size.height - 2 * bottomLeft,
                  right = 2 * bottomLeft,
                  bottom = size.height,
                ),
              startAngleDegrees = 180f,
              sweepAngleDegrees = -90f,
              forceMoveTo = true,
            )
            lineTo(size.width - bottomRight, size.height)
            arcTo(
              rect =
                Rect(
                  left = size.width - 2 * bottomRight,
                  top = size.height - 2 * bottomRight,
                  right = size.width,
                  bottom = size.height,
                ),
              startAngleDegrees = 90f,
              sweepAngleDegrees = -90f,
              forceMoveTo = false,
            )
          }

        onDrawWithContent {
          drawContent()
          drawIntoCanvas {
            it.run {
              drawPath(topShadowPath, paint.apply { strokeWidth = offset * animatedTopShadow })
              drawPath(
                bottomShadowPath,
                paint.apply { strokeWidth = offset * animatedBottomShadow },
              )
            }
          }
        }
      }
  ) {
    content(state)
  }
}
