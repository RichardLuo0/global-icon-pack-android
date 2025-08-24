package com.richardluo.globalIconPack.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.drawable.toBitmap
import com.richardluo.globalIconPack.ui.repo.IconPackApp

@Composable
private fun IconPackIcon(app: IconPackApp) {
  Image(
    bitmap = app.icon.toBitmap().asImageBitmap(),
    contentDescription = app.label,
    contentScale = ContentScale.Fit,
  )
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
    onClick = onClick,
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
