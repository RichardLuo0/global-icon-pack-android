package com.richardluo.globalIconPack.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backpack
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
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
import com.richardluo.globalIconPack.ui.components.LazyListDialog
import com.richardluo.globalIconPack.ui.components.LoadingDialog
import com.richardluo.globalIconPack.ui.components.OneLineText
import com.richardluo.globalIconPack.ui.components.SampleTheme
import com.richardluo.globalIconPack.ui.components.SnackbarErrorVisuals
import com.richardluo.globalIconPack.ui.components.WarnDialog
import com.richardluo.globalIconPack.ui.components.myPreferenceTheme
import com.richardluo.globalIconPack.ui.components.pinnedScrollBehaviorWithPager
import com.richardluo.globalIconPack.ui.viewModel.MainVM
import com.richardluo.globalIconPack.utils.AppPreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.LocalPreferenceFlow
import me.zhanghai.compose.preference.Preferences
import me.zhanghai.compose.preference.ProvidePreferenceLocals

class MainActivity : ComponentActivity() {
  private val vm: MainVM by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    // If onNewIntent() is not getting called
    applyIconPackIfNeeded(intent)

    setContent {
      SampleTheme {
        val prefFlow = vm.prefFlow
        if (prefFlow == null)
          WarnDialog(
            openState = remember { mutableStateOf(true) },
            title = { OneLineText(getString(R.string.warning)) },
            onOk = { finish() },
            onCancel = { finish() },
          ) {
            Text(getString(R.string.plzEnableModuleFirst))
          }
        else {
          val setupDialogState = rememberSaveable {
            mutableStateOf(AppPreference.get().get(AppPref.NEED_SETUP))
          }
          if (setupDialogState.value) SetUpDialog(setupDialogState, prefFlow)
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
    val prefFlow = vm.prefFlow
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
  private fun SetUpDialog(state: MutableState<Boolean>, prefFlow: MutableStateFlow<Preferences>) {
    LazyListDialog(
      state,
      title = { OneLineText(stringResource(R.string.chooseMode)) },
      value = listOf(MODE_SHARE, MODE_PROVIDER, MODE_LOCAL),
      dismissible = false,
    ) { mode, dismiss ->
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable {
              prefFlow.update { it.toMutablePreferences().apply { set(Pref.MODE.key, mode) } }
              AppPreference.get().edit { putBoolean(AppPref.NEED_SETUP.key, false) }
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
    val scrollBehavior = pinnedScrollBehaviorWithPager(pagerState)
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
      if (vm.waiting > 0) LoadingDialog()

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
