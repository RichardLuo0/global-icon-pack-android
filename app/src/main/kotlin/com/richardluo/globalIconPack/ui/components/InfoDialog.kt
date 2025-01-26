package com.richardluo.globalIconPack.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource

@Composable
inline fun InfoDialog(
  openState: MutableState<Boolean>,
  icon: ImageVector? = null,
  iconColor: Color? = null,
  noinline title: @Composable () -> Unit,
  noinline content: @Composable () -> Unit,
  crossinline onCancel: () -> Unit = {},
  crossinline onOk: () -> Unit = {},
) {
  var open by openState
  if (!open) return

  AlertDialog(
    icon =
      icon?.let {
        { Icon(it, tint = iconColor ?: LocalContentColor.current, contentDescription = "Icon") }
      },
    title = title,
    text = content,
    onDismissRequest = {
      onCancel()
      open = false
    },
    confirmButton = {
      TextButton(
        onClick = {
          onOk()
          open = false
        }
      ) {
        Text(text = stringResource(android.R.string.ok))
      }
    },
    dismissButton = {
      TextButton(
        onClick = {
          onCancel()
          open = false
        }
      ) {
        Text(text = stringResource(android.R.string.cancel))
      }
    },
  )
}

@Composable
inline fun WarnDialog(
  openState: MutableState<Boolean>,
  noinline title: @Composable () -> Unit,
  noinline content: @Composable () -> Unit,
  crossinline onCancel: () -> Unit = {},
  crossinline onOk: () -> Unit = {},
) {
  InfoDialog(
    openState,
    icon = Icons.Outlined.WarningAmber,
    iconColor = Color.Yellow,
    title,
    content,
    onCancel,
    onOk,
  )
}
