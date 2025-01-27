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
import androidx.compose.material.icons.outlined.FilterList
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.iconPack.IconPackApps
import com.richardluo.globalIconPack.ui.components.AppbarSearchBar
import com.richardluo.globalIconPack.ui.components.IconForApp
import com.richardluo.globalIconPack.ui.components.IconPackItem
import com.richardluo.globalIconPack.ui.components.LazyListDialog
import com.richardluo.globalIconPack.ui.components.LoadingDialog
import com.richardluo.globalIconPack.ui.components.RoundSearchBar
import com.richardluo.globalIconPack.ui.components.SampleTheme
import com.richardluo.globalIconPack.ui.components.WarnDialog
import com.richardluo.globalIconPack.ui.viewModel.IconVariantVM
import com.richardluo.globalIconPack.ui.viewModel.OriginalIcon
import com.richardluo.globalIconPack.ui.viewModel.VariantIcon
import com.richardluo.globalIconPack.ui.viewModel.VariantPackIcon
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
              IconButton(onClick = { resetWarnDialogState.value = true }) {
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

    WarnDialog(
      resetWarnDialogState,
      title = { Text(getString(R.string.restoreDefault)) },
      content = { Text(getString(R.string.restoreDefaultWarning)) },
    ) {
      lifecycleScope.launch { viewModel.restoreDefault() }
    }
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
        val packDialogState = remember { mutableStateOf(false) }
        LazyListDialog(
          packDialogState,
          title = { Text(getString(R.string.iconPack)) },
          value =
            IconPackApps.getFlow(this@IconVariantActivity).collectAsState(mapOf()).value.toList(),
          key = { it.first },
        ) { item, dismiss ->
          IconPackItem(item.first, item.second, viewModel.variantPack.value) {
            viewModel.variantPack.value = item.first
            dismiss()
          }
        }

        RoundSearchBar(
          viewModel.variantSearchText,
          stringResource(R.string.search),
          trailingIcon = {
            IconButton(onClick = { packDialogState.value = true }) {
              Icon(Icons.Outlined.FilterList, contentDescription = "By pack")
            }
          },
        ) {
          Icon(Icons.Outlined.Search, contentDescription = "Search")
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

  private fun LazyGridScope.variantIconItems(icons: List<VariantIcon>) {
    items(icons) { icon ->
      IconForApp(
        when (icon) {
          is OriginalIcon -> stringResource(R.string.originalIcon)
          is VariantPackIcon -> icon.name
          else -> ""
        },
        loadImage = { viewModel.loadIconForSelectedApp(icon) },
      ) {
        lifecycleScope.launch { viewModel.replaceIcon(icon) }
      }
    }
  }
}
