package com.richardluo.globalIconPack.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingDialog(progress: Float? = null, text: String? = null) {
  CustomDialog(
    onDismissRequest = {},
    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
  ) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) {
      LoadingLine(progress = progress)
      if (text != null) {
        Spacer(modifier = Modifier.height(6.dp))
        OneLineText(text)
      }
    }
  }
}

@SuppressLint("ModifierParameter")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingCircle(modifier: Modifier = Modifier.fillMaxSize()) {
  Box(modifier = modifier, contentAlignment = Alignment.Center) { LoadingIndicator() }
}

@SuppressLint("ModifierParameter")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingLine(modifier: Modifier = Modifier.fillMaxWidth(), progress: Float? = null) {
  if (progress != null) LinearWavyProgressIndicator(modifier = modifier, progress = { progress })
  else LinearWavyProgressIndicator(modifier = modifier)
}
