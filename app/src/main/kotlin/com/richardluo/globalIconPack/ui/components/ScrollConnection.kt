package com.richardluo.globalIconPack.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager

open class ExpandedScrollConnection(protected val threshold: Float = 30f) : NestedScrollConnection {
  var expanded by mutableStateOf(true)

  override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
    if (available.y > 1) expanded = true else if (available.y < -threshold) expanded = false
    return Offset.Zero
  }
}

class ClearFocusScrollConnection(private val focusManager: FocusManager) : NestedScrollConnection {

  override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
    if (available.y != 0f || available.x != 0f) focusManager.clearFocus()
    return Offset.Zero
  }
}

@Composable
fun Modifier.clearFocusOnScroll(): Modifier {
  val focusManager = LocalFocusManager.current
  return nestedScroll(remember(focusManager) { ClearFocusScrollConnection(focusManager) })
}
