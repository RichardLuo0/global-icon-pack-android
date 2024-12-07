package com.richardluo.globalIconPack.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.richardluo.globalIconPack.MODE_LOCAL
import com.richardluo.globalIconPack.MODE_PROVIDER
import com.richardluo.globalIconPack.PrefKey
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.iconPack.IconPackApp
import com.richardluo.globalIconPack.iconPack.IconPackApps
import com.richardluo.globalIconPack.iconPack.database.WritableIconPackDB
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.logInApp
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.zhanghai.compose.preference.Preferences
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.getPreferenceFlow
import me.zhanghai.compose.preference.listPreference
import me.zhanghai.compose.preference.preferenceCategory
import me.zhanghai.compose.preference.rememberPreferenceState
import me.zhanghai.compose.preference.sliderPreference
import me.zhanghai.compose.preference.switchPreference

@Composable
@SuppressLint("WorldReadableFiles")
private fun worldPreferenceFlow(): MutableStateFlow<Preferences> {
  val context = LocalContext.current
  return WorldPreference.getPrefInApp(context).getPreferenceFlow()
}

class MainActivity : ComponentActivity() {

  companion object {
    init {
      Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER))
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      SampleTheme { ProvidePreferenceLocals(flow = worldPreferenceFlow()) { SampleScreen() } }
    }
    // Init db
    val ctx = this
    runBlocking { launch { WritableIconPackDB(ctx) } }
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SampleScreen() {
  val context = LocalContext.current
  val windowInsets = WindowInsets.safeDrawing
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
  val scope = rememberCoroutineScope()
  val snackbarState = remember { SnackbarHostState() }
  Scaffold(
    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      val appLabel = context.applicationInfo.loadLabel(context.packageManager).toString()
      TopAppBar(
        title = { Text(appLabel) },
        modifier = Modifier.fillMaxWidth(),
        windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        scrollBehavior = scrollBehavior,
        actions = {
          val onShellResult =
            Shell.ResultCallback { result ->
              scope.launch {
                if (result.isSuccess)
                  snackbarState.showSnackbar(
                    "✅ ${context.getString(R.string.RestartedSuccessfully)}",
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                  )
                else {
                  val error =
                    "code: ${result.code} err: ${result.err.joinToString("\n")} out: ${result.out.joinToString("\n")}"
                  logInApp(error)
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
              text = { Text(stringResource(R.string.restartSettings)) },
              onClick = runCommand("am force-stop com.android.settings"),
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.restartSystemUI)) },
              onClick = runCommand("killall com.android.systemui"),
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.restartShareMenu)) },
              onClick = runCommand("am force-stop com.android.intentresolver"),
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.restartPixelLauncher)) },
              onClick =
                runCommand(
                  "rm -f /data/data/com.google.android.apps.nexuslauncher/databases/app_icons.db && am force-stop com.google.android.apps.nexuslauncher"
                ),
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
        },
      )
    },
    snackbarHost = { SnackbarHost(hostState = snackbarState) },
    containerColor = Color.Transparent,
    contentColor = contentColorFor(MaterialTheme.colorScheme.background),
    contentWindowInsets = windowInsets,
  ) { contentPadding ->
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
      preferenceCategory(key = "general", title = { Text(stringResource(R.string.general)) })
      listPreference(
        key = PrefKey.MODE,
        defaultValue = MODE_PROVIDER,
        values = listOf(MODE_PROVIDER, MODE_LOCAL),
        valueToText = { AnnotatedString(modeToDesc(context, it)) },
        title = { Text(stringResource(R.string.mode)) },
        summary = { Text(modeToDesc(context, it)) },
      )
      lazyListPreference(
        key = PrefKey.ICON_PACK,
        defaultValue = "",
        load = { IconPackApps.load(context) },
        item = { value, currentValue, valueMap, onClick ->
          IconPackItem(value, currentValue, valueMap, onClick)
        },
        title = { Text(stringResource(R.string.iconPack)) },
        summary = { Text(it.ifEmpty { stringResource(R.string.iconPackSummary) }) },
      )
      switchPreference(
        key = PrefKey.NO_FORCE_SHAPE,
        defaultValue = false,
        title = { Text(stringResource(R.string.noForceShape)) },
        summary = { Text(stringResource(R.string.noForceShapeSummary)) },
      )
      switchPreference(
        key = PrefKey.ICON_PACK_AS_FALLBACK,
        defaultValue = false,
        title = { Text(stringResource(R.string.iconPackAsFallback)) },
        summary = { Text(stringResource(R.string.iconPackAsFallbackSummary)) },
      )

      preferenceCategory(
        key = "launcherSettings",
        title = { Text(stringResource(R.string.launcherSettings)) },
      )
      sliderPreference(
        key = PrefKey.SCALE,
        defaultValue = 1f,
        valueRange = 0f..1.5f,
        valueSteps = 29,
        valueText = { Text("%.2f".format(it)) },
        title = { Text(stringResource(R.string.scale)) },
        summary = { Text(stringResource(R.string.scaleSummary)) },
      )
      switchPreference(
        key = PrefKey.FORCE_LOAD_CLOCK_AND_CALENDAR,
        defaultValue = true,
        title = { Text(stringResource(R.string.forceLoadClockAndCalendar)) },
        summary = { Text(if (it) "On" else "Off") },
      )

      preferenceCategory(
        key = "iconPackSettings",
        title = { Text(stringResource(R.string.iconPackSettings)) },
      )
      switchPreference(
        key = PrefKey.ICON_FALLBACK,
        defaultValue = true,
        title = { Text(stringResource(R.string.iconFallback)) },
        summary = { Text(stringResource(R.string.iconFallbackSummary)) },
      )
      item {
        val enableState = rememberPreferenceState(PrefKey.OVERRIDE_ICON_FALLBACK, false)
        val enabled by enableState
        SwitchPreference(
          state = enableState,
          title = { Text(stringResource(R.string.overrideIconFallback)) },
          summary = { Text(stringResource(R.string.overrideIconFallbackSummary)) },
        )
        ComposableSliderPreference(
          enabled = { enabled },
          key = PrefKey.ICON_PACK_SCALE,
          defaultValue = 1f,
          valueRange = 0f..1.5f,
          valueSteps = 29,
          valueText = { Text("%.2f".format(it)) },
          title = { Text(stringResource(R.string.iconPackScale)) },
        )
      }
    }
  }
}

fun modeToDesc(context: Context, mode: String) =
  when (mode) {
    MODE_PROVIDER -> context.getString(R.string.modeProvider)
    MODE_LOCAL -> context.getString(R.string.modeLocal)
    else -> mode
  }

@Composable
private fun IconPackItem(
  value: String,
  currentValue: String,
  valueMap: Map<String, IconPackApp>,
  onClick: () -> Unit,
) {
  val selected = value == currentValue
  val iconPack = valueMap[value]
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .height(IntrinsicSize.Min)
        .selectable(selected, true, Role.RadioButton, onClick)
        .padding(horizontal = 24.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = null)
    Spacer(modifier = Modifier.width(12.dp))
    Box(modifier = Modifier.fillMaxHeight().aspectRatio(1f)) {
      iconPack?.icon?.let {
        Image(
          bitmap = it.toBitmap().asImageBitmap(),
          contentDescription = value,
          modifier = Modifier.matchParentSize(),
          contentScale = ContentScale.Crop,
        )
      }
        ?: Image(
          painter = painterResource(android.R.drawable.sym_def_app_icon),
          contentDescription = value,
          modifier = Modifier.matchParentSize(),
          contentScale = ContentScale.Crop,
        )
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.fillMaxWidth()) {
      Text(
        iconPack?.label ?: "Unknown label",
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        value,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
