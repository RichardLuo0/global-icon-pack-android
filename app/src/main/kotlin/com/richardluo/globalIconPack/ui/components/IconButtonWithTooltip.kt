package com.richardluo.globalIconPack.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
    Crossfade(style) {
      when (it) {
        IconButtonStyle.None ->
          IconButton(onClick) { Icon(imageVector = icon, contentDescription = tooltip) }
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
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ClearIconButton(state: MutableState<String>) {
  AnimatedVisibility(
    state.value.isNotEmpty(),
    enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
  ) {
    IconButtonWithTooltip(Icons.Outlined.Clear, "Clear", IconButtonStyle.None) { state.value = "" }
  }
}
