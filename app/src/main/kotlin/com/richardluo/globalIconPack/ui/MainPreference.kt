package com.richardluo.globalIconPack.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Shortcut
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Backpack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.CropOriginal
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FlipToFront
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.PhotoSizeSelectSmall
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.ShapeLine
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import androidx.core.graphics.toColorInt
import com.richardluo.globalIconPack.MODE_LOCAL
import com.richardluo.globalIconPack.MODE_PROVIDER
import com.richardluo.globalIconPack.MODE_SHARE
import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.ui.components.IconButtonWithTooltip
import com.richardluo.globalIconPack.ui.components.IconPackItem
import com.richardluo.globalIconPack.ui.components.OneLineText
import com.richardluo.globalIconPack.ui.components.TextFieldDialog
import com.richardluo.globalIconPack.ui.components.TextFieldDialogContent
import com.richardluo.globalIconPack.ui.components.TwoLineText
import com.richardluo.globalIconPack.ui.components.dialogPreference
import com.richardluo.globalIconPack.ui.components.mapListPreference
import com.richardluo.globalIconPack.ui.components.myPreference
import com.richardluo.globalIconPack.ui.components.mySliderPreference
import com.richardluo.globalIconPack.ui.components.mySwitchPreference
import com.richardluo.globalIconPack.ui.repo.IconPackApps
import com.richardluo.globalIconPack.utils.DrawablePainter
import com.richardluo.globalIconPack.utils.PathDrawable
import com.richardluo.globalIconPack.utils.runCatchingToastOnMain
import me.zhanghai.compose.preference.LocalPreferenceTheme
import me.zhanghai.compose.preference.listPreference
import me.zhanghai.compose.preference.preference
import me.zhanghai.compose.preference.switchPreference

object MainPreference {
  @Composable
  fun General(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val typography = MaterialTheme.typography
    LazyColumn(modifier = modifier) {
      listPreference(
        icon = { AnimatedContent(it) { ModeToIcon(it) } },
        key = Pref.MODE.key,
        defaultValue = Pref.MODE.def,
        values = listOf(MODE_SHARE, MODE_PROVIDER, MODE_LOCAL),
        valueToText = { modeToAnnotatedString(context, it, typography) },
        title = { TwoLineText(modeToTitle(context, it)) },
        summary = { TwoLineText(modeToSummary(context, it)) },
      )
      mapListPreference(
        icon = { Icon(Icons.Outlined.Backpack, Pref.ICON_PACK.key) },
        key = Pref.ICON_PACK.key,
        defaultValue = Pref.ICON_PACK.def,
        getValueMap = { IconPackApps.flow.collectAsState(null).value },
        item = { key, value, currentKey, onClick ->
          IconPackItem(key, value, key == currentKey, onClick)
        },
        title = { TwoLineText(stringResource(R.string.iconPack)) },
        summary = { key, value ->
          Text(
            value?.label
              ?: key.takeIf { it.isNotEmpty() }
              ?: stringResource(R.string.iconPackSummary)
          )
        },
      )
      switchPreference(
        icon = {},
        key = Pref.ICON_PACK_AS_FALLBACK.key,
        defaultValue = Pref.ICON_PACK_AS_FALLBACK.def,
        title = { TwoLineText(stringResource(R.string.iconPackAsFallback)) },
        summary = { TwoLineText(stringResource(R.string.iconPackAsFallbackSummary)) },
      )
      switchPreference(
        icon = { Icon(Icons.AutoMirrored.Outlined.Shortcut, Pref.SHORTCUT.key) },
        key = Pref.SHORTCUT.key,
        defaultValue = Pref.SHORTCUT.def,
        title = { TwoLineText(stringResource(R.string.shortcut)) },
      )
      preference(
        icon = { Icon(Icons.Outlined.Merge, "openMerger") },
        key = "openMerger",
        onClick = { context.startActivity(Intent(context, IconPackMergerActivity::class.java)) },
        title = { TwoLineText(stringResource(R.string.mergeIconPack)) },
        summary = { TwoLineText(stringResource(R.string.mergeIconPackSummary)) },
      )
    }
  }

  @Composable
  fun IconPack(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LazyColumn(modifier = modifier) {
      myPreference(
        icon = { Icon(Icons.Outlined.Edit, "iconVariant") },
        key = "iconVariant",
        enabled = { it.get(Pref.MODE) != MODE_LOCAL && it.get(Pref.ICON_PACK).isNotEmpty() },
        onClick = { context.startActivity(Intent(context, IconVariantActivity::class.java)) },
        title = { TwoLineText(stringResource(R.string.iconVariant)) },
        summary = { TwoLineText(stringResource(R.string.iconVariantSummary)) },
      )
      item { HorizontalDivider() }
      fallback(context)
    }
  }

  private class Mask(val name: String, val pathData: String)

  private val masks =
    listOf(
      Mask("Original", ""),
      Mask("Circle", "M50 0a50 50 0 1 1 0 100A50 50 0 1 1 50 0"),
      Mask("Rectangle", "M0 0h100V100H0V0"),
      Mask("Round rect", "M20 0S0 0 0 20V80s0 20 20 20H80s20 0 20-20V20S100 0 80 0H20"),
      Mask(
        "Superellipse",
        "M100 50l-.005-1.569-.016-1.227-.025-1.123-.036-1.061-.046-1.016-.057-.981-.067-.953-.077-.928-.087-.908-.098-.89-.107-.872-.118-.857-.128-.843-.139-.83-.148-.817-.159-.805-.168-.794-.179-.783-.189-.772-.199-.762-.209-.752-.219-.743-.229-.733-.239-.723-.248-.714-.259-.705-.269-.696-.278-.688-.288-.678-.299-.67-.307-.66-.318-.653-.327-.643-.337-.635-.346-.626-.356-.617-.366-.609-.375-.6-.384-.592-.394-.583-.403-.574-.413-.566-.421-.556-.432-.548-.44-.54-.449-.53-.459-.521-.468-.513-.477-.504-.485-.495-.495-.485-.504-.477-.513-.468-.521-.459-.53-.449-.54-.44-.548-.432-.556-.421-.566-.413-.574-.403-.583-.394-.592-.384-.6-.375-.609-.366-.617-.356-.626-.346-.635-.337-.643-.327-.653-.318-.66-.307-.67-.299-.678-.288-.688-.278-.696-.269-.705-.259-.714-.248-.723-.239-.733-.229-.743-.219-.752-.209-.762-.199-.772-.189-.783-.179-.794-.168-.805-.159-.817-.148-.83-.139-.843-.128-.857-.118-.872-.107-.89-.098-.908-.087L57.93.252 56.977.185 55.996.128 54.98.082 53.919.046 52.796.021 51.569.005 50 0 48.431.005 47.204.021 46.081.046 45.02.082 44.004.128l-.981.057-.953.067-.928.077-.908.087-.89.098-.872.107-.857.118-.843.128-.83.139-.817.148-.805.159-.794.168-.783.179-.772.189-.762.199-.752.209-.743.219-.733.229-.723.239-.714.248-.705.259-.696.269-.688.278-.678.288-.67.299-.66.307-.653.318-.643.327-.635.337-.626.346-.617.356-.609.366-.6.375-.592.384-.583.394-.574.403-.566.413-.556.421-.548.432-.54.44-.53.449-.521.459-.513.468-.504.477-.495.485-.485.495-.477.504-.468.513-.459.521-.449.53-.44.54-.432.548-.421.556-.413.566-.403.574-.394.583-.384.592-.375.6-.366.609-.356.617-.346.626-.337.635-.327.643-.318.653-.307.66-.299.67-.288.678-.278.688-.269.696-.259.705-.248.714-.239.723-.229.733-.219.743-.209.752-.199.762-.189.772-.179.783-.168.794-.159.805-.148.817-.139.83-.128.843-.118.857-.107.872-.098.89-.087.908-.077.928-.067.953-.057.981L.082 45.02.046 46.081.021 47.204.005 48.431 0 50 .005 51.569.021 52.796.046 53.919.082 54.98.128 55.996l.057.981.067.953.077.928.087.908.098.89.107.872.118.857.128.843.139.83.148.817.159.805.168.794.179.783.189.772.199.762.209.752.219.743.229.733.239.723.248.714.259.705.269.696.278.688.288.678.299.67.307.66.318.653.327.643.337.635.346.626.356.617.366.609.375.6.384.592.394.583.403.574.413.566.421.556.432.548.44.54.449.53.459.521.468.513.477.504.485.495.495.485.504.477.513.468.521.459.53.449.54.44.548.432.556.421.566.413.574.403.583.394.592.384.6.375.609.366.617.356.626.346.635.337.643.327.653.318.66.307.67.299.678.288.688.278.696.269.705.259.714.248.723.239.733.229.743.219.752.209.762.199.772.189.783.179.794.168.805.159.817.148.83.139.843.128.857.118.872.107.89.098.908.087.928.077.953.067.981.057 1.016.046 1.061.036 1.123.025 1.227.016L50 100l1.569-.005 1.227-.016 1.123-.025 1.061-.036 1.016-.046.981-.057.953-.067.928-.077.908-.087.89-.098.872-.107.857-.118.843-.128.83-.139.817-.148.805-.159.794-.168.783-.179.772-.189.762-.199.752-.209.743-.219.733-.229.723-.239.714-.248.705-.259.696-.269.688-.278.678-.288.67-.299.66-.307.653-.318.643-.327.635-.337.626-.346.617-.356.609-.366.6-.375.592-.384.583-.394.574-.403.566-.413.556-.421.548-.432.54-.44.53-.449.521-.459.513-.468.504-.477.495-.485.485-.495.477-.504.468-.513.459-.521.449-.53.44-.54.432-.548.421-.556.413-.566.403-.574.394-.583.384-.592.375-.6.366-.609.356-.617.346-.626.337-.635.327-.643.318-.653.307-.66.299-.67.288-.678.278-.688.269-.696.259-.705.248-.714.239-.723.229-.733.219-.743.209-.752.199-.762.189-.772.179-.783.168-.794.159-.805.148-.817.139-.83.128-.843.118-.857.107-.872.098-.89.087-.908.077-.928.067-.953.057-.981.046-1.016.036-1.061.025-1.123.016-1.227L100 50Z",
      ),
      Mask(
        "Teardrops",
        "M50 0C22.4 0 0 22.4 0 50s22.4 50 50 50H88c6.6 0 12-5.4 12-12V50C100 22.4 77.6 0 50 0Z",
      ),
    )

  @OptIn(ExperimentalStdlibApi::class)
  fun LazyListScope.fallback(context: Context) {
    switchPreference(
      icon = { Icon(Icons.Outlined.SettingsBackupRestore, Pref.ICON_FALLBACK.key) },
      key = Pref.ICON_FALLBACK.key,
      defaultValue = Pref.ICON_FALLBACK.def,
      title = { TwoLineText(stringResource(R.string.iconFallback)) },
      summary = { TwoLineText(stringResource(R.string.iconFallbackSummary)) },
    )
    mySwitchPreference(
      icon = {},
      enabled = { it.get(Pref.ICON_FALLBACK) },
      key = Pref.SCALE_ONLY_FOREGROUND.key,
      defaultValue = Pref.SCALE_ONLY_FOREGROUND.def,
      title = { TwoLineText(stringResource(R.string.scaleOnlyForeground)) },
    )
    mySwitchPreference(
      icon = {},
      enabled = { it.get(Pref.ICON_FALLBACK) },
      key = Pref.BACK_AS_ADAPTIVE_BACK.key,
      defaultValue = Pref.BACK_AS_ADAPTIVE_BACK.def,
      title = { TwoLineText(stringResource(R.string.backAsAdaptiveBack)) },
    )
    mySliderPreference(
      icon = {},
      enabled = { it.get(Pref.ICON_FALLBACK) },
      key = Pref.NON_ADAPTIVE_SCALE.key,
      defaultValue = Pref.NON_ADAPTIVE_SCALE.def,
      valueRange = 0.1f..1.5f,
      valueSteps = 13,
      title = { TwoLineText(stringResource(R.string.nonAdaptiveScale)) },
      summary = { OneLineText("%.2f".format(it)) },
      valueToText = { "%.2f".format(it) },
    )
    mySwitchPreference(
      icon = {},
      enabled = { it.get(Pref.ICON_FALLBACK) },
      key = Pref.CONVERT_TO_ADAPTIVE.key,
      defaultValue = Pref.CONVERT_TO_ADAPTIVE.def,
      title = { TwoLineText(stringResource(R.string.convertToAdaptive)) },
      summary = { TwoLineText(stringResource(R.string.convertToAdaptiveSummary)) },
    )
    item { HorizontalDivider() }
    mySwitchPreference(
      icon = {},
      enabled = { it.get(Pref.ICON_FALLBACK) },
      key = Pref.OVERRIDE_ICON_FALLBACK.key,
      defaultValue = Pref.OVERRIDE_ICON_FALLBACK.def,
      title = { TwoLineText(stringResource(R.string.overrideIconFallback)) },
      summary = { TwoLineText(stringResource(R.string.overrideIconFallbackSummary)) },
    )
    mySliderPreference(
      icon = { Icon(Icons.Outlined.PhotoSizeSelectSmall, Pref.ICON_PACK_SCALE.key) },
      enabled = { it.get(Pref.ICON_FALLBACK) && it.get(Pref.OVERRIDE_ICON_FALLBACK) },
      key = Pref.ICON_PACK_SCALE.key,
      defaultValue = Pref.ICON_PACK_SCALE.def,
      valueRange = 0.1f..1.5f,
      valueSteps = 13,
      title = { TwoLineText(stringResource(R.string.iconPackScale)) },
      summary = { OneLineText("%.2f".format(it)) },
      valueToText = { "%.2f".format(it) },
    )
    dialogPreference(
      icon = { Icon(Icons.Outlined.ShapeLine, Pref.ICON_PACK_SHAPE.key) },
      enabled = { it.get(Pref.ICON_FALLBACK) && it.get(Pref.OVERRIDE_ICON_FALLBACK) },
      key = Pref.ICON_PACK_SHAPE.key,
      defaultValue = Pref.ICON_PACK_SHAPE.def,
      title = { TwoLineText(stringResource(R.string.iconPackShape)) },
      summary = {
        val iconColor = LocalPreferenceTheme.current.iconColor
        AnimatedContent(it) {
          if (it.isNotEmpty())
            Icon(
              DrawablePainter(PathDrawable(it, iconColor)),
              contentDescription = Pref.ICON_PACK_SHAPE.key,
              modifier = Modifier.size(26.dp).padding(4.dp),
            )
        }
      },
    ) { state, dismiss ->
      val iconColor = LocalPreferenceTheme.current.iconColor

      val openCustomizeDialog = rememberSaveable { mutableStateOf(false) }
      var value by state
      Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        LazyVerticalGrid(
          modifier = Modifier.heightIn(max = 500.dp).padding(vertical = 16.dp),
          columns = GridCells.Adaptive(minSize = 74.dp),
        ) {
          items(masks, key = { it.name }) {
            Column(
              modifier =
                Modifier.clip(MaterialTheme.shapes.medium)
                  .clickable {
                    value = it.pathData
                    dismiss()
                  }
                  .fillMaxWidth()
                  .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
              if (it.pathData.isNotEmpty())
                Image(
                  remember(it) { DrawablePainter(PathDrawable(it.pathData, iconColor)) },
                  contentDescription = it.name,
                  modifier = Modifier.padding(horizontal = 12.dp).aspectRatio(1f),
                  contentScale = ContentScale.Crop,
                )
              else
                Icon(
                  Icons.Outlined.CropOriginal,
                  contentDescription = it.name,
                  modifier = Modifier.padding(horizontal = 12.dp).aspectRatio(1f),
                  tint = iconColor,
                )
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                it.name,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
        }
        TextButton(onClick = { openCustomizeDialog.value = true }) {
          Text(text = stringResource(R.string.customize))
        }
      }

      TextFieldDialog(
        openCustomizeDialog,
        title = { Text(stringResource(R.string.pathData)) },
        initValue = value,
      ) {
        runCatchingToastOnMain(context) {
          if (PathParser.createPathFromPathData(it).isEmpty) throw Exception("Not a valid path!")
          value = it
          dismiss()
        }
      }
    }
    dialogPreference(
      icon = { Icon(Icons.Outlined.ColorLens, Pref.ICON_PACK_SHAPE.key) },
      enabled = {
        it.get(Pref.ICON_FALLBACK) &&
          it.get(Pref.OVERRIDE_ICON_FALLBACK) &&
          it.get(Pref.ICON_PACK_SHAPE).isNotEmpty()
      },
      key = Pref.ICON_PACK_SHAPE_COLOR.key,
      defaultValue = Pref.ICON_PACK_SHAPE_COLOR.def,
      title = { TwoLineText(stringResource(R.string.iconPackShapeColor)) },
      summary = { value ->
        AnimatedContent(Color(value)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier =
                Modifier.size(26.dp).padding(4.dp).drawBehind {
                  drawRoundRect(it, cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()))
                }
            )
            OneLineText("#" + value.toHexString())
          }
        }
      },
    ) { state, dismiss ->
      TextFieldDialogContent(
        initValue = state.value.toHexString(),
        prefix = { Text("#") },
        trailingIcon = { IconButtonWithTooltip(Icons.Outlined.Clear, "Clear") { it.value = "" } },
        onCancel = dismiss,
      ) {
        runCatchingToastOnMain(context) {
          state.value = "#$it".toColorInt()
          dismiss()
        }
      }
    }
    mySwitchPreference(
      icon = { Icon(Icons.Outlined.FlipToFront, Pref.ICON_PACK_ENABLE_UPON.key) },
      enabled = { it.get(Pref.ICON_FALLBACK) && it.get(Pref.OVERRIDE_ICON_FALLBACK) },
      key = Pref.ICON_PACK_ENABLE_UPON.key,
      defaultValue = Pref.ICON_PACK_ENABLE_UPON.def,
      title = { TwoLineText(stringResource(R.string.iconPackEnableUpon)) },
    )
  }

  @Composable
  fun Pixel(modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier) {
      dialogPreference(
        icon = { Icon(Icons.Outlined.Apps, Pref.PIXEL_LAUNCHER_PACKAGE.key) },
        key = Pref.PIXEL_LAUNCHER_PACKAGE.key,
        defaultValue = Pref.PIXEL_LAUNCHER_PACKAGE.def,
        title = { TwoLineText(stringResource(R.string.pixelLauncherPackage)) },
        summary = {
          TwoLineText(
            if (it == Pref.PIXEL_LAUNCHER_PACKAGE.def)
              stringResource(R.string.pixelLauncherPackageSummary)
            else it
          )
        },
      ) { state, dismiss ->
        TextFieldDialogContent(
          initValue = state.value,
          singleLine = false,
          maxLines = 2,
          onCancel = dismiss,
          trailingIcon = {
            IconButtonWithTooltip(Icons.Outlined.Restore, "Restore") {
              it.value = Pref.PIXEL_LAUNCHER_PACKAGE.def
            }
          },
        ) {
          state.value = it
          dismiss()
        }
      }
      switchPreference(
        icon = {},
        key = Pref.NO_SHADOW.key,
        defaultValue = Pref.NO_SHADOW.def,
        title = { TwoLineText(stringResource(R.string.noShadow)) },
        summary = { TwoLineText(stringResource(R.string.noShadowSummary)) },
      )
      item { HorizontalDivider() }
      switchPreference(
        icon = { Icon(Icons.Outlined.CalendarMonth, Pref.FORCE_LOAD_CLOCK_AND_CALENDAR.key) },
        key = Pref.FORCE_LOAD_CLOCK_AND_CALENDAR.key,
        defaultValue = Pref.FORCE_LOAD_CLOCK_AND_CALENDAR.def,
        title = { TwoLineText(stringResource(R.string.forceLoadClockAndCalendar)) },
      )
      mySwitchPreference(
        icon = {},
        enabled = { it.get(Pref.FORCE_LOAD_CLOCK_AND_CALENDAR) },
        key = Pref.CLOCK_USE_FALLBACK_MASK.key,
        defaultValue = Pref.CLOCK_USE_FALLBACK_MASK.def,
        title = { TwoLineText(stringResource(R.string.clockUseFallbackMask)) },
      )
      mySwitchPreference(
        icon = {},
        enabled = { it.get(Pref.FORCE_LOAD_CLOCK_AND_CALENDAR) },
        key = Pref.DISABLE_CLOCK_SECONDS.key,
        defaultValue = Pref.DISABLE_CLOCK_SECONDS.def,
        title = { TwoLineText(stringResource(R.string.disableClockSeconds)) },
      )
      item { HorizontalDivider() }
      switchPreference(
        icon = {},
        key = Pref.FORCE_ACTIVITY_ICON_FOR_TASK.key,
        defaultValue = Pref.FORCE_ACTIVITY_ICON_FOR_TASK.def,
        title = { TwoLineText(stringResource(R.string.forceActivityIconForTask)) },
        summary = { TwoLineText(stringResource(R.string.forceActivityIconForTaskSummary)) },
      )
    }
  }

  fun modeToAnnotatedString(context: Context, mode: String, typography: Typography) =
    buildAnnotatedString {
      withStyle(typography.titleMedium.toSpanStyle()) { append(modeToTitle(context, mode) + "\n") }
      withStyle(typography.bodyMedium.toSpanStyle()) { append(modeToSummary(context, mode)) }
    }

  @Composable
  fun ModeToIcon(mode: String) =
    when (mode) {
      MODE_SHARE -> Icon(Icons.Outlined.Share, mode)
      MODE_PROVIDER -> Icon(Icons.Outlined.SettingsRemote, mode)
      MODE_LOCAL -> Icon(Icons.Outlined.Memory, mode)
      else -> {}
    }

  fun modeToTitle(context: Context, mode: String) =
    when (mode) {
      MODE_SHARE -> context.getString(R.string.shareMode)
      MODE_PROVIDER -> context.getString(R.string.providerMode)
      MODE_LOCAL -> context.getString(R.string.localMode)
      else -> mode
    }

  fun modeToSummary(context: Context, mode: String) =
    when (mode) {
      MODE_SHARE -> context.getString(R.string.shareModeSummary)
      MODE_PROVIDER -> context.getString(R.string.providerModeSummary)
      MODE_LOCAL -> context.getString(R.string.localModeSummary)
      else -> mode
    }
}
