package com.richardluo.globalIconPack.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import com.richardluo.globalIconPack.utils.chain

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
  label: String,
  key: Any? = label,
  loadImage: suspend () -> ImageBitmap,
  shareKey: Any? = null,
  onLongClick: (() -> Unit)? = null,
  onClick: () -> Unit,
) {
  Column(
    modifier =
      Modifier.clip(MaterialTheme.shapes.medium)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .fillMaxWidth()
        .padding(vertical = 8.dp, horizontal = 4.dp)
  ) {
    LazyImage(
      key,
      contentDescription = label,
      modifier =
        Modifier.padding(horizontal = 12.dp).aspectRatio(1f).chain {
          shareKey?.let { sharedBounds("AppIcon/$it") }
        },
      contentScale = ContentScale.Crop,
      loadImage = loadImage,
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
      label,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodyMedium,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().chain { shareKey?.let { sharedBounds("AppLabel/$it") } },
    )
  }
}
