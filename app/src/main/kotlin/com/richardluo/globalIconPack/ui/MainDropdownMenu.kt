package com.richardluo.globalIconPack.ui

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenuItem
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
import com.richardluo.globalIconPack.ui.components.IconButtonWithTooltip
import com.richardluo.globalIconPack.ui.components.MyDropdownMenu
import com.richardluo.globalIconPack.ui.components.SnackbarErrorVisuals
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.launch

@Composable
fun MainDropdownMenu(snackbarState: SnackbarHostState) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var expanded by rememberSaveable { mutableStateOf(false) }

  val onShellResult = remember {
    Shell.ResultCallback { result ->
      scope.launch {
        if (result.isSuccess)
          snackbarState.showSnackbar(
            "✅ ${context.getString(R.string.restartedSuccessfully)}",
            withDismissAction = true,
            duration = SnackbarDuration.Long,
          )
        else {
          val error =
            "code: ${result.code} err: ${result.err.joinToString("\n")} out: ${result.out.joinToString("\n")}"
          log(error)
          snackbarState.showSnackbar(
            SnackbarErrorVisuals(
              "❌ $error",
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
    expanded = false
  }

  IconButtonWithTooltip(Icons.Outlined.MoreVert, stringResource(R.string.moreOptions)) {
    expanded = !expanded
  }
  MyDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
    DropdownMenuItem(
      leadingIcon = {},
      text = { Text(stringResource(R.string.restartSystemUI)) },
      onClick = { runCommand("killall com.android.systemui") },
    )
    DropdownMenuItem(
      leadingIcon = {},
      text = { Text(stringResource(R.string.restartPixelLauncher)) },
      onClick = {
        val launcher = WorldPreference.getPrefInApp(context).get(Pref.PIXEL_LAUNCHER_PACKAGE)
        runCommand("rm -f /data/data/$launcher/databases/app_icons.db && am force-stop $launcher")
      },
    )
    DropdownMenuItem(
      leadingIcon = {},
      text = { Text(stringResource(R.string.restartOthers)) },
      onClick = {
        runCommand(
          "am force-stop com.android.settings",
          "am force-stop com.google.android.settings.intelligence",
          "am force-stop com.android.intentresolver",
          "am force-stop com.android.permissioncontroller",
        )
      },
    )
    DropdownMenuItem(
      leadingIcon = { Text("🌐") },
      text = { Text(stringResource(R.string.openCrowdin)) },
      onClick = {
        context.startActivity(
          Intent(Intent.ACTION_VIEW, "https://crowdin.com/project/global-icon-pack-android".toUri())
        )
        expanded = false
      },
    )
    DropdownMenuItem(
      leadingIcon = { Text("🧑‍💻") },
      text = { Text(stringResource(R.string.openGithub)) },
      onClick = {
        context.startActivity(
          Intent(
            Intent.ACTION_VIEW,
            "https://github.com/RichardLuo0/global-icon-pack-android".toUri(),
          )
        )
        expanded = false
      },
    )
    DropdownMenuItem(
      leadingIcon = { Text("☕") },
      text = { Text(stringResource(R.string.buyMeACoffee)) },
      onClick = {
        context.startActivity(Intent(Intent.ACTION_VIEW, "https://ko-fi.com/richardluo".toUri()))
        expanded = false
      },
    )
  }
}
