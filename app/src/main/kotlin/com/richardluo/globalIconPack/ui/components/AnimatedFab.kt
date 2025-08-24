package com.richardluo.globalIconPack.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

class FabDesc(val icon: ImageVector, val text: String, val onClick: () -> Unit)

open class ExpandedScrollConnection : NestedScrollConnection {
  var expanded by mutableStateOf(true)

  override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
    if (available.y > 1) expanded = true else if (available.y < -1) expanded = false
    return Offset.Zero
  }
}

@Composable
fun AnimatedFab(desc: FabDesc, expanded: Boolean = true) {
  ExtendedFloatingActionButton(
    { AnimatedContent(targetState = desc.text, label = "Fab text change") { Text(text = it) } },
    { AnimatedContent(targetState = desc.icon, label = "Fab icon change") { Icon(it, null) } },
    onClick = desc.onClick,
    expanded = expanded,
  )
}
