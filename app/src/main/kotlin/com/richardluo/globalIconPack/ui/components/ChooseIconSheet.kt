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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.iconPack.IconPackApps
import com.richardluo.globalIconPack.ui.model.OriginalIcon
import com.richardluo.globalIconPack.ui.model.VariantIcon
import com.richardluo.globalIconPack.ui.model.VariantPackIcon
import com.richardluo.globalIconPack.ui.viewModel.ChooseIconVM
import com.richardluo.globalIconPack.utils.getValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseIconSheet(viewModel: ChooseIconVM, replaceIcon: (VariantIcon) -> Unit) {
  val context = LocalContext.current

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
        title = { Text(stringResource(R.string.iconPack)) },
        value = IconPackApps.getFlow(context).collectAsState(mapOf()).value.toList(),
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
        modifier = Modifier.padding(bottom = 8.dp),
        trailingIcon = {
          IconButtonWithTooltip(Icons.Outlined.FilterList, "By pack") {
            packDialogState.value = true
          }
        },
      ) {
        Icon(Icons.Outlined.Search, contentDescription = "Search")
      }

      fun LazyGridScope.variantIconItemsBound(icons: List<VariantIcon>) =
        variantIconItems(icons, { viewModel.loadIconForSelectedApp(it) }) {
          replaceIcon(it)
          viewModel.variantSheet = false
        }

      val suggestIcons = viewModel.suggestVariantIcons.getValue(null)
      if (suggestIcons != null)
        if (viewModel.variantSearchText.value.isEmpty()) {
          val variantIcons = viewModel.variantIcons.getValue(null) ?: setOf()
          LazyVerticalGrid(
            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
            columns = GridCells.Adaptive(minSize = 74.dp),
          ) {
            variantIconTitle(context.getString(R.string.suggestedIcons))
            variantIconItemsBound(suggestIcons)
            variantIconTitle(context.getString(R.string.allIcons))
            variantIconItemsBound(variantIcons.toList())
          }
        } else
          LazyVerticalGrid(
            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
            columns = GridCells.Adaptive(minSize = 74.dp),
          ) {
            variantIconItemsBound(suggestIcons)
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

private fun LazyGridScope.variantIconItems(
  icons: List<VariantIcon>,
  loadImage: suspend (VariantIcon) -> ImageBitmap,
  onClick: (VariantIcon) -> Unit,
) {
  items(icons) { icon ->
    IconForApp(
      when (icon) {
        is OriginalIcon -> stringResource(R.string.originalIcon)
        is VariantPackIcon -> icon.entry.name
        else -> ""
      },
      loadImage = { loadImage(icon) },
    ) {
      onClick(icon)
    }
  }
}
