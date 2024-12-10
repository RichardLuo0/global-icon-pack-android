package com.richardluo.globalIconPack.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.richardluo.globalIconPack.MODE_LOCAL
import com.richardluo.globalIconPack.MODE_PROVIDER
import com.richardluo.globalIconPack.PrefKey
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.iconPack.IconPackApps
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.registerAndCallOnSharedPreferenceChangeListener
import com.topjohnwu.superuser.Shell
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.getPreferenceFlow
import me.zhanghai.compose.preference.listPreference
import me.zhanghai.compose.preference.preferenceCategory
import me.zhanghai.compose.preference.rememberPreferenceState
import me.zhanghai.compose.preference.sliderPreference
import me.zhanghai.compose.preference.switchPreference

class MainActivity : ComponentActivity() {

  companion object {
    init {
      Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER))
    }
  }

  private lateinit var pref: SharedPreferences
  private val modeChangeListener = ModeChangeListener(this)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    pref = WorldPreference.getPrefInApp(this)
    pref.registerAndCallOnSharedPreferenceChangeListener(modeChangeListener, PrefKey.MODE)
    setContent {
      SampleTheme { ProvidePreferenceLocals(flow = pref.getPreferenceFlow()) { SampleScreen() } }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    pref.unregisterOnSharedPreferenceChangeListener(modeChangeListener)
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SampleScreen() {
  val context = LocalContext.current
  val windowInsets = WindowInsets.safeDrawing
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
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
        actions = { MainDropdownMenu(snackbarState) },
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

private fun modeToDesc(context: Context, mode: String) =
  when (mode) {
    MODE_PROVIDER -> context.getString(R.string.modeProvider)
    MODE_LOCAL -> context.getString(R.string.modeLocal)
    else -> mode
  }
