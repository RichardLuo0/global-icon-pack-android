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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Shortcut
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Backpack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoSizeSelectSmall
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.richardluo.globalIconPack.AppPref
import com.richardluo.globalIconPack.BuildConfig
import com.richardluo.globalIconPack.MODE_LOCAL
import com.richardluo.globalIconPack.MODE_PROVIDER
import com.richardluo.globalIconPack.MODE_SHARE
import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.ui.components.IconButtonWithTooltip
import com.richardluo.globalIconPack.ui.components.IconPackItem
import com.richardluo.globalIconPack.ui.components.LazyListDialog
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
import com.richardluo.globalIconPack.ui.viewModel.IconPackApps
import com.richardluo.globalIconPack.ui.viewModel.MainVM
import com.richardluo.globalIconPack.utils.AppPreference
import com.richardluo.globalIconPack.utils.getValue
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.LocalPreferenceFlow
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.listPreference
import me.zhanghai.compose.preference.preference
import me.zhanghai.compose.preference.switchPreference
import me.zhanghai.compose.preference.textFieldPreference

class MainActivity : ComponentActivity() {
  private companion object {
    init {
      Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER))
    }
  }

  private val viewModel: MainVM by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // If onNewIntent() is not getting called
    applyIconPackIfNeeded(intent)

    setContent {
      val needSetup = rememberSaveable {
        mutableStateOf(AppPreference.get(this).get(AppPref.NEED_SETUP))
      }
      SampleTheme {
        if (needSetup.value) SetUpDialog(needSetup)
        else {
          val prefFlow = viewModel.prefFlow
          if (prefFlow == null)
            WarnDialog(
              openState = remember { mutableStateOf(true) },
              title = { OneLineText(getString(R.string.warning)) },
              content = { Text(getString(R.string.plzEnableModuleFirst)) },
              onCancel = { finish() },
            ) {
              finish()
            }
          else ProvidePreferenceLocals(flow = prefFlow, myPreferenceTheme()) { SampleScreen() }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    applyIconPackIfNeeded(intent)
  }

  private fun applyIconPackIfNeeded(intent: Intent) {
    val prefFlow = viewModel.prefFlow
    if (prefFlow != null && intent.action == "${BuildConfig.APPLICATION_ID}.APPLY_ICON_PACK") {
      prefFlow.update {
        it.toMutablePreferences().apply {
          set(Pref.ICON_PACK.key, intent.getStringExtra("packageName"))
        }
      }
      Toast.makeText(this, R.string.iconPackApplied, Toast.LENGTH_LONG).show()
    }
  }

  @Composable
  private fun SetUpDialog(needSetup: MutableState<Boolean>) {
    LazyListDialog(
      needSetup,
      title = { OneLineText(stringResource(R.string.chooseMode)) },
      value = listOf(MODE_SHARE, MODE_PROVIDER, MODE_LOCAL),
      dismissible = false,
    ) { mode, dismiss ->
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable {
              viewModel.prefFlow?.update {
                it.toMutablePreferences().apply { set(Pref.MODE.key, mode) }
              }
              AppPreference.get(this).edit { putBoolean(AppPref.NEED_SETUP.key, false) }
              dismiss()
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        MainPreference.ModeToIcon(mode)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text =
            MainPreference.modeToAnnotatedString(this@MainActivity, mode, MaterialTheme.typography),
          color = MaterialTheme.colorScheme.onSurface,
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
  }

  private class Route(val name: String, val icon: ImageVector, val screen: @Composable () -> Unit)

  @Composable
  @OptIn(ExperimentalMaterial3Api::class)
  private fun SampleScreen() {
    val pages = remember {
      listOf(
        Route(getString(R.string.general), Icons.Outlined.Settings) { MainPreference.General() },
        Route(getString(R.string.iconPackSettings), Icons.Outlined.Backpack) {
          MainPreference.IconPack()
        },
        Route(getString(R.string.pixelSettings), Icons.Outlined.PhoneAndroid) {
          MainPreference.Pixel()
        },
      )
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarState = remember { SnackbarHostState() }
    Scaffold(
      modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
      topBar = {
        TopAppBar(
          title = {
            AnimatedContent(targetState = pagerState.currentPage, label = "Title text change") {
              pages.getOrNull(it)?.let { OneLineText(it.name) }
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
      bottomBar = {
        val coroutineScope = rememberCoroutineScope()
        NavigationBar {
          pages.forEachIndexed { i, page ->
            NavigationBarItem(
              icon = { Icon(page.icon, contentDescription = page.name) },
              selected = pagerState.currentPage == i,
              onClick = { coroutineScope.launch { pagerState.animateScrollToPage(i) } },
            )
          }
        }
      },
    ) { contentPadding ->
      if (viewModel.waiting > 0) LoadingDialog()

      val flow = LocalPreferenceFlow.current
      LaunchedEffect(flow) {
        flow
          .map { it.get(Pref.MODE) }
          .distinctUntilChanged()
          .onEach { mode ->
            // Ask for notification permission used for foreground service
            if (
              mode == MODE_PROVIDER &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                  PackageManager.PERMISSION_GRANTED
            )
              requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 200)
          }
          .launchIn(lifecycleScope)
      }

      HorizontalPager(pagerState, contentPadding = contentPadding, beyondViewportPageCount = 2) {
        Box(modifier = Modifier.fillMaxSize()) { pages.getOrNull(it)?.screen() }
      }
    }
  }
}

object MainPreference {
  @Composable
  fun General(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val typography = MaterialTheme.typography
    LazyColumn(modifier = modifier) {
      listPreference(
        icon = { AnimatedContent(it) { ModeToIcon(it) } },
        key = Pref.MODE.key,
        defaultValue = Pref.MODE.def,
        values = listOf(MODE_SHARE, MODE_PROVIDER, MODE_LOCAL),
        valueToText = { modeToAnnotatedString(context, it, typography) },
        title = { TwoLineText(modeToTitle(context, it)) },
        summary = { TwoLineText(modeToSummary(context, it)) },
      )
      mapListPreference(
        icon = { Icon(Icons.Outlined.Backpack, Pref.ICON_PACK.key) },
        key = Pref.ICON_PACK.key,
        defaultValue = Pref.ICON_PACK.def,
        getValueMap = { IconPackApps.flow.collectAsState(null).value },
        item = { key, value, currentKey, onClick -> IconPackItem(key, value, currentKey, onClick) },
        title = { TwoLineText(stringResource(R.string.iconPack)) },
        summary = { key, value ->
          Text(
            value?.label
              ?: key.takeIf { it.isNotEmpty() }
              ?: stringResource(R.string.iconPackSummary)
          )
        },
      )
      switchPreference(
        icon = {},
        key = Pref.ICON_PACK_AS_FALLBACK.key,
        defaultValue = Pref.ICON_PACK_AS_FALLBACK.def,
        title = { TwoLineText(stringResource(R.string.iconPackAsFallback)) },
        summary = { TwoLineText(stringResource(R.string.iconPackAsFallbackSummary)) },
      )
      switchPreference(
        icon = { Icon(Icons.AutoMirrored.Outlined.Shortcut, Pref.SHORTCUT.key) },
        key = Pref.SHORTCUT.key,
        defaultValue = Pref.SHORTCUT.def,
        title = { TwoLineText(stringResource(R.string.shortcut)) },
      )
      preference(
        icon = { Icon(Icons.Outlined.Merge, "openMerger") },
        key = "openMerger",
        onClick = { context.startActivity(Intent(context, IconPackMergerActivity::class.java)) },
        title = { TwoLineText(stringResource(R.string.mergeIconPack)) },
        summary = { TwoLineText(stringResource(R.string.mergeIconPackSummary)) },
      )
    }
  }

  @Composable
  fun IconPack(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    onlyOptions: Boolean = false,
  ) {
    val context = LocalContext.current
    LazyColumn(modifier = modifier, state = state) {
      if (!onlyOptions) {
        myPreference(
          icon = { Icon(Icons.Outlined.Edit, "iconVariant") },
          key = "iconVariant",
          enabled = { it.get(Pref.MODE) != MODE_LOCAL && it.get(Pref.ICON_PACK).isNotEmpty() },
          onClick = { context.startActivity(Intent(context, IconVariantActivity::class.java)) },
          title = { TwoLineText(stringResource(R.string.iconVariant)) },
          summary = { TwoLineText(stringResource(R.string.iconVariantSummary)) },
        )
      }
      switchPreference(
        icon = { Icon(Icons.Outlined.SettingsBackupRestore, Pref.ICON_FALLBACK.key) },
        key = Pref.ICON_FALLBACK.key,
        defaultValue = Pref.ICON_FALLBACK.def,
        title = { TwoLineText(stringResource(R.string.iconFallback)) },
        summary = { TwoLineText(stringResource(R.string.iconFallbackSummary)) },
      )
      mySwitchPreference(
        icon = {},
        key = Pref.SCALE_ONLY_FOREGROUND.key,
        enabled = { it.get(Pref.ICON_FALLBACK) },
        defaultValue = Pref.SCALE_ONLY_FOREGROUND.def,
        title = { TwoLineText(stringResource(R.string.scaleOnlyForeground)) },
      )
      mySwitchPreference(
        icon = {},
        key = Pref.BACK_AS_ADAPTIVE_BACK.key,
        enabled = { it.get(Pref.ICON_FALLBACK) },
        defaultValue = Pref.BACK_AS_ADAPTIVE_BACK.def,
        title = { TwoLineText(stringResource(R.string.backAsAdaptiveBack)) },
      )
      mySliderPreference(
        icon = {},
        key = Pref.NON_ADAPTIVE_SCALE.key,
        enabled = { it.get(Pref.ICON_FALLBACK) },
        defaultValue = Pref.NON_ADAPTIVE_SCALE.def,
        valueRange = 0f..1.5f,
        valueSteps = 29,
        title = { TwoLineText(stringResource(R.string.nonAdaptiveScale)) },
        summary = { OneLineText("%.2f".format(it)) },
        valueToText = { "%.2f".format(it) },
      )
      mySwitchPreference(
        icon = {},
        key = Pref.CONVERT_TO_ADAPTIVE.key,
        enabled = { it.get(Pref.ICON_FALLBACK) },
        defaultValue = Pref.CONVERT_TO_ADAPTIVE.def,
        title = { TwoLineText(stringResource(R.string.convertToAdaptive)) },
        summary = { TwoLineText(stringResource(R.string.convertToAdaptiveSummary)) },
      )
      mySwitchPreference(
        icon = {},
        key = Pref.OVERRIDE_ICON_FALLBACK.key,
        enabled = { it.get(Pref.ICON_FALLBACK) },
        defaultValue = Pref.OVERRIDE_ICON_FALLBACK.def,
        title = { TwoLineText(stringResource(R.string.overrideIconFallback)) },
        summary = { TwoLineText(stringResource(R.string.overrideIconFallbackSummary)) },
      )
      mySliderPreference(
        icon = { Icon(Icons.Outlined.PhotoSizeSelectSmall, Pref.ICON_PACK_SCALE.key) },
        enabled = { it.get(Pref.ICON_FALLBACK) && it.get(Pref.OVERRIDE_ICON_FALLBACK) },
        key = Pref.ICON_PACK_SCALE.key,
        defaultValue = Pref.ICON_PACK_SCALE.def,
        valueRange = 0f..1.5f,
        valueSteps = 29,
        title = { TwoLineText(stringResource(R.string.iconPackScale)) },
        summary = { OneLineText("%.2f".format(it)) },
        valueToText = { "%.2f".format(it) },
      )
    }
  }

  @Composable
  fun Pixel(modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier) {
      textFieldPreference(
        icon = { Icon(Icons.Outlined.Apps, Pref.PIXEL_LAUNCHER_PACKAGE.key) },
        key = Pref.PIXEL_LAUNCHER_PACKAGE.key,
        defaultValue = Pref.PIXEL_LAUNCHER_PACKAGE.def,
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
                onValueChange(TextFieldValue(Pref.PIXEL_LAUNCHER_PACKAGE.def))
              }
            },
          )
        },
        title = { TwoLineText(stringResource(R.string.pixelLauncherPackage)) },
        summary = {
          TwoLineText(
            if (it == Pref.PIXEL_LAUNCHER_PACKAGE.def)
              stringResource(R.string.pixelLauncherPackageSummary)
            else it
          )
        },
      )
      switchPreference(
        icon = {},
        key = Pref.NO_SHADOW.key,
        defaultValue = Pref.NO_SHADOW.def,
        title = { TwoLineText(stringResource(R.string.noShadow)) },
        summary = { TwoLineText(stringResource(R.string.noShadowSummary)) },
      )
      switchPreference(
        icon = {},
        key = Pref.FORCE_LOAD_CLOCK_AND_CALENDAR.key,
        defaultValue = Pref.FORCE_LOAD_CLOCK_AND_CALENDAR.def,
        title = { TwoLineText(stringResource(R.string.forceLoadClockAndCalendar)) },
      )
      switchPreference(
        icon = {},
        key = Pref.FORCE_ACTIVITY_ICON_FOR_TASK.key,
        defaultValue = Pref.FORCE_ACTIVITY_ICON_FOR_TASK.def,
        title = { TwoLineText(stringResource(R.string.forceActivityIconForTask)) },
        summary = { TwoLineText(stringResource(R.string.forceActivityIconForTaskSummary)) },
      )
    }
  }

  fun modeToAnnotatedString(context: Context, mode: String, typography: Typography) =
    buildAnnotatedString {
      withStyle(typography.titleMedium.toSpanStyle()) { append(modeToTitle(context, mode) + "\n") }
      withStyle(typography.bodyMedium.toSpanStyle()) { append(modeToSummary(context, mode)) }
    }

  @Composable
  fun ModeToIcon(mode: String) =
    when (mode) {
      MODE_SHARE -> Icon(Icons.Outlined.Share, mode)
      MODE_PROVIDER -> Icon(Icons.Outlined.SettingsRemote, mode)
      MODE_LOCAL -> Icon(Icons.Outlined.Memory, mode)
      else -> {}
    }

  fun modeToTitle(context: Context, mode: String) =
    when (mode) {
      MODE_SHARE -> context.getString(R.string.shareMode)
      MODE_PROVIDER -> context.getString(R.string.providerMode)
      MODE_LOCAL -> context.getString(R.string.localMode)
      else -> mode
    }

  fun modeToSummary(context: Context, mode: String) =
    when (mode) {
      MODE_SHARE -> context.getString(R.string.shareModeSummary)
      MODE_PROVIDER -> context.getString(R.string.providerModeSummary)
      MODE_LOCAL -> context.getString(R.string.localModeSummary)
      else -> mode
    }
}
