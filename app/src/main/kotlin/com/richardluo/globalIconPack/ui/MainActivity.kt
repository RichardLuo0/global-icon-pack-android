package com.richardluo.globalIconPack.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.richardluo.globalIconPack.PrefKey
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.WorldPreference
import kotlinx.coroutines.flow.MutableStateFlow
import me.zhanghai.compose.preference.Preferences
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.getPreferenceFlow
import me.zhanghai.compose.preference.preferenceCategory
import me.zhanghai.compose.preference.rememberPreferenceState
import me.zhanghai.compose.preference.sliderPreference
import me.zhanghai.compose.preference.switchPreference

@Composable
@SuppressLint("WorldReadableFiles")
fun worldPreferenceFlow(): MutableStateFlow<Preferences> {
  val context = LocalContext.current
  return WorldPreference.getWritablePref(context).getPreferenceFlow()
}

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      SampleTheme { ProvidePreferenceLocals(flow = worldPreferenceFlow()) { SampleScreen() } }
    }
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SampleScreen() {
  val context = LocalContext.current
  val windowInsets = WindowInsets.safeDrawing
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
  Scaffold(
    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      val appLabel = context.applicationInfo.loadLabel(context.packageManager).toString()
      TopAppBar(
        title = { Text(text = appLabel) },
        modifier = Modifier.fillMaxWidth(),
        windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        scrollBehavior = scrollBehavior,
      )
    },
    containerColor = Color.Transparent,
    contentColor = contentColorFor(MaterialTheme.colorScheme.background),
    contentWindowInsets = windowInsets,
  ) { contentPadding ->
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
      preferenceCategory(key = "general", title = { Text(text = stringResource(R.string.general)) })
      lazyListPreference(
        key = PrefKey.ICON_PACK,
        defaultValue = "",
        load = {
          mutableMapOf<String, String>().apply {
            val pm = context.packageManager
            listOf(
                "app.lawnchair.icons.THEMED_ICON",
                "org.adw.ActivityStarter.THEMES",
                "com.novalauncher.THEME",
              )
              .forEach { action ->
                pm.queryIntentActivities(Intent(action), 0).forEach { put(pm, it) }
              }
          }
        },
        item = { value, currentValue, valueMap, onClick ->
          IconPackItem(value, currentValue, valueMap, onClick)
        },
        title = { Text(text = stringResource(R.string.iconPack)) },
        summary = { Text(text = it.ifEmpty { stringResource(R.string.iconPackSummary) }) },
      )
      switchPreference(
        key = PrefKey.NO_FORCE_SHAPE,
        defaultValue = false,
        title = { Text(text = stringResource(R.string.noForceShape)) },
        summary = { Text(text = stringResource(R.string.noForceShapeSummary)) },
      )
      switchPreference(
        key = PrefKey.ICON_PACK_AS_FALLBACK,
        defaultValue = false,
        title = { Text(text = stringResource(R.string.iconPackAsFallback)) },
        summary = { Text(text = stringResource(R.string.iconPackAsFallbackSummary)) },
      )

      preferenceCategory(
        key = "launcherSettings",
        title = { Text(text = stringResource(R.string.launcherSettings)) },
      )
      sliderPreference(
        key = PrefKey.SCALE,
        defaultValue = 1f,
        valueRange = 0f..1.5f,
        valueSteps = 29,
        valueText = { Text(text = "%.2f".format(it)) },
        title = { Text(text = stringResource(R.string.scale)) },
        summary = { Text(text = stringResource(R.string.scaleSummary)) },
      )
      switchPreference(
        key = PrefKey.FORCE_LOAD_CLOCK_AND_CALENDAR,
        defaultValue = true,
        title = { Text(text = stringResource(R.string.forceLoadClockAndCalendar)) },
        summary = { Text(text = if (it) "On" else "Off") },
      )

      preferenceCategory(
        key = "iconPackSettings",
        title = { Text(text = stringResource(R.string.iconPackSettings)) },
      )
      switchPreference(
        key = PrefKey.ICON_FALLBACK,
        defaultValue = true,
        title = { Text(text = stringResource(R.string.iconFallback)) },
        summary = { Text(text = stringResource(R.string.iconFallbackSummary)) },
      )
      item {
        val enableState = rememberPreferenceState(PrefKey.OVERRIDE_ICON_FALLBACK, false)
        val enabled by enableState
        SwitchPreference(
          state = enableState,
          title = { Text(text = stringResource(R.string.overrideIconFallback)) },
          summary = { Text(text = stringResource(R.string.overrideIconFallbackSummary)) },
        )
        ComposableSliderPreference(
          enabled = { enabled },
          key = PrefKey.ICON_PACK_SCALE,
          defaultValue = 1f,
          valueRange = 0f..1.5f,
          valueSteps = 29,
          valueText = { Text(text = "%.2f".format(it)) },
          title = { Text(text = stringResource(R.string.iconPackScale)) },
        )
      }
    }
  }
}

private fun MutableMap<String, String>.put(pm: PackageManager, resolveInfo: ResolveInfo) =
  resolveInfo.activityInfo.applicationInfo.let { put(it.packageName, it.loadLabel(pm).toString()) }

@Composable
private fun IconPackItem(
  value: String,
  currentValue: String,
  valueMap: Map<String, String>,
  onClick: () -> Unit,
) {
  val selected = value == currentValue
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .heightIn(min = 48.dp)
        .selectable(selected, true, Role.RadioButton, onClick)
        .padding(horizontal = 24.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = null)
    Spacer(modifier = Modifier.width(24.dp))
    Column(modifier = Modifier.fillMaxWidth()) {
      Text(
        text = valueMap[value] ?: "Unknown app",
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
      )
      Text(
        text = value,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
      )
    }
  }
}
