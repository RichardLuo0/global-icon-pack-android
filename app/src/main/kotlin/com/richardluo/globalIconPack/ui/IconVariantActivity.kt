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
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.ui.components.AnimatedNavHost
import com.richardluo.globalIconPack.ui.components.AppFilterByType
import com.richardluo.globalIconPack.ui.components.AppIcon
import com.richardluo.globalIconPack.ui.components.AppbarSearchBar
import com.richardluo.globalIconPack.ui.components.AutoFillDialog
import com.richardluo.globalIconPack.ui.components.IconButtonWithTooltip
import com.richardluo.globalIconPack.ui.components.LoadingDialog
import com.richardluo.globalIconPack.ui.components.MyDropdownMenu
import com.richardluo.globalIconPack.ui.components.SampleTheme
import com.richardluo.globalIconPack.ui.components.WarnDialog
import com.richardluo.globalIconPack.ui.components.getLabelByType
import com.richardluo.globalIconPack.ui.components.navPage
import com.richardluo.globalIconPack.ui.viewModel.AutoFillVM
import com.richardluo.globalIconPack.ui.viewModel.IconVariantVM
import com.richardluo.globalIconPack.utils.getValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class IconVariantAVM : ViewModel() {
  var waiting by mutableIntStateOf(0)
}

class IconVariantActivity : ComponentActivity() {
  private lateinit var navController: NavHostController
  private val vm: IconVariantVM by viewModels()
  private val avm: IconVariantAVM by viewModels()

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
        navController = rememberNavController()
        AnimatedNavHost(navController = navController, startDestination = "Main") {
          navPage("Main") { Screen() }
          navPage("AppIconList") {
            AppIconListPage({ navController.popBackStack() }, vm, vm.appIconListVM)
          }
        }
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
            title = { Text(stringResource(R.string.iconVariant)) },
            actions = {
              IconButtonWithTooltip(Icons.Outlined.Search, stringResource(R.string.search)) {
                expandSearchBar.value = true
              }
              var expand by rememberSaveable { mutableStateOf(false) }
              val expandFilter = rememberSaveable { mutableStateOf(false) }
              val autoFillVM: AutoFillVM = viewModel()
              IconButtonWithTooltip(Icons.Outlined.MoreVert, stringResource(R.string.moreOptions)) {
                expand = true
              }
              MyDropdownMenu(expanded = expand, onDismissRequest = { expand = false }) {
                DropdownMenuItem(
                  leadingIcon = { Icon(Icons.Outlined.FilterList, "filter") },
                  text = { Text(getLabelByType(vm.filterType.value)) },
                  onClick = {
                    expand = false
                    expandFilter.value = true
                  },
                )
                DropdownMenuItem(
                  leadingIcon = { Icon(Icons.Outlined.FormatColorFill, "auto fill") },
                  text = { Text(stringResource(R.string.autoFill)) },
                  onClick = {
                    autoFillVM.open(vm.pack)
                    expand = false
                  },
                )
                DropdownMenuItem(
                  leadingIcon = { Icon(Icons.Outlined.Restore, "restore default") },
                  text = { Text(stringResource(R.string.restoreDefault)) },
                  onClick = {
                    resetWarnDialogState.value = true
                    expand = false
                  },
                )
                DropdownMenuItem(
                  leadingIcon = { Checkbox(vm.modified.getValue(), onCheckedChange = null) },
                  text = { Text(stringResource(R.string.modified)) },
                  onClick = {
                    lifecycleScope.launch {
                      vm.flipModified()
                      delay(100)
                      expand = false
                    }
                  },
                )
                DropdownMenuItem(
                  leadingIcon = {
                    Icon(Icons.Outlined.Upload, stringResource(R.string.exportIconPack))
                  },
                  text = { Text(stringResource(R.string.exportIconPack)) },
                  onClick = {
                    exportLauncher.launch("${vm.pack}.xml")
                    expand = false
                  },
                )
                DropdownMenuItem(
                  leadingIcon = {
                    Icon(Icons.Outlined.Download, stringResource(R.string.importIconPack))
                  },
                  text = { Text(stringResource(R.string.importIconPack)) },
                  onClick = {
                    importLauncher.launch(arrayOf("text/xml"))
                    expand = false
                  },
                )
              }
              AppFilterByType(expandFilter, vm.filterType)
              AutoFillDialog(autoFillVM) {
                lifecycleScope.launch {
                  avm.waiting++
                  vm.autoFill(it)
                  avm.waiting--
                }
              }
            },
            modifier = Modifier.fillMaxWidth(),
            scrollBehavior = scrollBehavior,
          )

          AppbarSearchBar(expandSearchBar, vm.searchText)
        }
      },
    ) { contentPadding ->
      val icons = vm.filteredIcons.getValue(null)
      if (icons != null)
        LazyVerticalGrid(
          contentPadding = contentPadding,
          modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
          columns = GridCells.Adaptive(minSize = 74.dp),
        ) {
          items(icons, key = { it.first.componentName }) {
            val (info, entry) = it
            AppIcon(
              info.label,
              key = entry?.entry?.name,
              loadImage = { vm.loadIcon(it) },
              shareKey = info.componentName.packageName,
            ) {
              vm.appIconListVM.setup(it)
              navController.navigate("AppIconList")
            }
          }
        }
      else
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(trackColor = MaterialTheme.colorScheme.surfaceVariant)
        }

      if (avm.waiting > 0) LoadingDialog()

      WarnDialog(
        resetWarnDialogState,
        title = { Text(getString(R.string.restoreDefault)) },
        onOk = { vm.restoreDefault() },
      ) {
        Text(getString(R.string.restoreDefaultWarning))
      }
    }
  }

  private val exportLauncher =
    registerForActivityResult(ActivityResultContracts.CreateDocument("text/xml")) { result ->
      result ?: return@registerForActivityResult
      lifecycleScope.launch {
        avm.waiting++
        runCatching { vm.export(result).await() }
        avm.waiting--
      }
    }

  private val importLauncher =
    registerForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
      result ?: return@registerForActivityResult
      lifecycleScope.launch {
        avm.waiting++
        runCatching { vm.import(result).await() }
        avm.waiting--
      }
    }
}
