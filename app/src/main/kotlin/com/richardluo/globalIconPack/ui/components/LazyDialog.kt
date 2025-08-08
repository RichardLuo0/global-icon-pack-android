package com.richardluo.globalIconPack.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.richardluo.globalIconPack.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> LazyDialog(
  openState: MutableState<Boolean>,
  title: (@Composable () -> Unit)? = null,
  value: T?,
  dismissible: Boolean = true,
  content: @Composable (T) -> Unit,
) {
  CustomDialog(openState, title = title, dismissible = dismissible) {
    if (value != null) content(value)
    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(24.dp))
  }
}

@Composable
fun <T> LazyListDialog(
  openState: MutableState<Boolean>,
  title: @Composable () -> Unit,
  value: List<T>?,
  key: ((item: T) -> Any)? = null,
  dismissible: Boolean = true,
  focusItem: (T) -> Boolean = { true },
  itemContent: @Composable (item: T, dismiss: () -> Unit) -> Unit,
) =
  LazyDialog(openState, title, value, dismissible) { list ->
    if (list.isEmpty()) Nothing()
    else
      ScrollIndicationBox(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        state =
          rememberSaveable(saver = LazyListState.Saver) {
            LazyListState(list.indexOfFirst(focusItem).takeIf { it > 0 } ?: 0, 0)
          },
      ) {
        LazyColumn(state = it) {
          items(list, key) { item -> itemContent(item) { openState.value = false } }
        }
      }
  }

@Composable
fun <T> LazyGridDialog(
  openState: MutableState<Boolean>,
  title: @Composable () -> Unit,
  value: List<T>,
  dismissible: Boolean = true,
  itemContent: @Composable (item: T, dismiss: () -> Unit) -> Unit,
) =
  LazyDialog(openState, title, value, dismissible) { list ->
    if (list.isEmpty()) Nothing()
    else
      LazyVerticalGrid(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp).padding(vertical = 4.dp),
        columns = GridCells.Adaptive(minSize = 80.dp),
      ) {
        items(list) { item -> itemContent(item) { openState.value = false } }
      }
  }

@Composable
private fun Nothing() {
  Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        Icons.Outlined.HourglassEmpty,
        "nothing",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.width(8.dp))
      OneLineText(
        stringResource(R.string.common_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
