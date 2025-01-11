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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.ui.components.IconForApp
import com.richardluo.globalIconPack.ui.components.LoadingDialog
import com.richardluo.globalIconPack.ui.components.SampleTheme
import com.richardluo.globalIconPack.ui.viewModel.IconVariantVM
import com.richardluo.globalIconPack.utils.getState
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
        TopAppBar(
          navigationIcon = {
            IconButton(onClick = { finish() }) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
            }
          },
          title = { Text(stringResource(R.string.iconVariant)) },
          actions = {
            IconButton(onClick = { lifecycleScope.launch { viewModel.restoreDefault() } }) {
              Icon(Icons.Outlined.Restore, stringResource(R.string.restoreDefault))
            }
          },
          modifier = Modifier.fillMaxWidth(),
          windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
          scrollBehavior = scrollBehavior,
        )
      },
      contentWindowInsets = windowInsets,
    ) { contentPadding ->
      LazyVerticalGrid(
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
        columns = GridCells.Adaptive(minSize = 74.dp),
      ) {
        items(viewModel.icons.toList(), key = { entry -> entry.first }) { (cn, info) ->
          IconForApp(
            info.label,
            key = info.entry?.entry?.name,
            loadImage = { viewModel.loadIcon(info) },
          ) {
            viewModel.openVariantSheet(cn)
          }
        }
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
      if (viewModel.variantSheet) sheetState.show() else sheetState.hide()
    }
    if (viewModel.variantSheet)
      ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = { viewModel.variantSheet = false },
      ) {
        TextField(
          value = viewModel.searchText.getValue(),
          onValueChange = { viewModel.searchText.value = it },
          placeholder = { Text(stringResource(R.string.searchVariants)) },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          shape = MaterialTheme.shapes.extraLarge,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
          colors =
            TextFieldDefaults.colors(
              focusedIndicatorColor = Color.Transparent,
              unfocusedIndicatorColor = Color.Transparent,
              disabledIndicatorColor = Color.Transparent,
            ),
        )
        val iconsFromVM by viewModel.suggestVariantIcons.getState(null)
        val icons = iconsFromVM
        if (icons != null)
          LazyVerticalGrid(
            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
            columns = GridCells.Adaptive(minSize = 74.dp),
          ) {
            items(icons, key = { it }) { name ->
              IconForApp(name, loadImage = { viewModel.loadIcon(name) }) {
                lifecycleScope.launch { viewModel.replaceIcon(name) }
              }
            }
          }
        else
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(24.dp))
          }
      }
  }
}
