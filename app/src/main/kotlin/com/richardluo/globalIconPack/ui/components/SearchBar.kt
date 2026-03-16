package com.richardluo.globalIconPack.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarDefaults.inputFieldColors
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.richardluo.globalIconPack.R
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundSearchBar(
  state: MutableState<String>,
  placeHolder: String,
  modifier: Modifier = Modifier,
  trailingIcon: (@Composable RowScope.() -> Unit)? = null,
  leadingIcon: @Composable () -> Unit,
) {
  val focusManager = LocalFocusManager.current
  val focusRequester = remember { FocusRequester() }
  SearchBarDefaults.InputField(
    query = state.value,
    onQueryChange = { state.value = it },
    onSearch = { focusManager.clearFocus() },
    expanded = true,
    onExpandedChange = {},
    placeholder = { Text(placeHolder) },
    leadingIcon = leadingIcon,
    trailingIcon = {
      Row {
        ClearIconButton(state) { focusRequester.requestFocus() }
        trailingIcon?.invoke(this)
      }
    },
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 12.dp)
        .focusRequester(focusRequester)
        .then(modifier),
    colors =
      inputFieldColors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
      ),
  )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WithSearch(
  state: MutableState<Boolean> = rememberSaveable { mutableStateOf(false) },
  searchText: MutableState<String>,
  placeHolder: String = stringResource(R.string.common_search),
  content: @Composable () -> Unit,
) {
  var expand by state

  AnimatedContent(expand, contentAlignment = Alignment.TopCenter) {
    if (!it) content()
    else
      Box(
        modifier =
          Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .height(TopAppBarDefaults.TopAppBarExpandedHeight),
        contentAlignment = Alignment.Center,
      ) {
        val focusRequester = remember { FocusRequester() }
        var initialized by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) {
          if (expand && !initialized) {
            focusRequester.requestFocus()
            initialized = true
          }
        }

        fun closeSearchBar() {
          expand = false
          searchText.value = ""
          initialized = false
        }

        BackHandler(onBack = ::closeSearchBar)

        val imeVisibleState = rememberUpdatedState(WindowInsets.isImeVisible)
        LaunchedEffect(Unit) {
          snapshotFlow { imeVisibleState.value }
            .dropWhile { !it }
            .first { !it && searchText.value.isEmpty() && expand }
          closeSearchBar()
        }

        var willBeFocused by remember { mutableStateOf(true) }

        RoundSearchBar(
          searchText,
          placeHolder,
          modifier =
            Modifier.focusRequester(focusRequester).onFocusChanged { state ->
              if (state.hasFocus) willBeFocused = false
              else if (!willBeFocused && searchText.value.isEmpty() && expand) closeSearchBar()
            },
        ) {
          IconButtonWithTooltip(
            Icons.AutoMirrored.Outlined.ArrowBack,
            "Back",
            IconButtonStyle.None,
            onClick = ::closeSearchBar,
          )
        }
      }
  }
}
