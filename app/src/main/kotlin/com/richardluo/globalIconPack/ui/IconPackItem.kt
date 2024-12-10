package com.richardluo.globalIconPack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.richardluo.globalIconPack.iconPack.IconPackApp

@Composable
fun IconPackItem(
  value: String,
  currentValue: String,
  valueMap: Map<String, IconPackApp>,
  onClick: () -> Unit,
) {
  val selected = value == currentValue
  val iconPack = valueMap[value]
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .height(IntrinsicSize.Min)
        .selectable(selected, true, Role.RadioButton, onClick)
        .padding(horizontal = 24.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = null)
    Spacer(modifier = Modifier.width(12.dp))
    Box(modifier = Modifier.fillMaxHeight().aspectRatio(1f)) {
      iconPack?.icon?.let {
        Image(
          bitmap = it.toBitmap().asImageBitmap(),
          contentDescription = value,
          modifier = Modifier.matchParentSize(),
          contentScale = ContentScale.Crop,
        )
      }
        ?: Image(
          painter = painterResource(android.R.drawable.sym_def_app_icon),
          contentDescription = value,
          modifier = Modifier.matchParentSize(),
          contentScale = ContentScale.Crop,
        )
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.fillMaxWidth()) {
      Text(
        iconPack?.label ?: "Unknown label",
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        value,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
