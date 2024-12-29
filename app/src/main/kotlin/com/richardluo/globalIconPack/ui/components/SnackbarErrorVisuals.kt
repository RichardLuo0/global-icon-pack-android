package com.richardluo.globalIconPack.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals

class SnackbarErrorVisuals(
  override val message: String,
  override val actionLabel: String? = null,
  override val withDismissAction: Boolean = false,
  override val duration: SnackbarDuration =
    if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Indefinite,
) : SnackbarVisuals
