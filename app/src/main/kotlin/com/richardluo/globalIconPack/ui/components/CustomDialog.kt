package com.richardluo.globalIconPack.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDialog(
  modifier: Modifier = Modifier,
  properties: DialogProperties = DialogProperties(),
  title: @Composable () -> Unit,
  onDismissRequest: () -> Unit,
  content: @Composable () -> Unit,
) {
  BasicAlertDialog(
    modifier = modifier.heightIn(max = 600.dp),
    onDismissRequest = onDismissRequest,
    properties = properties,
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = AlertDialogDefaults.shape,
      color = AlertDialogDefaults.containerColor,
      tonalElevation = AlertDialogDefaults.TonalElevation,
    ) {
      Column(modifier = Modifier.padding(vertical = 24.dp)) {
        ProvideContentColorTextStyle(
          contentColor = AlertDialogDefaults.titleContentColor,
          textStyle = MaterialTheme.typography.headlineSmall,
        ) {
          Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) { title() }
        }
        ProvideContentColorTextStyle(
          contentColor = AlertDialogDefaults.textContentColor,
          textStyle = MaterialTheme.typography.labelLarge,
        ) {
          content()
        }
      }
    }
  }
}

@Composable
fun CustomDialog(
  openState: MutableState<Boolean>,
  modifier: Modifier = Modifier,
  properties: DialogProperties = DialogProperties(),
  title: @Composable () -> Unit,
  dismissible: Boolean = true,
  content: @Composable () -> Unit,
) {
  if (!openState.value) return

  CustomDialog(
    modifier,
    properties,
    title,
    if (dismissible)
      fun() {
        openState.value = false
      }
    else fun() {},
    content,
  )
}

class DialogButton(val name: String, val onClick: () -> Unit)

@Composable
fun DialogButtonRow(buttons: Array<DialogButton> = emptyArray()) {
  if (buttons.isEmpty()) return

  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    horizontalArrangement = Arrangement.End,
  ) {
    buttons.forEach { TextButton(onClick = it.onClick) { Text(text = it.name) } }
  }
}

@Composable
fun ProvideContentColorTextStyle(
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

@Composable
fun TextFieldDialog(
  openState: MutableState<Boolean>,
  title: @Composable () -> Unit,
  initValue: String = "",
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  trailingIcon: @Composable ((MutableState<String>) -> Unit)? = null,
  onOk: (String) -> Unit,
) {
  CustomDialog(openState, title = title) {
    TextFieldDialogContent(
      initValue,
      keyboardOptions,
      trailingIcon,
      { openState.value = false },
      onOk,
    )
  }
}

@Composable
fun TextFieldDialogContent(
  initValue: String = "",
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  trailingIcon: @Composable ((MutableState<String>) -> Unit)? = null,
  dismiss: () -> Unit,
  onOk: (String) -> Unit,
) {
  val state = rememberSaveable { mutableStateOf(initValue) }
  val focusRequester = remember { FocusRequester() }
  OutlinedTextField(
    value = state.value,
    onValueChange = { state.value = it },
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 16.dp)
        .focusRequester(focusRequester),
    singleLine = true,
    keyboardOptions = keyboardOptions.copy(imeAction = ImeAction.Done),
    keyboardActions =
      KeyboardActions {
        onOk(state.value)
        dismiss()
      },
    trailingIcon = trailingIcon?.let { { it(state) } },
  )
  LaunchedEffect(focusRequester) { focusRequester.requestFocus() }

  DialogButtonRow(
    arrayOf(
      DialogButton(stringResource(android.R.string.cancel)) { dismiss() },
      DialogButton(stringResource(android.R.string.ok)) {
        onOk(state.value)
        dismiss()
      },
    )
  )
}
