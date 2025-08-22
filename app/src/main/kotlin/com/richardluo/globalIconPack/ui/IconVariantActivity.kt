package com.richardluo.globalIconPack.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.ui.components.AnimatedNavHost
import com.richardluo.globalIconPack.ui.components.AppFilterButtonGroup
import com.richardluo.globalIconPack.ui.components.AppIcon
import com.richardluo.globalIconPack.ui.components.AppbarSearchBar
import com.richardluo.globalIconPack.ui.components.AutoFillDialog
import com.richardluo.globalIconPack.ui.components.IconButtonWithTooltip
import com.richardluo.globalIconPack.ui.components.LoadingCircle
import com.richardluo.globalIconPack.ui.components.LoadingDialog
import com.richardluo.globalIconPack.ui.components.LocalNavControllerWithArgs
import com.richardluo.globalIconPack.ui.components.MyDropdownMenu
import com.richardluo.globalIconPack.ui.components.SampleTheme
import com.richardluo.globalIconPack.ui.components.WarnDialog
import com.richardluo.globalIconPack.ui.components.navPage
import com.richardluo.globalIconPack.ui.model.AppCompIcon
import com.richardluo.globalIconPack.ui.state.rememberAutoFillState
import com.richardluo.globalIconPack.ui.viewModel.IconVariantVM
import com.richardluo.globalIconPack.utils.consumable
import com.richardluo.globalIconPack.utils.getValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class IconVariantActivity : ComponentActivity() {
  private val vm: IconVariantVM by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    runCatching { vm.iconPack }
      .onFailure {
        finish()
        return
      }

    setContent {
      SampleTheme {
        AnimatedNavHost(
          startDestination = "Main",
          pages =
            arrayOf(
              navPage("Main") { Screen() },
              navPage<AppCompIcon>("AppIconList") {
                val navController = LocalNavControllerWithArgs.current!!
                AppIconListPage({ navController.popBackStack() }, vm, it)
              },
            ),
        )
      }
    }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun Screen() {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val expandSearchBar = rememberSaveable { mutableStateOf(false) }
    val resetWarnDialogState = rememberSaveable { mutableStateOf(false) }

    Scaffold(
      modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
      topBar = {
        Box(contentAlignment = Alignment.TopCenter) {
          TopAppBar(
            navigationIcon = {
              IconButtonWithTooltip(Icons.AutoMirrored.Outlined.ArrowBack, "Back") { finish() }
            },
            title = { Text(stringResource(R.string.iconPack_iconVariant)) },
            actions = {
              IconButtonWithTooltip(Icons.Outlined.Search, stringResource(R.string.common_search)) {
                expandSearchBar.value = true
              }
              var expand by rememberSaveable { mutableStateOf(false) }

              val autoFillState = rememberAutoFillState()
              IconButtonWithTooltip(
                Icons.Outlined.MoreVert,
                stringResource(R.string.common_moreOptions),
              ) {
                expand = true
              }
              MyDropdownMenu(expanded = expand, onDismissRequest = { expand = false }) {
                DropdownMenuItem(
                  leadingIcon = { Icon(Icons.Outlined.FormatColorFill, "auto fill") },
                  text = { Text(stringResource(R.string.autoFill)) },
                  onClick = {
                    autoFillState.open(vm.pack)
                    expand = false
                  },
                )
                DropdownMenuItem(
                  leadingIcon = { Icon(Icons.Outlined.Restore, "restore default") },
                  text = { Text(stringResource(R.string.icons_restoreDefault)) },
                  onClick = {
                    resetWarnDialogState.value = true
                    expand = false
                  },
                )
                DropdownMenuItem(
                  leadingIcon = { Checkbox(vm.modified.getValue(), onCheckedChange = null) },
                  text = { Text(stringResource(R.string.iconVariant_menu_modified)) },
                  onClick = {
                    lifecycleScope.launch {
                      vm.flipModified()
                      delay(100)
                      expand = false
                    }
                  },
                )
                HorizontalDivider()
                DropdownMenuItem(
                  leadingIcon = {
                    Icon(Icons.Outlined.Upload, stringResource(R.string.iconVariant_menu_export))
                  },
                  text = { Text(stringResource(R.string.iconVariant_menu_export)) },
                  onClick = {
                    exportLauncher.launch("${vm.pack}.xml")
                    expand = false
                  },
                )
                DropdownMenuItem(
                  leadingIcon = {
                    Icon(Icons.Outlined.Download, stringResource(R.string.iconVariant_menu_import))
                  },
                  text = { Text(stringResource(R.string.iconVariant_menu_import)) },
                  onClick = {
                    importLauncher.launch(arrayOf("text/xml"))
                    expand = false
                  },
                )
              }
              AutoFillDialog(autoFillState) { vm.autoFill(it) }
            },
            modifier = Modifier.fillMaxWidth(),
            scrollBehavior = scrollBehavior,
          )

          AppbarSearchBar(expandSearchBar, vm.searchText)
        }
      },
    ) { contentPadding ->
      val navController = LocalNavControllerWithArgs.current!!
      val density = LocalDensity.current
      val consumablePadding = contentPadding.consumable()

      Box(modifier = Modifier.padding(consumablePadding.consumeTop())) {
        var filterHeight by remember { mutableStateOf(0.dp) }

        val icons = vm.filteredIcons.getValue()
        if (icons != null)
          LazyVerticalGrid(
            contentPadding = consumablePadding.apply { top += filterHeight }.consume(),
            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
            columns = GridCells.Adaptive(minSize = 74.dp),
          ) {
            items(icons, key = { it.info.componentName }) {
              val (info, entry) = it
              AppIcon(
                info.label,
                key = entry?.entry?.name,
                loadImage = { vm.loadIcon(it) },
                shareKey = info.componentName.packageName,
              ) {
                navController.navigate("AppIconList", it)
              }
            }
          }
        else LoadingCircle(modifier = Modifier.fillMaxSize())

        AppFilterButtonGroup(
          Modifier.padding(horizontal = 8.dp).fillMaxWidth().onGloballyPositioned {
            filterHeight = with(density) { it.size.height.toDp() }
          },
          vm.filterType,
        )
      }

      if (vm.loading > 0) LoadingDialog()

      WarnDialog(
        resetWarnDialogState,
        title = { Text(getString(R.string.icons_restoreDefault)) },
        onOk = { vm.restoreDefault() },
      ) {
        Text(getString(R.string.icons_warn_restoreDefault))
      }
    }
  }

  private val exportLauncher =
    registerForActivityResult(ActivityResultContracts.CreateDocument("text/xml")) { result ->
      result ?: return@registerForActivityResult
      vm.export(result)
    }

  private val importLauncher =
    registerForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
      result ?: return@registerForActivityResult
      vm.import(result)
    }
}
