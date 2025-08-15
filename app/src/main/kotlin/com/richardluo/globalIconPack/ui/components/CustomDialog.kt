package com.richardluo.globalIconPack.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDialog(
  modifier: Modifier = Modifier,
  properties: DialogProperties = DialogProperties(),
  onDismissRequest: () -> Unit,
  title: (@Composable () -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
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
      Column(modifier = Modifier.padding(vertical = 12.dp)) {
        if (title != null)
          ProvideContentColorTextStyle(
            contentColor = AlertDialogDefaults.titleContentColor,
            textStyle = MaterialTheme.typography.headlineSmall,
          ) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
              title()
            }
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
  dismissible: Boolean = true,
  title: (@Composable () -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  if (!openState.value) return

  CustomDialog(
    modifier,
    properties,
    if (dismissible)
      fun() {
        openState.value = false
      }
    else fun() {},
    title,
    content,
  )
}

enum class ButtonType {
  Text,
  Filled,
  Outlined,
}

open class DialogButton(
  val name: String,
  val type: ButtonType = ButtonType.Text,
  val onClick: () -> Unit,
)

class CancelDialogButton(context: Context, onClick: () -> Unit) :
  DialogButton(context.getString(android.R.string.cancel), ButtonType.Outlined, onClick)

class OkDialogButton(context: Context, onClick: () -> Unit) :
  DialogButton(context.getString(android.R.string.ok), ButtonType.Filled, onClick)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DialogButtonRow(buttons: Array<DialogButton> = emptyArray()) {
  if (buttons.isEmpty()) return

  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    val buttonList = buttons.asList()
    val splitIndex = buttonList.size - 2
    buttonList.subList(0, splitIndex).forEach { DialogButton(it) }
    Spacer(modifier = Modifier.weight(1f))
    buttonList.subList(splitIndex, buttonList.size).forEach { DialogButton(it) }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DialogButton(button: DialogButton, shapes: ButtonShapes = ButtonDefaults.shapes()) {
  when (button.type) {
    ButtonType.Text ->
      TextButton(shapes = shapes, onClick = button.onClick) { Text(text = button.name) }
    ButtonType.Filled ->
      Button(shapes = shapes, onClick = button.onClick) { Text(text = button.name) }
    ButtonType.Outlined ->
      OutlinedButton(shapes = shapes, onClick = button.onClick) { Text(text = button.name) }
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
  title: (@Composable () -> Unit)? = null,
  initValue: String = "",
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  trailingIcon: @Composable ((MutableState<String>) -> Unit)? = null,
  onOk: (String) -> Unit,
) {
  CustomDialog(openState, title = title) {
    TextFieldDialogContent(
      initValue,
      keyboardOptions = keyboardOptions,
      trailingIcon = trailingIcon,
      onCancel = { openState.value = false },
      onOk = {
        onOk(it)
        openState.value = false
      },
    )
  }
}

@Composable
fun TextFieldDialogContent(
  initValue: String = "",
  textStyle: TextStyle = LocalTextStyle.current,
  placeholder: @Composable (() -> Unit)? = null,
  leadingIcon: @Composable ((MutableState<String>) -> Unit)? = null,
  trailingIcon: @Composable ((MutableState<String>) -> Unit)? = null,
  prefix: @Composable ((MutableState<String>) -> Unit)? = null,
  suffix: @Composable ((MutableState<String>) -> Unit)? = null,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  singleLine: Boolean = true,
  maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
  minLines: Int = 1,
  onCancel: () -> Unit,
  onOk: (String) -> Unit,
) {
  val state = remember {
    mutableStateOf(TextFieldValue(initValue, selection = TextRange(initValue.length)))
  }
  val textState = remember {
    object : MutableState<String> {
      override var value: String
        get() = state.value.text
        set(value) {
          state.value = TextFieldValue(value, selection = TextRange(value.length))
        }

      override fun component1() = value

      override fun component2(): (String) -> Unit = { value = it }
    }
  }
  val focusRequester = remember { FocusRequester() }

  OutlinedTextField(
    value = state.value,
    onValueChange = { state.value = it },
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 8.dp)
        .focusRequester(focusRequester),
    textStyle = textStyle,
    placeholder = placeholder,
    leadingIcon = leadingIcon?.let { { it(textState) } },
    trailingIcon = trailingIcon?.let { { it(textState) } },
    prefix = prefix?.let { { it(textState) } },
    suffix = suffix?.let { { it(textState) } },
    singleLine = singleLine,
    maxLines = maxLines,
    minLines = minLines,
    keyboardOptions = keyboardOptions.copy(imeAction = ImeAction.Done),
    keyboardActions = KeyboardActions { onOk(textState.value) },
  )

  LaunchedEffect(focusRequester) { focusRequester.requestFocus() }

  DialogButtonRow(
    arrayOf(
      CancelDialogButton(LocalContext.current) { onCancel() },
      OkDialogButton(LocalContext.current) { onOk(textState.value) },
    )
  )
}
