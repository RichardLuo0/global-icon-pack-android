package com.richardluo.globalIconPack.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun RoundSearchBar(
  state: MutableState<String>,
  placeHolder: String,
  modifier: Modifier = Modifier,
  trailingIcon: (@Composable () -> Unit)? = null,
  leadingIcon: @Composable () -> Unit,
) {
  val keyboardController = LocalSoftwareKeyboardController.current
  TextField(
    value = state.value,
    onValueChange = { state.value = it },
    placeholder = { Text(placeHolder) },
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    singleLine = true,
    shape = MaterialTheme.shapes.extraLarge,
    modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
    colors =
      TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
      ),
    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
  )
}
