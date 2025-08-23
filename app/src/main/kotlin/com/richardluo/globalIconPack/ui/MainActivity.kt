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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backpack
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TonalToggleButton
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
import com.richardluo.globalIconPack.ui.MainPreference.ModeItem
import com.richardluo.globalIconPack.ui.components.CustomSnackbar
import com.richardluo.globalIconPack.ui.components.LazyListDialog
import com.richardluo.globalIconPack.ui.components.LoadingDialog
import com.richardluo.globalIconPack.ui.components.OneLineText
import com.richardluo.globalIconPack.ui.components.SampleTheme
import com.richardluo.globalIconPack.ui.components.WarnDialog
import com.richardluo.globalIconPack.ui.components.myPreferenceTheme
import com.richardluo.globalIconPack.ui.components.pinnedScrollBehaviorWithPager
import com.richardluo.globalIconPack.ui.viewModel.MainVM
import com.richardluo.globalIconPack.utils.AppPreference
import com.richardluo.globalIconPack.utils.consumable
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
            title = { OneLineText(getString(R.string.common_warning)) },
            onOk = { finish() },
            onCancel = { finish() },
          ) {
            Text(getString(R.string.warn_enableModule))
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
      Toast.makeText(this, R.string.info_iconPackApplied, Toast.LENGTH_LONG).show()
    }
  }

  @Composable
  private fun SetUpDialog(state: MutableState<Boolean>, prefFlow: MutableStateFlow<Preferences>) {
    LazyListDialog(
      state,
      title = { OneLineText(stringResource(R.string.setup_chooseMode)) },
      value = listOf(MODE_SHARE, MODE_PROVIDER, MODE_LOCAL),
      dismissible = false,
    ) { pos, mode, dismiss ->
      ModeItem(pos, mode) {
        prefFlow.update { it.toMutablePreferences().apply { set(Pref.MODE.key, mode) } }
        AppPreference.get().edit { putBoolean(AppPref.NEED_SETUP.key, false) }
        dismiss()
      }
    }
  }

  private class Page(
    val name: String,
    val icon: ImageVector,
    val screen: @Composable (PaddingValues) -> Unit,
  )

  @Composable
  @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
  private fun SampleScreen() {
    val pages = remember {
      arrayOf(
        Page(getString(R.string.general_settings), Icons.Outlined.Settings) {
          MainPreference.General(contentPadding = it)
        },
        Page(getString(R.string.iconPack_settings), Icons.Outlined.Backpack) {
          MainPreference.IconPack(contentPadding = it)
        },
        Page(getString(R.string.pixel_settings), Icons.Outlined.PhoneAndroid) {
          MainPreference.Pixel(contentPadding = it)
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
      snackbarHost = { SnackbarHost(hostState = snackbarState, snackbar = { CustomSnackbar(it) }) },
    ) { contentPadding ->
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

      Box {
        val toolBarBottomPadding = 8.dp
        val consumablePadding = contentPadding.consumable()
        val pagePadding =
          PaddingValues(
            bottom =
              consumablePadding.consumeBottomValue() +
                toolBarBottomPadding +
                FloatingToolbarDefaults.ContainerSize
          )

        HorizontalPager(
          pagerState,
          contentPadding = consumablePadding.consume(),
          beyondViewportPageCount = 2,
        ) {
          Box(modifier = Modifier.fillMaxSize()) { pages.getOrNull(it)?.screen(pagePadding) }
        }

        val coroutineScope = rememberCoroutineScope()
        HorizontalFloatingToolbar(
          modifier =
            Modifier.align(Alignment.BottomCenter)
              .padding(bottom = contentPadding.calculateBottomPadding() + toolBarBottomPadding),
          expanded = true,
        ) {
          pages.forEachIndexed { i, page ->
            val checked = pagerState.currentPage == i
            TonalToggleButton(
              checked,
              { coroutineScope.launch { pagerState.animateScrollToPage(i) } },
              modifier = Modifier.padding(horizontal = 3.dp),
            ) {
              Icon(page.icon, contentDescription = page.name)
              AnimatedVisibility(checked) {
                Text(page.name, modifier = Modifier.padding(start = 8.dp))
              }
            }
          }
        }
      }

      if (vm.loading > 0) LoadingDialog()
    }
  }
}
