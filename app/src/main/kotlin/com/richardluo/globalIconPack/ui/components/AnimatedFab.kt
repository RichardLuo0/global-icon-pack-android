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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.unit.dp

class FabDesc(val icon: ImageVector, val text: String, val onClick: () -> Unit)

open class ExpandFabScrollConnection : NestedScrollConnection {
  var isExpand by mutableStateOf(true)
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
