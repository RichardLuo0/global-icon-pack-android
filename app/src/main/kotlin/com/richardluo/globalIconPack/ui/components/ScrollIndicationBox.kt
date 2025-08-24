package com.richardluo.globalIconPack.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun <T : ScrollableState> ScrollIndicationBox(
  modifier: Modifier = Modifier,
  state: T,
  radius: Dp = 8.dp,
  content: @Composable BoxScope.(T) -> Unit,
) {
  Box(modifier = modifier) {
    content(state)

    val darkTheme = isSystemInDarkTheme()
    val shadowColors =
      remember(darkTheme) {
        val baseColor = if (darkTheme) Color.Black else Color.DarkGray
        listOf(baseColor.copy(alpha = 0.3f), baseColor.copy(alpha = 0.1f), Color.Transparent).let {
          it to it.reversed()
        }
      }

    val animatedTopShadow by
      animateFloatAsState(targetValue = if (state.canScrollBackward) 1f else 0f)
    val animatedBottomShadow by
      animateFloatAsState(targetValue = if (state.canScrollForward) 1f else 0f)

    Spacer(
      Modifier.fillMaxWidth()
        .align(Alignment.TopCenter)
        .height(radius * animatedTopShadow)
        .background(Brush.verticalGradient(colors = shadowColors.first))
    )

    Spacer(
      Modifier.fillMaxWidth()
        .align(Alignment.BottomCenter)
        .height(radius * animatedBottomShadow)
        .background(Brush.verticalGradient(colors = shadowColors.second))
    )
  }
}
