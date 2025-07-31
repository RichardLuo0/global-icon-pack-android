package com.richardluo.globalIconPack.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope.Companion.DefaultFilterQuality
import androidx.compose.ui.layout.ContentScale

@Composable
fun LazyImage(
  key: Any?,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  filterQuality: FilterQuality = DefaultFilterQuality,
  loadImage: suspend () -> ImageBitmap,
) {
  var image by remember { mutableStateOf<ImageBitmap?>(null) }
  LaunchedEffect(key) {
    image = null
    image = loadImage()
  }
  AnimatedContent(targetState = image, modifier = modifier, label = "Image loaded") { targetImage ->
    if (targetImage != null)
      Image(
        bitmap = targetImage,
        contentDescription = contentDescription,
        modifier = Modifier.fillMaxSize(),
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        filterQuality = filterQuality,
      )
    else Box(modifier = Modifier.fillMaxSize().shimmer())
  }
}

@Composable
fun Modifier.shimmer(): Modifier {
  val transition = rememberInfiniteTransition()
  val translateAnimation by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 400f,
      animationSpec =
        infiniteRepeatable(
          tween(durationMillis = 1500, easing = LinearOutSlowInEasing),
          RepeatMode.Restart,
        ),
    )
  val brush =
    Brush.linearGradient(
      colors = listOf(Color.LightGray.copy(alpha = 0.8f), Color.LightGray.copy(alpha = 0.4f)),
      start = Offset(translateAnimation, translateAnimation),
      end = Offset(translateAnimation + 100f, translateAnimation + 100f),
      tileMode = TileMode.Mirror,
    )

  return drawBehind { drawRoundRect(brush, cornerRadius = CornerRadius(12f, 12f)) }
}
