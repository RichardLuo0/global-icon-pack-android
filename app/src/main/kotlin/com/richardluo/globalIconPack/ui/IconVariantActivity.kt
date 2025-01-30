package com.richardluo.globalIconPack.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.ui.components.AppbarSearchBar
import com.richardluo.globalIconPack.ui.components.ChooseIconSheet
import com.richardluo.globalIconPack.ui.components.IconButtonWithTooltip
import com.richardluo.globalIconPack.ui.components.IconForApp
import com.richardluo.globalIconPack.ui.components.LoadingDialog
import com.richardluo.globalIconPack.ui.components.SampleTheme
import com.richardluo.globalIconPack.ui.components.WarnDialog
import com.richardluo.globalIconPack.ui.viewModel.IconVariantVM
import com.richardluo.globalIconPack.utils.getValue
import kotlinx.coroutines.launch

class IconVariantActivity : ComponentActivity() {
  private val viewModel: IconVariantVM by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (viewModel.basePack.isEmpty()) {
      finish()
      return
    }

    setContent { SampleTheme { Screen() } }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Preview
  @Composable
  private fun Screen() {
    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val resetWarnDialogState = remember { mutableStateOf(false) }

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
              var expanded by remember { mutableStateOf(false) }
              IconButtonWithTooltip(Icons.Outlined.MoreVert, stringResource(R.string.moreOptions)) {
                expanded = true
              }
              DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                viewModel.filterAppsVM.systemOnly.let {
                  DropdownMenuItem(
                    leadingIcon = {
                      Icon(Icons.Outlined.FilterList, stringResource(R.string.restoreDefault))
                    },
                    text = {
                      Text(
                        stringResource(
                          if (it.value) R.string.showUserApp else R.string.showSystemApp
                        )
                      )
                    },
                    onClick = {
                      it.value = !it.value
                      expanded = false
                    },
                  )
                }
                DropdownMenuItem(
                  leadingIcon = {
                    Icon(Icons.Outlined.Restore, stringResource(R.string.restoreDefault))
                  },
                  text = { Text(stringResource(R.string.restoreDefault)) },
                  onClick = {
                    resetWarnDialogState.value = true
                    expanded = false
                  },
                )
                DropdownMenuItem(
                  leadingIcon = { Checkbox(viewModel.modified.getValue(), onCheckedChange = null) },
                  text = { Text(stringResource(R.string.modified)) },
                  onClick = {
                    lifecycleScope.launch { viewModel.flipModified() }
                    expanded = false
                  },
                )
              }
            },
            modifier = Modifier.fillMaxWidth(),
            windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            scrollBehavior = scrollBehavior,
          )

          AppbarSearchBar(viewModel.expandSearchBar, viewModel.filterAppsVM.searchText)
        }
      },
      contentWindowInsets = windowInsets,
    ) { contentPadding ->
      val icons = viewModel.filterAppsVM.filteredApps.getValue(null)
      if (icons != null)
        LazyVerticalGrid(
          contentPadding = contentPadding,
          modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
          columns = GridCells.Adaptive(minSize = 74.dp),
        ) {
          items(icons.toList(), key = { entry -> entry.first }) { (cn, info) ->
            IconForApp(
              info.label,
              key = info.entry?.entry?.name,
              loadImage = { viewModel.loadIcon(info) },
            ) {
              viewModel.openVariantSheet(cn)
            }
          }
        }
      else
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(24.dp))
        }
    }

    ChooseIconSheet(viewModel.chooseIconVM) { lifecycleScope.launch { viewModel.replaceIcon(it) } }

    if (viewModel.isLoading) LoadingDialog()

    WarnDialog(
      resetWarnDialogState,
      title = { Text(getString(R.string.restoreDefault)) },
      content = { Text(getString(R.string.restoreDefaultWarning)) },
    ) {
      lifecycleScope.launch { viewModel.restoreDefault() }
    }
  }
}
