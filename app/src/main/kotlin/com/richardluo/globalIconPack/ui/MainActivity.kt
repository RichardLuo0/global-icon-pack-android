package com.richardluo.globalIconPack.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Shortcut
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Backpack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.PhotoSizeSelectSmall
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import com.richardluo.globalIconPack.BuildConfig
import com.richardluo.globalIconPack.MODE_LOCAL
import com.richardluo.globalIconPack.MODE_PROVIDER
import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.iconPack.IconPackApps
import com.richardluo.globalIconPack.ui.components.IconButtonWithTooltip
import com.richardluo.globalIconPack.ui.components.IconPackItem
import com.richardluo.globalIconPack.ui.components.LoadingDialog
import com.richardluo.globalIconPack.ui.components.MainDropdownMenu
import com.richardluo.globalIconPack.ui.components.OneLineText
import com.richardluo.globalIconPack.ui.components.SampleTheme
import com.richardluo.globalIconPack.ui.components.SnackbarErrorVisuals
import com.richardluo.globalIconPack.ui.components.TwoLineText
import com.richardluo.globalIconPack.ui.components.WarnDialog
import com.richardluo.globalIconPack.ui.components.mapListPreference
import com.richardluo.globalIconPack.ui.components.myPreference
import com.richardluo.globalIconPack.ui.components.myPreferenceTheme
import com.richardluo.globalIconPack.ui.components.mySliderPreference
import com.richardluo.globalIconPack.ui.components.mySwitchPreference
import com.richardluo.globalIconPack.ui.viewModel.MainVM
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.getPreferenceFlow
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import me.zhanghai.compose.preference.LocalPreferenceFlow
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.listPreference
import me.zhanghai.compose.preference.preference
import me.zhanghai.compose.preference.preferenceCategory
import me.zhanghai.compose.preference.switchPreference
import me.zhanghai.compose.preference.textFieldPreference

class MainActivity : ComponentActivity() {
  private companion object {
    init {
      Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER))
    }
  }

  private val viewModel: MainVM by viewModels()
  private val prefFlow by lazy {
    runCatching { WorldPreference.getPrefInApp(this) }.getOrNull()?.getPreferenceFlow()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // If onNewIntent() is not getting called
    applyIconPackIfNeeded(intent)

    val prefFlow = prefFlow
    setContent {
      if (prefFlow == null) {
        WarnDialog(
          openState = remember { mutableStateOf(true) },
          title = { OneLineText(getString(R.string.warning)) },
          content = { Text(getString(R.string.plzEnableModuleFirst)) },
          onCancel = { finish() },
        ) {
          finish()
        }
      } else
        SampleTheme {
          ProvidePreferenceLocals(flow = prefFlow, myPreferenceTheme()) { SampleScreen() }
        }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    applyIconPackIfNeeded(intent)
  }

  private fun applyIconPackIfNeeded(intent: Intent) {
    val prefFlow = prefFlow
    if (prefFlow != null && intent.action == "${BuildConfig.APPLICATION_ID}.APPLY_ICON_PACK") {
      prefFlow.update {
        it.toMutablePreferences().apply {
          this[Pref.ICON_PACK.first] = intent.getStringExtra("packageName")
        }
      }
      Toast.makeText(this, R.string.iconPackApplied, Toast.LENGTH_LONG).show()
    }
  }

  @Composable
  @OptIn(ExperimentalMaterial3Api::class)
  private fun SampleScreen() {
    val context = this
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarState = remember { SnackbarHostState() }
    Scaffold(
      modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
      topBar = {
        TopAppBar(
          title = {
            Box(
              modifier = Modifier.fillMaxWidth().height(TopAppBarDefaults.TopAppBarExpandedHeight),
              contentAlignment = Alignment.CenterStart,
            ) {
              Icon(
                getDrawable(R.mipmap.ic_launcher)!!.toBitmap().asImageBitmap(),
                "Logo",
                modifier = Modifier.fillMaxHeight(0.5f).padding(horizontal = 8.dp),
                tint = Color.Unspecified,
              )
            }
          },
          modifier = Modifier.fillMaxWidth(),
          scrollBehavior = scrollBehavior,
          actions = { MainDropdownMenu(snackbarState) },
        )
      },
      snackbarHost = {
        SnackbarHost(
          hostState = snackbarState,
          snackbar = {
            val isError = snackbarState.currentSnackbarData?.visuals is SnackbarErrorVisuals
            Snackbar(
              it,
              shape = MaterialTheme.shapes.extraLarge,
              containerColor =
                if (isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.secondary,
              contentColor =
                if (isError) MaterialTheme.colorScheme.onError
                else MaterialTheme.colorScheme.onSecondary,
            )
          },
        )
      },
    ) { contentPadding ->
      if (viewModel.waiting > 0) LoadingDialog()

      val flow = LocalPreferenceFlow.current
      LaunchedEffect(flow) {
        viewModel.bindPrefFlow(flow)
        flow
          .map { it.get(Pref.MODE) }
          .distinctUntilChanged()
          .onEach { mode ->
            // Ask for notification permission used for foreground service
            if (
              mode == MODE_PROVIDER &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                  PackageManager.PERMISSION_GRANTED
            )
              context.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 200)
          }
          .launchIn(lifecycleScope)
      }

      LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        MainPreference.run {
          general(context)
          iconPack(context)
          pixel()
        }
      }
    }
  }
}

object MainPreference {
  fun LazyListScope.general(context: Context) {
    preferenceCategory(
      key = "generalCat",
      title = { OneLineText(stringResource(R.string.general)) },
    )
    listPreference(
      icon = {
        AnimatedContent(it) {
          when (it) {
            MODE_PROVIDER -> Icon(Icons.Outlined.SettingsRemote, it)
            MODE_LOCAL -> Icon(Icons.Outlined.Memory, it)
          }
        }
      },
      key = Pref.MODE.first,
      defaultValue = Pref.MODE.second,
      values = listOf(MODE_PROVIDER, MODE_LOCAL),
      valueToText = { AnnotatedString(modeToDesc(context, it)) },
      title = { OneLineText(stringResource(R.string.mode)) },
      summary = { TwoLineText(modeToDesc(context, it)) },
    )
    mapListPreference(
      icon = { Icon(Icons.Outlined.Backpack, Pref.ICON_PACK.first) },
      key = Pref.ICON_PACK.first,
      defaultValue = Pref.ICON_PACK.second,
      getValueMap = { IconPackApps.getFlow(context).collectAsState(mapOf()).value },
      item = { key, value, currentKey, onClick -> IconPackItem(key, value, currentKey, onClick) },
      title = { OneLineText(stringResource(R.string.iconPack)) },
      summary = { key, value ->
        Text(
          value?.label ?: key.takeIf { it.isNotEmpty() } ?: stringResource(R.string.iconPackSummary)
        )
      },
    )
    switchPreference(
      icon = {},
      key = Pref.ICON_PACK_AS_FALLBACK.first,
      defaultValue = Pref.ICON_PACK_AS_FALLBACK.second,
      title = { OneLineText(stringResource(R.string.iconPackAsFallback)) },
      summary = { TwoLineText(stringResource(R.string.iconPackAsFallbackSummary)) },
    )
    switchPreference(
      icon = { Icon(Icons.AutoMirrored.Outlined.Shortcut, Pref.SHORTCUT.first) },
      key = Pref.SHORTCUT.first,
      defaultValue = Pref.SHORTCUT.second,
      title = { OneLineText(stringResource(R.string.shortcut)) },
    )
    preference(
      icon = { Icon(Icons.Outlined.Merge, "openMerger") },
      key = "openMerger",
      onClick = { context.startActivity(Intent(context, IconPackMergerActivity::class.java)) },
      title = { OneLineText(stringResource(R.string.mergeIconPack)) },
      summary = { TwoLineText(stringResource(R.string.mergeIconPackSummary)) },
    )
  }

  fun LazyListScope.iconPack(context: Context, onlyOptions: Boolean = false) {
    if (!onlyOptions) {
      preferenceCategory(
        key = "iconPackCat",
        title = { OneLineText(stringResource(R.string.iconPackSettings)) },
      )
      myPreference(
        icon = { Icon(Icons.Outlined.Edit, "iconVariant") },
        key = "iconVariant",
        enabled = { it.get(Pref.MODE) == MODE_PROVIDER && it.get(Pref.ICON_PACK).isNotEmpty() },
        onClick = { context.startActivity(Intent(context, IconVariantActivity::class.java)) },
        title = { OneLineText(stringResource(R.string.iconVariant)) },
        summary = { TwoLineText(stringResource(R.string.iconVariantSummary)) },
      )
    }
    switchPreference(
      icon = { Icon(Icons.Outlined.SettingsBackupRestore, Pref.ICON_FALLBACK.first) },
      key = Pref.ICON_FALLBACK.first,
      defaultValue = Pref.ICON_FALLBACK.second,
      title = { OneLineText(stringResource(R.string.iconFallback)) },
      summary = { TwoLineText(stringResource(R.string.iconFallbackSummary)) },
    )
    mySwitchPreference(
      icon = {},
      key = Pref.SCALE_ONLY_FOREGROUND.first,
      enabled = { it.get(Pref.ICON_FALLBACK) },
      defaultValue = Pref.SCALE_ONLY_FOREGROUND.second,
      title = { OneLineText(stringResource(R.string.scaleOnlyForeground)) },
    )
    mySwitchPreference(
      icon = {},
      key = Pref.OVERRIDE_ICON_FALLBACK.first,
      enabled = { it.get(Pref.ICON_FALLBACK) },
      defaultValue = Pref.OVERRIDE_ICON_FALLBACK.second,
      title = { OneLineText(stringResource(R.string.overrideIconFallback)) },
      summary = { TwoLineText(stringResource(R.string.overrideIconFallbackSummary)) },
    )
    mySliderPreference(
      icon = { Icon(Icons.Outlined.PhotoSizeSelectSmall, Pref.ICON_PACK_SCALE.first) },
      enabled = { it.get(Pref.ICON_FALLBACK) && it.get(Pref.OVERRIDE_ICON_FALLBACK) },
      key = Pref.ICON_PACK_SCALE.first,
      defaultValue = Pref.ICON_PACK_SCALE.second,
      valueRange = 0f..1.5f,
      valueSteps = 29,
      title = { OneLineText(stringResource(R.string.iconPackScale)) },
      summary = { OneLineText("%.2f".format(it)) },
      valueToText = { "%.2f".format(it) },
    )
  }

  fun LazyListScope.pixel() {
    preferenceCategory(
      key = "pixelCat",
      title = { OneLineText(stringResource(R.string.pixelSettings)) },
    )
    textFieldPreference(
      icon = { Icon(Icons.Outlined.Apps, Pref.PIXEL_LAUNCHER_PACKAGE.first) },
      key = Pref.PIXEL_LAUNCHER_PACKAGE.first,
      defaultValue = Pref.PIXEL_LAUNCHER_PACKAGE.second,
      textToValue = { it },
      textField = { value, onValueChange, onOk ->
        OutlinedTextField(
          value = value,
          onValueChange = onValueChange,
          modifier = Modifier.fillMaxWidth(),
          keyboardActions = KeyboardActions { onOk() },
          singleLine = true,
          trailingIcon = {
            IconButtonWithTooltip(Icons.Outlined.Restore, "Restore") {
              onValueChange(TextFieldValue(Pref.PIXEL_LAUNCHER_PACKAGE.second))
            }
          },
        )
      },
      title = { OneLineText(stringResource(R.string.pixelLauncherPackage)) },
      summary = { TwoLineText(stringResource(R.string.pixelLauncherPackageSummary)) },
    )
    switchPreference(
      icon = {},
      key = Pref.NO_FORCE_SHAPE.first,
      defaultValue = Pref.NO_FORCE_SHAPE.second,
      title = { OneLineText(stringResource(R.string.noForceShape)) },
      summary = { TwoLineText(stringResource(R.string.noForceShapeSummary)) },
    )
    switchPreference(
      icon = {},
      key = Pref.NO_SHADOW.first,
      defaultValue = Pref.NO_SHADOW.second,
      title = { OneLineText(stringResource(R.string.noShadow)) },
      summary = { TwoLineText(stringResource(R.string.noShadowSummary)) },
    )
    switchPreference(
      icon = {},
      key = Pref.FORCE_LOAD_CLOCK_AND_CALENDAR.first,
      defaultValue = Pref.FORCE_LOAD_CLOCK_AND_CALENDAR.second,
      title = { OneLineText(stringResource(R.string.forceLoadClockAndCalendar)) },
    )
  }

  private fun modeToDesc(context: Context, mode: String) =
    when (mode) {
      MODE_PROVIDER -> context.getString(R.string.modeProvider)
      MODE_LOCAL -> context.getString(R.string.modeLocal)
      else -> mode
    }
}
