package com.richardluo.globalIconPack.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun IconForApp(
  label: String,
  key: Any? = label,
  loadImage: suspend () -> ImageBitmap,
  onClick: () -> Unit,
) {
  Column(
    modifier =
      Modifier.clip(MaterialTheme.shapes.medium)
        .clickable { onClick() }
        .fillMaxWidth()
        .padding(vertical = 18.dp, horizontal = 4.dp)
  ) {
    LazyImage(
      key,
      contentDescription = label,
      modifier = Modifier.padding(horizontal = 12.dp).aspectRatio(1f),
      contentScale = ContentScale.Crop,
      loadImage = loadImage,
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
      label,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodyMedium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}
