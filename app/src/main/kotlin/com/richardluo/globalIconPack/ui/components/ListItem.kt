package com.richardluo.globalIconPack.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.richardluo.globalIconPack.R

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
fun Modifier.animatedShape(
  shape: CornerBasedShape,
  interactionSource: InteractionSource,
): Modifier {
  val isPressed by interactionSource.collectIsPressedAsState()
  val target = if (isPressed) listMiddleItemShape else shape
  val animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Dp>()
  val density = LocalDensity.current

  val (targetTopStart, targetTopEnd, targetBottomEnd, targetBottomStart) =
    remember(target, density) {
      with(density) {
        listOf(
          target.topStart.toPx(Size.Unspecified, density).toDp(),
          target.topEnd.toPx(Size.Unspecified, density).toDp(),
          target.bottomEnd.toPx(Size.Unspecified, density).toDp(),
          target.bottomStart.toPx(Size.Unspecified, density).toDp(),
        )
      }
    }

  val topStart by
    animateDpAsState(
      targetTopStart,
      animationSpec,
      label = "topStart",
    )
  val topEnd by
    animateDpAsState(
      targetTopEnd,
      animationSpec,
      label = "topEnd",
    )
  val bottomEnd by
    animateDpAsState(
      targetBottomEnd,
      animationSpec,
      label = "bottomEnd",
    )
  val bottomStart by
    animateDpAsState(
      targetBottomStart,
      animationSpec,
      label = "bottomStart",
    )

  return clip(
    remember(topStart, topEnd, bottomEnd, bottomStart) {
      RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
    }
  )
}

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
  val interactionSource = remember { MutableInteractionSource() }

  Box(
    modifier =
      Modifier.fillMaxWidth()
        .padding(padding)
        .then(
          shape?.let {
            Modifier.animatedShape(shape, interactionSource)
              .background(
                if (selected) MaterialTheme.colorScheme.primaryFixedDim
                else MaterialTheme.colorScheme.surfaceContainerLow
              )
          } ?: Modifier
        )
        .then(
          onClick?.let {
            Modifier.selectable(
              selected,
              true,
              Role.RadioButton,
              interactionSource,
              onClick = onClick,
            )
          } ?: Modifier.clickable(interactionSource, ripple()) {}
        )
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
      val otherPlaceables = others.map {
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
  val image = painterResource(R.drawable.broken_image)
  val leading =
    @Composable {
      Box(modifier = Modifier.fillMaxHeight().aspectRatio(1f)) {
        Image(
          painter = image,
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
