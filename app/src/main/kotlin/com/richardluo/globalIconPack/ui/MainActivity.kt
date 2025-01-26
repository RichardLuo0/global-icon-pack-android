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
import com.richardluo.globalIconPack.PrefDef
import com.richardluo.globalIconPack.PrefKey
import com.richardluo.globalIconPack.R
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
  private val viewModel: MainVM by viewModels()

  companion object {
    init {
      Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER))
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      val pref = runCatching { WorldPreference.getPrefInApp(this) }.getOrNull()
      if (pref == null) {
        WarnDialog(
          openState = remember { mutableStateOf(true) },
          title = { Text(getString(R.string.warning)) },
          content = { Text(getString(R.string.plzEnableModuleFirst)) },
          onCancel = { finish() },
        ) {
          finish()
        }
      } else
        SampleTheme { ProvidePreferenceLocals(flow = pref.getPreferenceFlow()) { SampleScreen() } }
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
          .map { it[PrefKey.MODE] ?: PrefDef.MODE }
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
          key = PrefKey.MODE,
          defaultValue = PrefDef.MODE,
          values = listOf(MODE_PROVIDER, MODE_LOCAL),
          valueToText = { AnnotatedString(modeToDesc(it)) },
          title = { Text(stringResource(R.string.mode)) },
          summary = { Text(modeToDesc(it)) },
        )
        mapListPreference(
          key = PrefKey.ICON_PACK,
          defaultValue = PrefDef.ICON_PACK,
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
          key = PrefKey.ICON_PACK_AS_FALLBACK,
          defaultValue = PrefDef.ICON_PACK_AS_FALLBACK,
          title = { Text(stringResource(R.string.iconPackAsFallback)) },
          summary = { Text(stringResource(R.string.iconPackAsFallbackSummary)) },
        )
        switchPreference(
          key = PrefKey.SHORTCUT,
          defaultValue = PrefDef.SHORTCUT,
          title = { Text(stringResource(R.string.shortcut)) },
        )
        item(key = "iconVariant") {
          val isProvider =
            flow.map { (it[PrefKey.MODE] ?: PrefDef.MODE) == MODE_PROVIDER }.getValue(false)
          val isPackSet =
            flow.map { it.get<String>(PrefKey.ICON_PACK)?.isNotEmpty() ?: false }.getValue(false)
          Preference(
            enabled = isProvider && isPackSet,
            onClick = { context.startActivity(Intent(context, IconVariantActivity::class.java)) },
            title = { Text(stringResource(R.string.iconVariant)) },
            summary = { Text(stringResource(R.string.iconVariantSummary)) },
          )
        }
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
        switchPreference(
          key = PrefKey.ICON_FALLBACK,
          defaultValue = PrefDef.ICON_FALLBACK,
          title = { Text(stringResource(R.string.iconFallback)) },
          summary = { Text(stringResource(R.string.iconFallbackSummary)) },
        )
        item {
          val enableState =
            rememberPreferenceState(PrefKey.OVERRIDE_ICON_FALLBACK, PrefDef.OVERRIDE_ICON_FALLBACK)
          val enabled by enableState
          SwitchPreference(
            state = enableState,
            title = { Text(stringResource(R.string.overrideIconFallback)) },
            summary = { Text(stringResource(R.string.overrideIconFallbackSummary)) },
          )
          ComposableSliderPreference(
            enabled = { enabled },
            key = PrefKey.ICON_PACK_SCALE,
            defaultValue = PrefDef.ICON_PACK_SCALE,
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
          key = PrefKey.PIXEL_LAUNCHER_PACKAGE,
          defaultValue = PrefDef.PIXEL_LAUNCHER_PACKAGE,
          textToValue = { it },
          title = { Text(stringResource(R.string.pixelLauncherPackage)) },
          summary = { Text(stringResource(R.string.pixelLauncherPackageSummary)) },
        )
        switchPreference(
          key = PrefKey.NO_FORCE_SHAPE,
          defaultValue = PrefDef.NO_FORCE_SHAPE,
          title = { Text(stringResource(R.string.noForceShape)) },
          summary = { Text(stringResource(R.string.noForceShapeSummary)) },
        )
        switchPreference(
          key = PrefKey.NO_SHADOW,
          defaultValue = PrefDef.NO_SHADOW,
          title = { Text(stringResource(R.string.noShadow)) },
          summary = { Text(stringResource(R.string.noShadowSummary)) },
        )
        switchPreference(
          key = PrefKey.FORCE_LOAD_CLOCK_AND_CALENDAR,
          defaultValue = PrefDef.FORCE_LOAD_CLOCK_AND_CALENDAR,
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
