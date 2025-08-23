package com.richardluo.globalIconPack.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.ui.viewModel.IAppsFilter.Type

private class AppTypeButton(val icon: ImageVector, val label: String, val type: Type)

val appFilterHeight =
  ButtonDefaults.ContentPadding.calculateTopPadding() +
    ButtonDefaults.MinHeight +
    ButtonDefaults.ContentPadding.calculateBottomPadding()

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
        if (checked) 1.3f else 1f,
      )
    }
  }
}
