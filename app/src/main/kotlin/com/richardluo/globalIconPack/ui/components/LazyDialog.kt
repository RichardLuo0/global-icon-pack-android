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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> LazyDialog(
  openState: MutableState<Boolean> = remember { mutableStateOf(true) },
  title: @Composable () -> Unit,
  initValue: T,
  load: suspend () -> T,
  content: @Composable (T) -> Unit,
) {
  var open by openState
  if (!open) return

  var value by remember { mutableStateOf(initValue) }
  var isLoaded by remember { mutableStateOf(false) }
  LaunchedEffect(openState) {
    if (open) {
      value = load()
      isLoaded = true
    }
  }
  BasicAlertDialog(onDismissRequest = { open = false }) {
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = AlertDialogDefaults.shape,
      color = AlertDialogDefaults.containerColor,
      tonalElevation = AlertDialogDefaults.TonalElevation,
    ) {
      Column {
        ProvideContentColorTextStyle(
          contentColor = AlertDialogDefaults.titleContentColor,
          textStyle = MaterialTheme.typography.headlineSmall,
        ) {
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp)
          ) {
            title()
          }
        }
        ProvideContentColorTextStyle(
          contentColor = MaterialTheme.colorScheme.primary,
          textStyle = MaterialTheme.typography.labelLarge,
        ) {
          Box(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            if (isLoaded) content(value)
            else
              LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
              )
          }
        }
      }
    }
  }
}

@Composable
fun <T> LazyListDialog(
  openState: MutableState<Boolean> = remember { mutableStateOf(true) },
  title: @Composable () -> Unit,
  initValue: List<T> = listOf(),
  load: suspend () -> List<T>,
  nothing: @Composable () -> Unit = {},
  item: @Composable (key: T, onClick: () -> Unit) -> Unit,
) =
  LazyDialog(openState, title, initValue, load) { list ->
    if (list.isEmpty()) nothing()
    else
      LazyColumn(modifier = Modifier.fillMaxWidth()) {
        var open by openState
        items(list) { key -> item(key) { open = false } }
      }
  }

@Composable
fun <T> LazyGridDialog(
  openState: MutableState<Boolean> = remember { mutableStateOf(true) },
  title: @Composable () -> Unit,
  initValue: List<T> = listOf(),
  load: () -> List<T>,
  nothing: @Composable () -> Unit = {},
  item: @Composable (key: T, onClick: () -> Unit) -> Unit,
) =
  LazyDialog(openState, title, initValue, load) { list ->
    if (list.isEmpty()) nothing()
    else
      LazyVerticalGrid(
        modifier = Modifier.padding(horizontal = 2.dp),
        columns = GridCells.Adaptive(minSize = 80.dp),
      ) {
        var open by openState
        items(list) { key -> item(key) { open = false } }
      }
  }
