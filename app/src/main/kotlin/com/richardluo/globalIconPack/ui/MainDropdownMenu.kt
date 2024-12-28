package com.richardluo.globalIconPack.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.utils.log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.launch

@Composable
fun MainDropdownMenu(snackbarState: SnackbarHostState) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val onShellResult =
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
            "❌ $error",
            withDismissAction = true,
            duration = SnackbarDuration.Long,
          )
        }
      }
    }

  var expanded by remember { mutableStateOf(false) }

  fun runCommand(vararg cmd: String): () -> Unit {
    return {
      Shell.cmd(*cmd).submit(onShellResult)
      expanded = false
    }
  }

  IconButton(onClick = { expanded = !expanded }) {
    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.moreOptions))
  }
  DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
    DropdownMenuItem(
      text = { Text(stringResource(R.string.restartSystemUI)) },
      onClick = runCommand("killall com.android.systemui"),
    )
    DropdownMenuItem(
      text = { Text(stringResource(R.string.restartPixelLauncher)) },
      onClick =
        runCommand(
          "rm -f /data/data/com.google.android.apps.nexuslauncher/databases/app_icons.db && am force-stop com.google.android.apps.nexuslauncher"
        ),
    )
    DropdownMenuItem(
      text = { Text(stringResource(R.string.restartOthers)) },
      onClick =
        runCommand(
          "am force-stop com.android.settings",
          "am force-stop com.android.intentresolver",
          "am force-stop com.android.permissioncontroller",
        ),
    )
    DropdownMenuItem(
      text = { Text(stringResource(R.string.openCrowdin)) },
      onClick = {
        context.startActivity(
          Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://crowdin.com/project/global-icon-pack-android"),
          )
        )
        expanded = false
      },
    )
    DropdownMenuItem(
      text = { Text(stringResource(R.string.openGithub)) },
      onClick = {
        context.startActivity(
          Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://github.com/RichardLuo0/global-icon-pack-android"),
          )
        )
        expanded = false
      },
    )
  }
}
