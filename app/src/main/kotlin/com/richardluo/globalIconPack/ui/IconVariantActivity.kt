package com.richardluo.globalIconPack.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.ui.components.AppFilterByType
import com.richardluo.globalIconPack.ui.components.AppIcon
import com.richardluo.globalIconPack.ui.components.AppbarSearchBar
import com.richardluo.globalIconPack.ui.components.IconButtonWithTooltip
import com.richardluo.globalIconPack.ui.components.IconChooserSheet
import com.richardluo.globalIconPack.ui.components.MyDropdownMenu
import com.richardluo.globalIconPack.ui.components.SampleTheme
import com.richardluo.globalIconPack.ui.components.WarnDialog
import com.richardluo.globalIconPack.ui.components.getLabelByType
import com.richardluo.globalIconPack.ui.viewModel.IconChooserVM
import com.richardluo.globalIconPack.ui.viewModel.IconVariantVM
import com.richardluo.globalIconPack.utils.getValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class IconVariantActivity : ComponentActivity() {
  private val viewModel: IconVariantVM by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    runCatching { viewModel.iconPack }
      .getOrElse {
        finish()
        return
      }

    setContent { SampleTheme { Screen() } }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun Screen() {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val resetWarnDialogState = rememberSaveable { mutableStateOf(false) }
    val iconChooser: IconChooserVM = viewModel()

    Scaffold(
      modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
      topBar = {
        Box {
          TopAppBar(
            navigationIcon = {
              IconButtonWithTooltip(Icons.AutoMirrored.Outlined.ArrowBack, "Back") { finish() }
            },
            title = { Text(stringResource(R.string.iconVariant)) },
            actions = {
              IconButtonWithTooltip(Icons.Outlined.Search, stringResource(R.string.search)) {
                lifecycleScope.launch { viewModel.expandSearchBar.value = true }
              }
              var expand by rememberSaveable { mutableStateOf(false) }
              val expandFilter = rememberSaveable { mutableStateOf(false) }
              IconButtonWithTooltip(Icons.Outlined.MoreVert, stringResource(R.string.moreOptions)) {
                expand = true
              }
              MyDropdownMenu(expanded = expand, onDismissRequest = { expand = false }) {
                DropdownMenuItem(
                  leadingIcon = { Icon(Icons.Outlined.FilterList, "filter") },
                  text = { Text(getLabelByType(viewModel.filterType.value)) },
                  onClick = {
                    expand = false
                    expandFilter.value = true
                  },
                )
                DropdownMenuItem(
                  leadingIcon = {
                    Icon(Icons.Outlined.Restore, stringResource(R.string.restoreDefault))
                  },
                  text = { Text(stringResource(R.string.restoreDefault)) },
                  onClick = {
                    resetWarnDialogState.value = true
                    expand = false
                  },
                )
                DropdownMenuItem(
                  leadingIcon = { Checkbox(viewModel.modified.getValue(), onCheckedChange = null) },
                  text = { Text(stringResource(R.string.modified)) },
                  onClick = {
                    lifecycleScope.launch {
                      viewModel.flipModified()
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
                    exportLauncher.launch("${viewModel.iconPack.pack}.xml")
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
              AppFilterByType(expandFilter, viewModel.filterType)
            },
            modifier = Modifier.fillMaxWidth(),
            scrollBehavior = scrollBehavior,
          )

          AppbarSearchBar(viewModel.expandSearchBar, viewModel.searchText)
        }
      },
    ) { contentPadding ->
      val icons = viewModel.filteredIcons.getValue(null)
      if (icons != null)
        LazyVerticalGrid(
          contentPadding = contentPadding,
          modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
          columns = GridCells.Adaptive(minSize = 74.dp),
        ) {
          items(icons, key = { it.first.componentName }) { pair ->
            val (info, entry) = pair
            AppIcon(
              info.label,
              key = entry?.entry?.name,
              loadImage = { viewModel.loadIcon(pair) },
            ) {
              iconChooser.open(info, viewModel.iconPack, entry?.entry?.name, viewModel::replaceIcon)
            }
          }
        }
      else
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(trackColor = MaterialTheme.colorScheme.surfaceVariant)
        }
    }

    IconChooserSheet(iconChooser) { viewModel.loadIcon(it to null) }

    WarnDialog(
      resetWarnDialogState,
      title = { Text(getString(R.string.restoreDefault)) },
      content = { Text(getString(R.string.restoreDefaultWarning)) },
    ) {
      viewModel.restoreDefault()
    }
  }

  private val exportLauncher =
    registerForActivityResult(ActivityResultContracts.CreateDocument("text/xml")) { result ->
      if (result != null) viewModel.export(result)
    }

  private val importLauncher =
    registerForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
      if (result != null) viewModel.import(result)
    }
}
