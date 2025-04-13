package com.richardluo.globalIconPack.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> LazyDialog(
  openState: MutableState<Boolean>,
  title: @Composable () -> Unit,
  value: T?,
  dismissible: Boolean = true,
  content: @Composable (T) -> Unit,
) {
  if (!openState.value) return
  BasicAlertDialog(
    onDismissRequest =
      if (dismissible)
        fun() {
          openState.value = false
        }
      else fun() {}
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = AlertDialogDefaults.shape,
      color = AlertDialogDefaults.containerColor,
      tonalElevation = AlertDialogDefaults.TonalElevation,
    ) {
      Column(modifier = Modifier.padding(vertical = 24.dp)) {
        ProvideContentColorTextStyle(
          contentColor = AlertDialogDefaults.titleContentColor,
          textStyle = MaterialTheme.typography.headlineSmall,
        ) {
          Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) { title() }
        }
        ProvideContentColorTextStyle(
          contentColor = MaterialTheme.colorScheme.primary,
          textStyle = MaterialTheme.typography.labelLarge,
        ) {
          Box(modifier = Modifier.fillMaxWidth()) {
            if (value != null) content(value)
            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(24.dp))
          }
        }
      }
    }
  }
}

@Composable
fun <T> LazyListDialog(
  openState: MutableState<Boolean>,
  title: @Composable () -> Unit,
  value: List<T>?,
  key: ((item: T) -> Any)? = null,
  nothing: @Composable () -> Unit = {},
  dismissible: Boolean = true,
  itemContent: @Composable (item: T, dismiss: () -> Unit) -> Unit,
) =
  LazyDialog(openState, title, value, dismissible) { list ->
    if (list.isEmpty()) nothing()
    else
      LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        items(list, key) { item -> itemContent(item) { openState.value = false } }
      }
  }

@Composable
fun <T> LazyGridDialog(
  openState: MutableState<Boolean>,
  title: @Composable () -> Unit,
  value: List<T>,
  nothing: @Composable () -> Unit = {},
  dismissible: Boolean = true,
  itemContent: @Composable (item: T, dismiss: () -> Unit) -> Unit,
) =
  LazyDialog(openState, title, value, dismissible) { list ->
    if (list.isEmpty()) nothing()
    else
      LazyVerticalGrid(
        modifier = Modifier.padding(horizontal = 2.dp).padding(top = 16.dp),
        columns = GridCells.Adaptive(minSize = 80.dp),
      ) {
        items(list) { item -> itemContent(item) { openState.value = false } }
      }
  }
