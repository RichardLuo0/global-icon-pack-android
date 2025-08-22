package com.richardluo.globalIconPack.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

enum class SnackbarType {
  Success,
  Error,
}

class CustomSnackbarVisuals(
  val type: SnackbarType = SnackbarType.Success,
  override val message: String,
  override val actionLabel: String? = null,
  override val withDismissAction: Boolean = false,
  override val duration: SnackbarDuration =
    if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Indefinite,
) : SnackbarVisuals

@Composable
fun CustomSnackbar(data: SnackbarData) {
  val visuals = data.visuals as? CustomSnackbarVisuals ?: return

  val actionLabel = data.visuals.actionLabel
  val actionComposable: (@Composable () -> Unit)? =
    if (actionLabel != null) {
      @Composable {
        TextButton(
          colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor),
          onClick = { data.performAction() },
          content = { Text(actionLabel) },
        )
      }
    } else {
      null
    }

  val dismissActionComposable: (@Composable () -> Unit)? =
    if (data.visuals.withDismissAction) {
      @Composable {
        IconButton(
          onClick = { data.dismiss() },
          content = { Icon(Icons.Outlined.Close, contentDescription = "Dismiss") },
        )
      }
    } else {
      null
    }

  when (visuals.type) {
    SnackbarType.Success ->
      Snackbar(
        modifier = Modifier.padding(12.dp),
        shape = MaterialTheme.shapes.extraLarge,
        action = actionComposable,
        dismissAction = dismissActionComposable,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Outlined.Check, "Success")
          Spacer(Modifier.width(8.dp))
          OneLineText(visuals.message)
        }
      }
    SnackbarType.Error ->
      Snackbar(
        modifier = Modifier.padding(12.dp),
        shape = MaterialTheme.shapes.extraLarge,
        action = actionComposable,
        dismissAction = dismissActionComposable,
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Outlined.Error, "Error")
          Spacer(Modifier.width(8.dp))
          OneLineText(visuals.message)
        }
      }
  }
}

class EmptySnackbarData(override val visuals: SnackbarVisuals) : SnackbarData {
  override fun performAction() {}

  override fun dismiss() {}
}

@Preview
@Composable
private fun CustomSnackbarPreview() {
  CustomSnackbar(
    EmptySnackbarData(
      CustomSnackbarVisuals(
        SnackbarType.Success,
        "Test",
        withDismissAction = true,
        duration = SnackbarDuration.Long,
      )
    )
  )
}

@Preview
@Composable
private fun CustomSnackbarErrorPreview() {
  CustomSnackbar(
    EmptySnackbarData(
      CustomSnackbarVisuals(
        SnackbarType.Error,
        "Test",
        withDismissAction = true,
        duration = SnackbarDuration.Long,
      )
    )
  )
}
