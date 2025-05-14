package com.richardluo.globalIconPack.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.ui.model.IconInfo
import com.richardluo.globalIconPack.ui.model.OriginalIcon
import com.richardluo.globalIconPack.ui.model.VariantIcon
import com.richardluo.globalIconPack.ui.model.VariantPackIcon
import com.richardluo.globalIconPack.ui.viewModel.IconChooserVM
import com.richardluo.globalIconPack.ui.viewModel.IconPackApps
import com.richardluo.globalIconPack.ui.viewModel.emptyImageBitmap
import com.richardluo.globalIconPack.utils.getValue
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.Preference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconChooserSheet(
  vm: IconChooserVM = viewModel(),
  loadOriginalIcon: suspend (IconInfo) -> ImageBitmap,
) {
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
  val scope = rememberCoroutineScope()

  fun onDismissRequest() {
    vm.variantSheet = false
    vm.searchText.value = ""
  }

  if (vm.variantSheet)
    ModalBottomSheet(sheetState = sheetState, onDismissRequest = ::onDismissRequest) {
      val packDialogState = rememberSaveable { mutableStateOf(false) }
      val optionDialogState = remember { mutableStateOf(false) }
      val selectedIcon = remember { mutableStateOf<VariantIcon?>(null) }

      fun replaceAsNormalIcon(icon: VariantIcon) {
        vm.replaceIcon(vm.iconInfo ?: return, icon)
        scope.launch {
          sheetState.hide()
          onDismissRequest()
        }
      }

      fun replaceAsCalendarIcon(icon: VariantIcon) {
        try {
          vm.replaceIcon(vm.iconInfo ?: return, vm.asCalendarEntry(icon))
          scope.launch {
            sheetState.hide()
            onDismissRequest()
          }
        } catch (_: IconChooserVM.NotCalendarEntryException) {
          Toast.makeText(context, context.getString(R.string.notCalendarIcon), Toast.LENGTH_LONG)
            .show()
        }
      }

      RoundSearchBar(
        vm.searchText,
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

      fun LazyGridScope.variantIconTitle(text: String, expandState: MutableState<Boolean>? = null) {
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "Title") {
          Row(horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
              text,
              style = MaterialTheme.typography.titleMedium,
              modifier = Modifier.padding(top = 12.dp, start = 12.dp, end = 12.dp),
            )
            if (expandState != null) {
              var isExpanded by expandState
              IconButton(onClick = { isExpanded = !isExpanded }) {
                Icon(
                  imageVector =
                    if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                  contentDescription = "Expand",
                )
              }
            }
          }
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
            loadImage = {
              when (icon) {
                is OriginalIcon -> vm.iconInfo?.let { loadOriginalIcon(it) }
                is VariantPackIcon -> vm.loadIcon(icon)
                else -> null
              } ?: emptyImageBitmap
            },
            onLongClick = {
              selectedIcon.value = icon
              optionDialogState.value = true
            },
            onClick = { replaceAsNormalIcon(icon) },
          )
        }
      }

      val suggestIcons = vm.suggestIcons.getValue(null)
      if (suggestIcons != null)
        if (vm.searchText.value.isEmpty()) {
          val variantIcons = vm.icons.getValue(null) ?: setOf()
          var expandState = rememberSaveable { mutableStateOf(false) }
          val gridState = rememberLazyGridState()
          LazyVerticalGrid(
            state = gridState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
            columns = GridCells.Adaptive(minSize = 74.dp),
          ) {
            val shrinkSize = gridState.layoutInfo.maxSpan * 2
            if (suggestIcons.size > shrinkSize) {
              variantIconTitle(context.getString(R.string.suggestedIcons), expandState)
              variantIconItems(
                if (expandState.value) suggestIcons else suggestIcons.take(shrinkSize)
              )
            } else {
              variantIconTitle(context.getString(R.string.suggestedIcons))
              variantIconItems(suggestIcons)
            }

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

      LazyListDialog(
        packDialogState,
        title = { Text(stringResource(R.string.iconPack)) },
        value = IconPackApps.flow.collectAsState(null).value?.toList(),
        key = { it.first },
        focusItem = { it.first == vm.iconPack?.pack },
      ) { item, dismiss ->
        IconPackItem(item.first, item.second, vm.iconPack?.pack ?: "") {
          vm.setPack(item.first)
          dismiss()
        }
      }

      class Option(val icon: ImageVector?, val name: String, val onClick: (VariantIcon) -> Unit)

      ProvideMyPreferenceTheme {
        LazyListDialog(
          optionDialogState,
          title = { Text(stringResource(R.string.options)) },
          value =
            listOf(
              Option(null, stringResource(R.string.asNormalIcon), ::replaceAsNormalIcon),
              Option(
                Icons.Outlined.CalendarMonth,
                stringResource(R.string.asCalendarIcon),
                ::replaceAsCalendarIcon,
              ),
            ),
        ) { option, dismiss ->
          Preference(
            icon = { option.icon?.let { Icon(it, option.name) } },
            title = { Text(option.name) },
          ) {
            option.onClick(selectedIcon.value ?: return@Preference)
            dismiss()
          }
        }
      }
    }
}
