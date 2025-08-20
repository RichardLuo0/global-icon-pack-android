package com.richardluo.globalIconPack.ui.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val listTopItemShape
  @Composable
  get() =
    shapes.largeIncreased.copy(
      bottomStart = shapes.extraSmall.bottomStart,
      bottomEnd = shapes.extraSmall.bottomEnd,
    )

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val listMiddleItemShape
  @Composable get() = shapes.extraSmall

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val listBottomItemShape
  @Composable
  get() =
    shapes.largeIncreased.copy(
      topStart = shapes.extraSmall.topStart,
      topEnd = shapes.extraSmall.topEnd,
    )

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val listSingleItemShape
  @Composable get() = shapes.largeIncreased
