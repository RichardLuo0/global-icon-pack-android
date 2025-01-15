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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.ui.components.AppbarSearchBar
import com.richardluo.globalIconPack.ui.components.IconForApp
import com.richardluo.globalIconPack.ui.components.LoadingDialog
import com.richardluo.globalIconPack.ui.components.RoundSearchBar
import com.richardluo.globalIconPack.ui.components.SampleTheme
import com.richardluo.globalIconPack.ui.viewModel.IconVariantVM
import com.richardluo.globalIconPack.utils.getValue
import kotlinx.coroutines.launch

class IconVariantActivity : ComponentActivity() {
  private val viewModel: IconVariantVM by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { SampleTheme { Screen() } }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Preview
  @Composable
  private fun Screen() {
    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
      modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
      topBar = {
        Box {
          TopAppBar(
            navigationIcon = {
              IconButton(onClick = { finish() }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
              }
            },
            title = { Text(stringResource(R.string.iconVariant)) },
            actions = {
              IconButton(
                onClick = { lifecycleScope.launch { viewModel.expandSearchBar.value = true } }
              ) {
                Icon(Icons.Outlined.Search, stringResource(R.string.search))
              }
              IconButton(onClick = { lifecycleScope.launch { viewModel.restoreDefault() } }) {
                Icon(Icons.Outlined.Restore, stringResource(R.string.restoreDefault))
              }
            },
            modifier = Modifier.fillMaxWidth(),
            windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            scrollBehavior = scrollBehavior,
          )

          AppbarSearchBar(viewModel.expandSearchBar, viewModel.searchText)
        }
      },
      contentWindowInsets = windowInsets,
    ) { contentPadding ->
      val icons = viewModel.filteredIcons.getValue(null)
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

    SelectVariantIcon()

    if (viewModel.isLoading) LoadingDialog()
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun SelectVariantIcon() {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    LaunchedEffect(viewModel.variantSheet) {
      if (viewModel.variantSheet) sheetState.show()
      else {
        sheetState.hide()
        viewModel.variantSearchText.value = ""
      }
    }
    if (viewModel.variantSheet)
      ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = { viewModel.variantSheet = false },
      ) {
        RoundSearchBar(viewModel.variantSearchText, stringResource(R.string.search)) {
          Icon(Icons.Default.Search, contentDescription = "Search")
        }
        val suggestIcons = viewModel.suggestVariantIcons.getValue(null)
        if (suggestIcons != null)
          if (viewModel.variantSearchText.value.isEmpty()) {
            val variantIcons = viewModel.variantIcons.getValue(null) ?: setOf()
            LazyVerticalGrid(
              modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
              columns = GridCells.Adaptive(minSize = 74.dp),
            ) {
              variantIconTitle(getString(R.string.suggestedIcons))
              variantIconItems(suggestIcons)
              variantIconTitle(getString(R.string.allIcons))
              variantIconItems(variantIcons.toList())
            }
          } else
            LazyVerticalGrid(
              modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
              columns = GridCells.Adaptive(minSize = 74.dp),
            ) {
              variantIconItems(suggestIcons)
            }
        else
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(24.dp))
          }
      }
  }

  private fun LazyGridScope.variantIconTitle(text: String) {
    item(span = { GridItemSpan(maxLineSpan) }, contentType = "Title") {
      Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 12.dp, start = 12.dp, end = 12.dp),
      )
    }
  }

  private fun LazyGridScope.variantIconItems(icons: List<String>) {
    items(icons, key = { it }) { name ->
      IconForApp(
        name.ifEmpty { stringResource(R.string.originalIcon) },
        loadImage = { viewModel.loadIcon(name) },
      ) {
        lifecycleScope.launch { viewModel.replaceIcon(name) }
      }
    }
  }
}
