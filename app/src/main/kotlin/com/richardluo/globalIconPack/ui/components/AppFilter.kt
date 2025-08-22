package com.richardluo.globalIconPack.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.ui.viewModel.IAppsFilter.Type

@Composable
fun AppFilterMenu(enabledState: MutableState<Boolean>, typeState: MutableState<Type>) {
  var type by typeState
  MyDropdownMenu(expanded = enabledState.value, onDismissRequest = { enabledState.value = false }) {
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
    Type.User -> stringResource(R.string.icons_filter_user)
    Type.System -> stringResource(R.string.icons_filter_system)
  }

private class AppTypeButton(val icon: ImageVector, val label: String, val type: Type)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppFilterButtonGroup(modifier: Modifier = Modifier, typeState: MutableState<Type>) {
  val buttons =
    arrayOf(
      AppTypeButton(
        Icons.Outlined.InstallMobile,
        stringResource(R.string.icons_filter_user),
        Type.User,
      ),
      AppTypeButton(
        Icons.Outlined.Android,
        stringResource(R.string.icons_filter_system),
        Type.System,
      ),
    )

  var type by typeState
  ButtonGroup(overflowIndicator = { menuState -> }, modifier = modifier) {
    buttons.forEach { button ->
      val checked = type == button.type
      toggleableItem(
        checked,
        button.label,
        { type = button.type },
        { if (checked) Icon(button.icon, button.label) },
        weight = if (checked) 1.3f else 1f,
      )
    }
  }
}
