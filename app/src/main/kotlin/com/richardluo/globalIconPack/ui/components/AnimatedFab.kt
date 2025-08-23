package com.richardluo.globalIconPack.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.dp

class FabDesc(val icon: ImageVector, val text: String, val onClick: () -> Unit)

open class ExpandFabScrollConnection : NestedScrollConnection {
  var isExpand by mutableStateOf(true)

  override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
    if (available.y > 1) isExpand = true else if (available.y < -1) isExpand = false
    return Offset.Zero
  }
}

@Composable
fun AnimatedFab(desc: FabDesc, isExpand: Boolean = true) {
  FloatingActionButton(onClick = desc.onClick) {
    Row(
      modifier = Modifier.padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AnimatedContent(targetState = desc, label = "Fab icon change") { Icon(it.icon, it.text) }
      AnimatedVisibility(isExpand) {
        AnimatedContent(targetState = desc, label = "Fab text change") {
          Text(text = it.text, modifier = Modifier.padding(start = 8.dp))
        }
      }
    }
  }
}
