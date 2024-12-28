package com.richardluo.globalIconPack.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoDialog(
  openState: MutableState<Boolean> = remember { mutableStateOf(true) },
  title: @Composable () -> Unit,
  content: @Composable () -> Unit,
  onOk: () -> Unit = {},
) {
  var open by openState
  if (!open) return

  BasicAlertDialog(onDismissRequest = { open = false }) {
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = AlertDialogDefaults.shape,
      color = AlertDialogDefaults.containerColor,
      tonalElevation = AlertDialogDefaults.TonalElevation,
    ) {
      Column {
        ProvideContentColorTextStyle(
          contentColor = AlertDialogDefaults.titleContentColor,
          textStyle = MaterialTheme.typography.headlineSmall,
        ) {
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp)
          ) {
            title()
          }
        }
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) { content() }
        ProvideContentColorTextStyle(
          contentColor = MaterialTheme.colorScheme.primary,
          textStyle = MaterialTheme.typography.labelLarge,
        ) {
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 24.dp),
            contentAlignment = Alignment.CenterEnd,
          ) {
            TextButton(
              onClick = {
                onOk()
                open = false
              }
            ) {
              Text(text = stringResource(android.R.string.ok))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ProvideContentColorTextStyle(
  contentColor: Color,
  textStyle: TextStyle,
  content: @Composable () -> Unit,
) {
  val mergedStyle = LocalTextStyle.current.merge(textStyle)
  CompositionLocalProvider(
    LocalContentColor provides contentColor,
    LocalTextStyle provides mergedStyle,
    content = content,
  )
}
