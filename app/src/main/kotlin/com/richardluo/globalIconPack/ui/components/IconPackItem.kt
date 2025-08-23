package com.richardluo.globalIconPack.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.drawable.toBitmap
import com.richardluo.globalIconPack.ui.repo.IconPackApp

@Composable
private fun IconPackIcon(app: IconPackApp) {
  Box(modifier = Modifier.fillMaxHeight().aspectRatio(1f)) {
    Image(
      bitmap = app.icon.toBitmap().asImageBitmap(),
      contentDescription = app.label,
      modifier = Modifier.matchParentSize(),
      contentScale = ContentScale.Crop,
    )
  }
}

@Composable
fun IconPackItem(
  pack: String,
  app: IconPackApp,
  selected: Boolean,
  shape: CornerBasedShape = listSingleItemShape,
  onClick: () -> Unit,
) {
  ListItem(
    { IconPackIcon(app) },
    { OneLineText(app.label) },
    { OneLineText(pack) },
    selected,
    shape,
    onClick,
  )
}

@Composable
fun IconPackItemContent(pack: String, app: IconPackApp, selected: Boolean = false) {
  ListItemContent(
    { IconPackIcon(app) },
    { OneLineText(app.label) },
    { OneLineText(pack) },
    selected,
  )
}
