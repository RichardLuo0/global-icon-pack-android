package com.richardluo.globalIconPack.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun OneLineText(text: String) {
  Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun TwoLineText(text: String) {
  Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
}
