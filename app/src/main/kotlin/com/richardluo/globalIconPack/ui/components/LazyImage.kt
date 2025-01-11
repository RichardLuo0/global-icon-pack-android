package com.richardluo.globalIconPack.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope.Companion.DefaultFilterQuality
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.launch

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
  val coroutine = rememberCoroutineScope()
  var image by remember { mutableStateOf<ImageBitmap?>(null) }
  LaunchedEffect(key) {
    image = null
    coroutine.launch { image = loadImage() }
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
    else Box(modifier = Modifier.fillMaxSize())
  }
}
