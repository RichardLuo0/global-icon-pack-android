package com.richardluo.globalIconPack.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.ui.viewModel.AutoFillVM
import com.richardluo.globalIconPack.ui.viewModel.IconPackApps
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun AutoFillDialog(vm: AutoFillVM = viewModel(), onOk: (List<String>) -> Unit) {
  val lazyListState = rememberLazyListState()
  val reorderableLazyListState =
    rememberReorderableLazyListState(lazyListState) { from, to ->
      vm.movePack(from.index - 1, to.index - 1)
    }
  val scope = rememberCoroutineScope()

  LaunchedEffect(vm.dialog.value) { if (!vm.dialog.value) vm.packs.clear() }

  CustomDialog(vm.dialog, title = { OneLineText(stringResource(R.string.autoFill)) }) {
    val apps = IconPackApps.flow.collectAsState(null).value ?: return@CustomDialog
    val baseApp = apps[vm.basePack] ?: return@CustomDialog
    val addPackDialog = rememberSaveable { mutableStateOf(false) }

    TwoLineText(
      modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 4.dp),
      text = stringResource(R.string.autoFillSummary),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    ScrollIndicationBox(
      modifier = Modifier
          .fillMaxWidth()
          .weight(1f, false)
          .padding(vertical = 8.dp),
      state = lazyListState,
    ) {
      LazyColumn(state = it) {
        item {
          Row(modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 24.dp, vertical = 6.dp)) {
            IconPackItemContent(vm.basePack, baseApp)
          }
        }
        itemsIndexed(vm.packs, key = { _, it -> it }) { i, it ->
          var width by remember { mutableIntStateOf(1) }
          val offsetX = remember { Animatable(0f) }
          ReorderableItem(
            reorderableLazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            key = it,
          ) { isDragging ->
            val elevation by
              animateDpAsState(
                if (isDragging || offsetX.value in -width / 2f..-width / 3f) 4.dp else 0.dp
              )
            Surface(
              modifier =
                  Modifier
                      .onSizeChanged { it -> width = it.width }
                      .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                      .draggable(
                          rememberDraggableState { it ->
                              scope.launch {
                                  if (!offsetX.isRunning)
                                      offsetX.snapTo((offsetX.value + it).coerceAtMost(0f))
                              }
                          },
                          Orientation.Horizontal,
                          onDragStopped = {
                              if (offsetX.value <= -width / 3f) {
                                  offsetX.animateTo(-width.toFloat())
                                  vm.removePack(i)
                              } else offsetX.animateTo(0f)
                          },
                      ),
              color = AlertDialogDefaults.containerColor,
              shape = MaterialTheme.shapes.medium,
              shadowElevation = elevation,
            ) {
              Row(
                modifier =
                  Modifier
                      .height(IntrinsicSize.Min)
                      .padding(horizontal = 8.dp, vertical = 8.dp)
              ) {
                Box(modifier = Modifier.weight(1f)) {
                  IconPackItemContent(it, apps[it] ?: return@Row)
                }
                Icon(
                  modifier = Modifier
                      .fillMaxHeight()
                      .draggableHandle(),
                  imageVector = Icons.Outlined.DragIndicator,
                  contentDescription = "reorder",
                )
              }
            }
          }
        }
      }
    }

    DialogButtonRow(
      arrayOf(
        DialogButton(stringResource(R.string.autoFillAdd)) { addPackDialog.value = true },
        DialogButton(stringResource(android.R.string.cancel), ButtonType.Outlined) {
          vm.dialog.value = false
        },
        DialogButton(stringResource(android.R.string.ok), ButtonType.Filled) {
          onOk(vm.packs)
          vm.dialog.value = false
        },
      )
    )

    LazyListDialog(
      addPackDialog,
      { OneLineText(stringResource(R.string.autoFillAdd)) },
      remember { derivedStateOf { (apps - vm.packs - vm.basePack).toList() } }.value,
    ) { (pack, app), dismiss ->
      IconPackItem(pack, app, false) {
        vm.addPack(pack)
        dismiss()
      }
    }
  }
}
