package com.richardluo.globalIconPack.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.richardluo.globalIconPack.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.AppbarSearchBar(
  expandSearchBar: MutableState<Boolean>,
  searchText: MutableState<String>,
  placeHolder: String = stringResource(R.string.search),
) {
  Box(
    modifier =
      Modifier.align(Alignment.BottomCenter).height(TopAppBarDefaults.TopAppBarExpandedHeight)
  ) {
    AnimatedVisibility(
      expandSearchBar.value,
      enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
      exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
      label = "Expand search bar",
      modifier = Modifier.align(Alignment.Center),
    ) {
      BackHandler {
        expandSearchBar.value = false
        searchText.value = ""
      }
      val focusRequester = remember { FocusRequester() }
      RoundSearchBar(searchText, placeHolder, modifier = Modifier.focusRequester(focusRequester)) {
        IconButton(
          onClick = {
            expandSearchBar.value = false
            searchText.value = ""
          }
        ) {
          Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
        }
      }
      LaunchedEffect(expandSearchBar.value) {
        if (expandSearchBar.value) focusRequester.requestFocus()
      }
    }
  }
}
