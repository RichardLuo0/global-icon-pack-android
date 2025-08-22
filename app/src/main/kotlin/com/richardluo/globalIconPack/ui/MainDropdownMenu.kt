package com.richardluo.globalIconPack.ui

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.ui.components.CustomSnackbarVisuals
import com.richardluo.globalIconPack.ui.components.IconButtonWithTooltip
import com.richardluo.globalIconPack.ui.components.MyDropdownMenu
import com.richardluo.globalIconPack.ui.components.SnackbarType
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.log
import com.richardluo.globalIconPack.utils.msg
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.launch

@Composable
fun MainDropdownMenu(snackbarState: SnackbarHostState) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var expand by rememberSaveable { mutableStateOf(false) }

  val onShellResult = remember {
    Shell.ResultCallback { result ->
      scope.launch {
        if (result.isSuccess)
          snackbarState.showSnackbar(
            CustomSnackbarVisuals(
              SnackbarType.Success,
              context.getString(R.string.mainMenu_info_restarted),
              withDismissAction = true,
              duration = SnackbarDuration.Long,
            )
          )
        else {
          val error = result.msg
          log(error)
          snackbarState.showSnackbar(
            CustomSnackbarVisuals(
              SnackbarType.Error,
              error,
              withDismissAction = true,
              duration = SnackbarDuration.Long,
            )
          )
        }
      }
    }
  }

  fun runCommand(vararg cmd: String) {
    Shell.cmd("set -e", *cmd).submit(onShellResult)
    expand = false
  }

  IconButtonWithTooltip(Icons.Outlined.MoreVert, stringResource(R.string.common_moreOptions)) {
    expand = !expand
  }
  MyDropdownMenu(expanded = expand, onDismissRequest = { expand = false }) {
    DropdownMenuItem(
      leadingIcon = {},
      text = { Text(stringResource(R.string.mainMenu_restartSystemUI)) },
      onClick = { runCommand("killall com.android.systemui") },
    )
    DropdownMenuItem(
      leadingIcon = {},
      text = { Text(stringResource(R.string.mainMenu_restartLauncher)) },
      onClick = {
        val launcher = WorldPreference.get().get(Pref.PIXEL_LAUNCHER_PACKAGE)
        runCommand("rm -f /data/data/$launcher/databases/app_icons.db && am force-stop $launcher")
      },
    )
    DropdownMenuItem(
      leadingIcon = {},
      text = { Text(stringResource(R.string.mainMenu_restartOthers)) },
      onClick = {
        runCommand(
          "am force-stop com.android.settings",
          "am force-stop com.google.android.settings.intelligence",
          "am force-stop com.android.intentresolver",
          "am force-stop com.android.permissioncontroller",
        )
      },
    )
    HorizontalDivider()
    DropdownMenuItem(
      leadingIcon = { Icon(Icons.Outlined.Language, "localization") },
      text = { Text(stringResource(R.string.mainMenu_openCrowdin)) },
      onClick = {
        context.startActivity(
          Intent(Intent.ACTION_VIEW, "https://crowdin.com/project/global-icon-pack-android".toUri())
        )
        expand = false
      },
    )
    DropdownMenuItem(
      leadingIcon = { Icon(Icons.Outlined.Code, "Github") },
      text = { Text(stringResource(R.string.mainMenu_openGithub)) },
      onClick = {
        context.startActivity(
          Intent(
            Intent.ACTION_VIEW,
            "https://github.com/RichardLuo0/global-icon-pack-android".toUri(),
          )
        )
        expand = false
      },
    )
    DropdownMenuItem(
      leadingIcon = { Icon(Icons.Outlined.Coffee, "kofi") },
      text = { Text(stringResource(R.string.mainMenu_buyMeACoffee)) },
      onClick = {
        context.startActivity(Intent(Intent.ACTION_VIEW, "https://ko-fi.com/richardluo".toUri()))
        expand = false
      },
    )
  }
}
