package com.richardluo.globalIconPack.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ScrollIndicationBox(
  modifier: Modifier = Modifier,
  state: LazyListState = rememberLazyListState(),
  content: @Composable BoxScope.(LazyListState) -> Unit,
) {
  Box(modifier = modifier) {
    content(state)

    val topShadowColors = remember {
      listOf(Color.Black.copy(alpha = 0.12f), Color.Black.copy(alpha = 0.02f), Color.Transparent)
    }

    AnimatedVisibility(
      state.canScrollBackward,
      modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
    ) {
      Spacer(
        Modifier.fillMaxWidth()
          .height(8.dp)
          .background(Brush.verticalGradient(colors = topShadowColors))
      )
    }

    val bottomShadowColors = remember { topShadowColors.reversed() }

    AnimatedVisibility(
      state.canScrollForward,
      modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
    ) {
      Spacer(
        Modifier.fillMaxWidth()
          .height(8.dp)
          .background(Brush.verticalGradient(colors = bottomShadowColors))
      )
    }
  }
}
