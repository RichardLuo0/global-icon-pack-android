package com.richardluo.globalIconPack.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

enum class IconButtonStyle {
  None,
  Outlined,
  Filled,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IconButtonWithTooltip(
  icon: ImageVector,
  tooltip: String,
  style: IconButtonStyle = IconButtonStyle.Filled,
  onClick: () -> Unit,
) {
  TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
    tooltip = { PlainTooltip { Text(tooltip) } },
    state = rememberTooltipState(isPersistent = false),
  ) {
    when (style) {
      IconButtonStyle.None ->
        IconButton(onClick, shapes = IconButtonDefaults.shapes()) {
          Icon(imageVector = icon, contentDescription = tooltip)
        }
      IconButtonStyle.Outlined ->
        OutlinedIconButton(onClick, shapes = IconButtonDefaults.shapes()) {
          Icon(imageVector = icon, contentDescription = tooltip)
        }
      IconButtonStyle.Filled ->
        FilledTonalIconButton(onClick, shapes = IconButtonDefaults.shapes()) {
          Icon(imageVector = icon, contentDescription = tooltip)
        }
    }
  }
}
