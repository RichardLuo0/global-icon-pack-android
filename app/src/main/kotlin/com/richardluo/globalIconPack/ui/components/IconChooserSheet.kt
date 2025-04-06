package com.richardluo.globalIconPack.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.iconPack.IconPackApps
import com.richardluo.globalIconPack.ui.model.AppIconInfo
import com.richardluo.globalIconPack.ui.model.OriginalIcon
import com.richardluo.globalIconPack.ui.model.VariantIcon
import com.richardluo.globalIconPack.ui.model.VariantPackIcon
import com.richardluo.globalIconPack.ui.viewModel.IconChooserVM
import com.richardluo.globalIconPack.utils.getValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconChooserSheet(
  viewModel: IconChooserVM = viewModel(),
  replaceIcon: (AppIconInfo, VariantIcon) -> Unit,
) {
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

  LaunchedEffect(viewModel.variantSheet) {
    if (viewModel.variantSheet) sheetState.show()
    else {
      sheetState.hide()
      viewModel.searchText.value = ""
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
        title = { Text(stringResource(R.string.iconPack)) },
        value = IconPackApps.getFlow(context).collectAsState(mapOf()).value.toList(),
        key = { it.first },
      ) { item, dismiss ->
        IconPackItem(item.first, item.second, viewModel.pack) {
          viewModel.pack = item.first
          dismiss()
        }
      }

      RoundSearchBar(
        viewModel.searchText,
        stringResource(R.string.search),
        modifier = Modifier.padding(bottom = 8.dp),
        trailingIcon = {
          IconButtonWithTooltip(Icons.Outlined.FilterList, "By pack") {
            packDialogState.value = true
          }
        },
      ) {
        Icon(Icons.Outlined.Search, contentDescription = "Search")
      }

      fun LazyGridScope.variantIconTitle(text: String) {
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "Title") {
          Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp, start = 12.dp, end = 12.dp),
          )
        }
      }

      fun LazyGridScope.variantIconItems(icons: List<VariantIcon>) {
        items(icons) { icon ->
          AppIcon(
            when (icon) {
              is OriginalIcon -> stringResource(R.string.originalIcon)
              is VariantPackIcon -> icon.entry.name
              else -> ""
            },
            loadImage = { viewModel.loadIconForSelectedApp(icon) },
          ) {
            replaceIcon(viewModel.appInfo ?: return@AppIcon, icon)
            viewModel.variantSheet = false
          }
        }
      }

      val suggestIcons = viewModel.suggestIcons.getValue(null)
      if (suggestIcons != null)
        if (viewModel.searchText.value.isEmpty()) {
          val variantIcons = viewModel.icons.getValue(null) ?: setOf()
          LazyVerticalGrid(
            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
            columns = GridCells.Adaptive(minSize = 74.dp),
          ) {
            variantIconTitle(context.getString(R.string.suggestedIcons))
            variantIconItems(suggestIcons)
            variantIconTitle(context.getString(R.string.allIcons))
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
