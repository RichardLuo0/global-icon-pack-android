package com.richardluo.globalIconPack.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.richardluo.globalIconPack.ui.viewModel.IconPackApp

@Composable
fun IconPackItem(pack: String, app: IconPackApp, selected: Boolean, onClick: () -> Unit) {
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 2.dp)
        .clip(MaterialTheme.shapes.extraLarge)
        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
        .selectable(selected, true, Role.RadioButton, onClick)
        .padding(horizontal = 8.dp, vertical = 10.dp)
  ) {
    IconPackItemContent(pack, app, selected)
  }
}

@Composable
fun IconPackItemContent(pack: String, app: IconPackApp, selected: Boolean = false) {
  Row(modifier = Modifier.height(IntrinsicSize.Min)) {
    Box(modifier = Modifier.fillMaxHeight().aspectRatio(1f)) {
      Image(
        bitmap = app.icon.toBitmap().asImageBitmap(),
        contentDescription = pack,
        modifier = Modifier.matchParentSize(),
        contentScale = ContentScale.Crop,
      )
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column {
      OneLineText(
        app.label,
        color =
          if (selected) MaterialTheme.colorScheme.onPrimary
          else MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyLarge,
      )
      OneLineText(
        pack,
        color =
          if (selected) MaterialTheme.colorScheme.onPrimary
          else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}
