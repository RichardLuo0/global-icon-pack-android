package com.richardluo.globalIconPack.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.lifecycleScope
import com.richardluo.globalIconPack.MODE_LOCAL
import com.richardluo.globalIconPack.MODE_PROVIDER
import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.iconPack.IconPackApps
import com.richardluo.globalIconPack.ui.components.ComposableSliderPreference
import com.richardluo.globalIconPack.ui.components.IconPackItem
import com.richardluo.globalIconPack.ui.components.LoadingDialog
import com.richardluo.globalIconPack.ui.components.SampleTheme
import com.richardluo.globalIconPack.ui.components.SnackbarErrorVisuals
import com.richardluo.globalIconPack.ui.components.WarnDialog
import com.richardluo.globalIconPack.ui.components.mapListPreference
import com.richardluo.globalIconPack.ui.viewModel.MainVM
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.getValue
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import me.zhanghai.compose.preference.LocalPreferenceFlow
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.getPreferenceFlow
import me.zhanghai.compose.preference.listPreference
import me.zhanghai.compose.preference.preference
import me.zhanghai.compose.preference.preferenceCategory
import me.zhanghai.compose.preference.rememberPreferenceState
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
      } else SampleTheme { ProvidePreferenceLocals(flow = prefFlow) { SampleScreen() } }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    applyIconPackIfNeeded(intent)
  }

  private fun applyIconPackIfNeeded(intent: Intent) {
    val prefFlow = prefFlow
    if (prefFlow != null && intent.action == "${BuildConfig.APPLICATION_ID}.APPLY_ICON_PACK") {
      prefFlow.value =
        prefFlow.value.toMutablePreferences().apply {
          this[Pref.ICON_PACK.first] = intent.getStringExtra("packageName")
        }
      Toast.makeText(this, R.string.iconPackApplied, Toast.LENGTH_LONG).show()
    }
  }

  @Composable
  @OptIn(ExperimentalMaterial3Api::class)
  private fun SampleScreen() {
    val context = this
    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarState = remember { SnackbarHostState() }
    Scaffold(
      modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
      topBar = {
        val appLabel = applicationInfo.loadLabel(packageManager).toString()
        TopAppBar(
          title = { Text(appLabel) },
          modifier = Modifier.fillMaxWidth(),
          windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
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
      contentWindowInsets = windowInsets,
    ) { contentPadding ->
      if (viewModel.waiting > 0) LoadingDialog()

      val flow = LocalPreferenceFlow.current
      LaunchedEffect(flow) {
        viewModel.bindPreferencesFlow(flow)
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
        preferenceCategory(key = "general", title = { Text(stringResource(R.string.general)) })
        listPreference(
          key = Pref.MODE.first,
          defaultValue = Pref.MODE.second,
          values = listOf(MODE_PROVIDER, MODE_LOCAL),
          valueToText = { AnnotatedString(modeToDesc(it)) },
          title = { Text(stringResource(R.string.mode)) },
          summary = { Text(modeToDesc(it)) },
        )
        mapListPreference(
          key = Pref.ICON_PACK.first,
          defaultValue = Pref.ICON_PACK.second,
          getValueMap = { IconPackApps.getFlow(context).collectAsState(mapOf()).value },
          item = { key, value, currentKey, onClick ->
            IconPackItem(key, value, currentKey, onClick)
          },
          title = { Text(stringResource(R.string.iconPack)) },
          summary = { key, value ->
            Text(
              value?.label
                ?: key.takeIf { it.isNotEmpty() }
                ?: stringResource(R.string.iconPackSummary)
            )
          },
        )
        switchPreference(
          key = Pref.ICON_PACK_AS_FALLBACK.first,
          defaultValue = Pref.ICON_PACK_AS_FALLBACK.second,
          title = { Text(stringResource(R.string.iconPackAsFallback)) },
          summary = { Text(stringResource(R.string.iconPackAsFallbackSummary)) },
        )
        switchPreference(
          key = Pref.SHORTCUT.first,
          defaultValue = Pref.SHORTCUT.second,
          title = { Text(stringResource(R.string.shortcut)) },
        )
        preference(
          key = "openMerger",
          onClick = { context.startActivity(Intent(context, IconPackMergerActivity::class.java)) },
          title = { Text(stringResource(R.string.mergeIconPack)) },
          summary = { Text(stringResource(R.string.mergeIconPackSummary)) },
        )

        preferenceCategory(
          key = "iconPackSettings",
          title = { Text(stringResource(R.string.iconPackSettings)) },
        )
        item(key = "iconVariant") {
          val isProvider = flow.map { it.get(Pref.MODE) == MODE_PROVIDER }.getValue(false)
          val isPackSet = flow.map { it.get(Pref.ICON_PACK).isNotEmpty() }.getValue(false)
          Preference(
            enabled = isProvider && isPackSet,
            onClick = { context.startActivity(Intent(context, IconVariantActivity::class.java)) },
            title = { Text(stringResource(R.string.iconVariant)) },
            summary = { Text(stringResource(R.string.iconVariantSummary)) },
          )
        }
        switchPreference(
          key = Pref.ICON_FALLBACK.first,
          defaultValue = Pref.ICON_FALLBACK.second,
          title = { Text(stringResource(R.string.iconFallback)) },
          summary = { Text(stringResource(R.string.iconFallbackSummary)) },
        )
        switchPreference(
          key = Pref.SCALE_ONLY_FOREGROUND.first,
          defaultValue = Pref.SCALE_ONLY_FOREGROUND.second,
          title = { Text(stringResource(R.string.scaleOnlyForeground)) },
        )
        item {
          val enableState =
            rememberPreferenceState(
              Pref.OVERRIDE_ICON_FALLBACK.first,
              Pref.OVERRIDE_ICON_FALLBACK.second,
            )
          val enabled by enableState
          SwitchPreference(
            state = enableState,
            title = { Text(stringResource(R.string.overrideIconFallback)) },
            summary = { Text(stringResource(R.string.overrideIconFallbackSummary)) },
          )
          ComposableSliderPreference(
            enabled = { enabled },
            key = Pref.ICON_PACK_SCALE.first,
            defaultValue = Pref.ICON_PACK_SCALE.second,
            valueRange = 0f..1.5f,
            valueSteps = 29,
            valueText = { Text("%.2f".format(it)) },
            title = { Text(stringResource(R.string.iconPackScale)) },
          )
        }

        preferenceCategory(
          key = "pixelSettings",
          title = { Text(stringResource(R.string.pixelSettings)) },
        )
        textFieldPreference(
          key = Pref.PIXEL_LAUNCHER_PACKAGE.first,
          defaultValue = Pref.PIXEL_LAUNCHER_PACKAGE.second,
          textToValue = { it },
          title = { Text(stringResource(R.string.pixelLauncherPackage)) },
          summary = { Text(stringResource(R.string.pixelLauncherPackageSummary)) },
        )
        switchPreference(
          key = Pref.NO_FORCE_SHAPE.first,
          defaultValue = Pref.NO_FORCE_SHAPE.second,
          title = { Text(stringResource(R.string.noForceShape)) },
          summary = { Text(stringResource(R.string.noForceShapeSummary)) },
        )
        switchPreference(
          key = Pref.NO_SHADOW.first,
          defaultValue = Pref.NO_SHADOW.second,
          title = { Text(stringResource(R.string.noShadow)) },
          summary = { Text(stringResource(R.string.noShadowSummary)) },
        )
        switchPreference(
          key = Pref.FORCE_LOAD_CLOCK_AND_CALENDAR.first,
          defaultValue = Pref.FORCE_LOAD_CLOCK_AND_CALENDAR.second,
          title = { Text(stringResource(R.string.forceLoadClockAndCalendar)) },
        )
      }
    }
  }

  private fun modeToDesc(mode: String) =
    when (mode) {
      MODE_PROVIDER -> getString(R.string.modeProvider)
      MODE_LOCAL -> getString(R.string.modeLocal)
      else -> mode
    }
}
