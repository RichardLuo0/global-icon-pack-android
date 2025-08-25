package com.richardluo.globalIconPack.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector

class FabDesc(val icon: ImageVector, val text: String, val onClick: () -> Unit)

@Composable
fun AnimatedFab(desc: FabDesc, expanded: Boolean = true) {
  ExtendedFloatingActionButton(
    { AnimatedContent(desc.text, contentAlignment = Alignment.Center) { Text(text = it) } },
    { AnimatedContent(desc.icon, contentAlignment = Alignment.Center) { Icon(it, null) } },
    onClick = desc.onClick,
    expanded = expanded,
  )
}
