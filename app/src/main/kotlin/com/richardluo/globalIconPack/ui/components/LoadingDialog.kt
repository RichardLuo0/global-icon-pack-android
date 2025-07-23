package com.richardluo.globalIconPack.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadingDialog(progress: Float? = null, text: String? = null) {
  CustomDialog(
    onDismissRequest = {},
    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
  ) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) {
      if (progress != null)
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = { progress })
      else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

      if (text != null) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(text)
      }
    }
  }
}
