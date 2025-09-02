package com.richardluo.globalIconPack.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.utils.chain

enum class ListItemPos {
  Top,
  Middle,
  Bottom,
  Single;

  companion object {
    fun from(index: Int, size: Int) =
      if (size == 1) Single
      else
        when (index) {
          0 -> Top
          size - 1 -> Bottom
          else -> Middle
        }
  }
}

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

@Composable
fun ListItemPos.toShape() =
  when (this) {
    ListItemPos.Top -> listTopItemShape
    ListItemPos.Middle -> listMiddleItemShape
    ListItemPos.Bottom -> listBottomItemShape
    ListItemPos.Single -> listSingleItemShape
  }

val listItemPadding = PaddingValues(horizontal = 16.dp, vertical = 1.5.dp)

@Composable
fun ListItem(
  leading: @Composable (() -> Unit)? = null,
  headline: @Composable () -> Unit,
  supporting: @Composable () -> Unit,
  selected: Boolean = false,
  shape: CornerBasedShape? = listSingleItemShape,
  padding: PaddingValues = listItemPadding,
  onClick: (() -> Unit)? = null,
) {
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .padding(padding)
        .chain {
          shape?.let {
            clip(it)
              .background(
                if (selected) MaterialTheme.colorScheme.primaryFixedDim
                else MaterialTheme.colorScheme.surfaceContainerLow
              )
          }
        }
        .chain { onClick?.let { selectable(selected, true, Role.RadioButton, onClick = onClick) } }
        .padding(horizontal = 12.dp, vertical = 10.dp)
  ) {
    ListItemContent(leading, headline, supporting, selected)
  }
}

@Composable
fun ListItemContent(
  leading: @Composable (() -> Unit)? = null,
  headline: @Composable () -> Unit,
  supporting: @Composable () -> Unit,
  selected: Boolean = false,
) {
  val textPart =
    @Composable {
      Column {
        ProvideContentColorTextStyle(
          contentColor =
            if (selected) MaterialTheme.colorScheme.onPrimaryFixed
            else MaterialTheme.colorScheme.onSurface,
          textStyle = MaterialTheme.typography.bodyLarge,
          headline,
        )
        ProvideContentColorTextStyle(
          contentColor =
            if (selected) MaterialTheme.colorScheme.onPrimaryFixedVariant
            else MaterialTheme.colorScheme.onSurfaceVariant,
          textStyle = MaterialTheme.typography.bodySmall,
          supporting,
        )
      }
    }

  if (leading != null)
    Layout(
      content = {
        CompositionLocalProvider(
          LocalContentColor provides
            if (selected) MaterialTheme.colorScheme.onPrimaryFixed
            else MaterialTheme.colorScheme.secondary
        ) {
          Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
            leading()
          }
        }
        Spacer(modifier = Modifier.width(12.dp))
        textPart()
      }
    ) { measurables, constraints ->
      val leading = measurables[0]
      val others = measurables.drop(1)

      val estimatedHeight = others.maxOf { it.maxIntrinsicHeight(Int.MAX_VALUE) }
      val leadingWidth = leading.minIntrinsicWidth(estimatedHeight)

      var newOtherWidth = constraints.maxWidth - leadingWidth
      val otherPlaceables =
        others.map {
          it.measure(constraints.copy(maxWidth = newOtherWidth)).also { newOtherWidth -= it.width }
        }
      val height = otherPlaceables.maxOf { it.height }
      val leadingPlaceable =
        leading.measure(constraints.copy(maxWidth = leadingWidth, maxHeight = height))

      layout(constraints.maxWidth, height) {
        var x = 0
        leadingPlaceable.placeRelative(x, 0)
        x += leadingPlaceable.width
        otherPlaceables.forEach {
          it.placeRelative(x, 0)
          x += it.width
        }
      }
    }
  else textPart()
}

@Preview(showBackground = true)
@Composable
fun ListItemPreview() {
  val context = LocalContext.current
  val image =
    remember(context) { context.getDrawable(R.drawable.broken_image)!!.toBitmap().asImageBitmap() }
  val leading =
    @Composable {
      Box(modifier = Modifier.fillMaxHeight().aspectRatio(1f)) {
        Image(
          bitmap = image,
          contentDescription = "Test",
          modifier = Modifier.matchParentSize(),
          contentScale = ContentScale.Crop,
        )
      }
    }

  Column {
    ListItem(
      leading,
      { OneLineText("Test") },
      { OneLineText("com.test") },
      false,
      listTopItemShape,
    ) {}
    ListItem(
      leading,
      { OneLineText("Test") },
      { OneLineText("com.test") },
      true,
      listBottomItemShape,
    ) {}
  }
}
