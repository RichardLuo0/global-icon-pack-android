package com.richardluo.globalIconPack.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.richardluo.globalIconPack.R

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
    modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithSearch(
  state: MutableState<Boolean> = rememberSaveable { mutableStateOf(false) },
  searchText: MutableState<String>,
  placeHolder: String = stringResource(R.string.common_search),
  content: @Composable () -> Unit,
) {
  var expand by state

  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(expand) { if (expand) focusRequester.requestFocus() }

  AnimatedContent(
    expand,
    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
    contentAlignment = Alignment.TopCenter,
  ) {
    if (!it) content()
    else
      Box(
        modifier =
          Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .height(TopAppBarDefaults.TopAppBarExpandedHeight)
      ) {
        BackHandler {
          expand = false
          searchText.value = ""
        }
        RoundSearchBar(
          searchText,
          placeHolder,
          modifier = Modifier.focusRequester(focusRequester),
        ) {
          IconButtonWithTooltip(
            Icons.AutoMirrored.Outlined.ArrowBack,
            "Back",
            IconButtonStyle.None,
          ) {
            expand = false
            searchText.value = ""
          }
        }
      }
  }
}
