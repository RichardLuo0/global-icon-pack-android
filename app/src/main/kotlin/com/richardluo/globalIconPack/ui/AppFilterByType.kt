package com.richardluo.globalIconPack.ui

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.ui.viewModel.FilterAppsVM.Type

@Composable
fun AppFilterByType(enabledState: MutableState<Boolean>, typeState: MutableState<Type>) {
  var type by typeState
  DropdownMenu(expanded = enabledState.value, onDismissRequest = { enabledState.value = false }) {
    Type.entries.forEach {
      DropdownMenuItem(
        leadingIcon = { RadioButton(type == it, onClick = null) },
        text = { Text(getLabelByType(it)) },
        onClick = {
          type = it
          enabledState.value = false
        },
      )
    }
  }
}

@Composable
fun getLabelByType(type: Type) =
  when (type) {
    Type.User -> stringResource(R.string.userApps)
    Type.System -> stringResource(R.string.systemApps)
    Type.Shortcut -> stringResource(R.string.shortcuts)
  }
