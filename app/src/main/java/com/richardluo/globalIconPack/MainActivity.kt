package com.richardluo.globalIconPack

import android.annotation.SuppressLint
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.MutableStateFlow
import me.zhanghai.compose.preference.Preferences
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.getPreferenceFlow
import me.zhanghai.compose.preference.preferenceCategory
import me.zhanghai.compose.preference.sliderPreference
import me.zhanghai.compose.preference.switchPreference
import me.zhanghai.compose.preference.textFieldPreference

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
      textFieldPreference(
        key = "iconPack",
        defaultValue = "",
        title = { Text(text = stringResource(R.string.iconPack)) },
        textToValue = { it },
        summary = { Text(text = it.ifEmpty { stringResource(R.string.iconPackSummary) }) },
      )
      switchPreference(
        key = "noForceShape",
        defaultValue = true,
        title = { Text(text = stringResource(R.string.noForceShape)) },
        summary = { Text(text = stringResource(R.string.noForceShapeSummary)) },
      )

      preferenceCategory(
        key = "pixelLauncherSettings",
        title = { Text(text = stringResource(R.string.pixelLauncherSettings)) },
      )
      sliderPreference(
        key = "scale",
        defaultValue = 1f,
        valueRange = 0f..1.5f,
        valueSteps = 29,
        valueText = { Text(text = "%.2f".format(it)) },
        title = { Text(text = stringResource(R.string.scale)) },
        summary = { Text(text = stringResource(R.string.scaleSummary)) },
      )
      switchPreference(
        key = "forceLoadClockAndCalendar",
        defaultValue = true,
        title = { Text(text = stringResource(R.string.forceLoadClockAndCalendar)) },
        summary = { Text(text = if (it) "On" else "Off") },
      )

      preferenceCategory(
        key = "iconPackSettings",
        title = { Text(text = stringResource(R.string.iconPackSettings)) },
      )
      switchPreference(
        key = "iconBack",
        defaultValue = true,
        title = { Text(text = stringResource(R.string.iconBack)) },
        summary = { Text(text = if (it) "On" else "Off") },
      )
      switchPreference(
        key = "iconUpon",
        defaultValue = true,
        title = { Text(text = stringResource(R.string.iconUpon)) },
        summary = { Text(text = if (it) "On" else "Off") },
      )
      switchPreference(
        key = "iconMask",
        defaultValue = true,
        title = { Text(text = stringResource(R.string.iconMask)) },
        summary = { Text(text = if (it) "On" else "Off") },
      )
      switchPreference(
        key = "iconScale",
        defaultValue = true,
        title = { Text(text = stringResource(R.string.iconScale)) },
        summary = { Text(text = if (it) "On" else "Off") },
      )
    }
  }
}
