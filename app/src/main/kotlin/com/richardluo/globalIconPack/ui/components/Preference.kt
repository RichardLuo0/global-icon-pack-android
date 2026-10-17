package com.richardluo.globalIconPack.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Label
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import com.richardluo.globalIconPack.utils.consumable
import com.richardluo.globalIconPack.utils.getValue
import me.zhanghai.compose.preference.LocalPreferenceFlow
import me.zhanghai.compose.preference.LocalPreferenceTheme
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.PreferenceTheme
import me.zhanghai.compose.preference.Preferences
import me.zhanghai.compose.preference.ProvidePreferenceTheme
import me.zhanghai.compose.preference.preferenceTheme
import me.zhanghai.compose.preference.rememberPreferenceState

@Composable
fun myPreferenceTheme() = preferenceTheme(iconColor = MaterialTheme.colorScheme.secondary)

@Composable
fun ProvideMyPreferenceTheme(
  theme: PreferenceTheme = myPreferenceTheme(),
  content: @Composable (() -> Unit),
) = ProvidePreferenceTheme(theme, content)

val LocalPreferenceLock = compositionLocalOf { false }

inline fun LazyListScope.myPreference(
  key: String,
  noinline title: @Composable () -> Unit,
  crossinline modifier: @Composable (InteractionSource) -> Modifier = { Modifier.fillMaxWidth() },
  crossinline enabled: (Preferences) -> Boolean = { true },
  noinline icon: @Composable (() -> Unit)? = null,
  noinline summary: @Composable (() -> Unit)? = null,
  noinline widgetContainer: @Composable (() -> Unit)? = null,
  noinline onClick: (() -> Unit)? = null,
) {
  item(key = key, contentType = "MyPreference") {
    val interactionSource = remember { MutableInteractionSource() }
    Preference(
      title = title,
      modifier =
        modifier(interactionSource)
          .then(
            onClick?.let {
              Modifier.clickable(interactionSource, ripple(), onClick = onClick)
            } ?: Modifier
          ),
      enabled = enabled(LocalPreferenceFlow.current.getValue()),
      icon = icon,
      summary = summary,
      widgetContainer = widgetContainer,
    )
  }
}

inline fun <T> LazyListScope.myListPreference(
  key: String,
  defaultValue: T,
  values: List<T>,
  crossinline title: @Composable (T) -> Unit,
  crossinline modifier: @Composable (InteractionSource) -> Modifier = { Modifier.fillMaxWidth() },
  crossinline rememberState: @Composable () -> MutableState<T> = {
    rememberPreferenceState(key, defaultValue)
  },
  crossinline enabled: (Preferences) -> Boolean = { true },
  noinline icon: @Composable ((T) -> Unit)? = null,
  noinline summary: @Composable ((T) -> Unit)? = null,
  noinline item:
    @Composable
    (pos: ListItemPos, value: T, currentValue: T, onClick: () -> Unit) -> Unit,
) {
  item(key = key, contentType = "MyListPreference") {
    val state = rememberState()
    var value by state
    val title = @Composable { title(value) }
    val openSelector = rememberSaveable { mutableStateOf(false) }
    LazyListDialog(openSelector, title, values, focusItem = { it == value }) {
      pos,
      itemValue,
      dismiss ->
      item(pos, itemValue, value) {
        value = itemValue
        dismiss()
      }
    }
    val interactionSource = remember { MutableInteractionSource() }
    val enabled = !LocalPreferenceLock.current && enabled(LocalPreferenceFlow.current.getValue())
    Preference(
      title = title,
      modifier =
        modifier(interactionSource)
          .then(
            if (enabled)
              Modifier.clickable(interactionSource, ripple()) {
                openSelector.value = true
              }
            else Modifier
          ),
      enabled = enabled,
      icon = icon?.let { { it(value) } },
      summary = summary?.let { { it(value) } },
    )
  }
}

inline fun <T, U> LazyListScope.mapListPreference(
  key: String,
  defaultValue: T,
  crossinline getValueMap: @Composable () -> Map<T, U>?,
  crossinline title: @Composable (T) -> Unit,
  crossinline modifier: @Composable (InteractionSource) -> Modifier = { Modifier.fillMaxWidth() },
  crossinline rememberState: @Composable () -> MutableState<T> = {
    rememberPreferenceState(key, defaultValue)
  },
  crossinline enabled: (Preferences) -> Boolean = { true },
  noinline icon: @Composable ((T) -> Unit)? = null,
  noinline summary: @Composable ((T, U?) -> Unit)? = null,
  noinline item:
    @Composable
    (pos: ListItemPos, key: T, value: U, currentKey: T, onClick: () -> Unit) -> Unit,
) {
  item(key = key, contentType = "MapListPreference") {
    val state = rememberState()
    val valueMap = getValueMap()
    var valueKey by state
    val title = @Composable { title(valueKey) }
    val openSelector = rememberSaveable { mutableStateOf(false) }
    LazyListDialog(openSelector, title, valueMap?.keys?.toList(), focusItem = { it == valueKey }) {
      pos,
      key,
      dismiss ->
      item(pos, key, valueMap!!.getValue(key), valueKey) {
        valueKey = key
        dismiss()
      }
    }
    val interactionSource = remember { MutableInteractionSource() }
    val enabled = !LocalPreferenceLock.current && enabled(LocalPreferenceFlow.current.getValue())
    Preference(
      title = title,
      modifier =
        modifier(interactionSource)
          .then(
            if (enabled)
              Modifier.clickable(interactionSource, ripple()) {
                openSelector.value = true
              }
            else Modifier
          ),
      enabled = enabled,
      icon = icon?.let { { it(valueKey) } },
      summary = summary?.let { { it(valueKey, valueMap?.get(valueKey)) } },
    )
  }
}

inline fun <T> LazyListScope.dialogPreference(
  key: String,
  defaultValue: T,
  crossinline title: @Composable (T) -> Unit,
  crossinline modifier: @Composable (InteractionSource) -> Modifier = { Modifier.fillMaxWidth() },
  crossinline rememberState: @Composable () -> MutableState<T> = {
    rememberPreferenceState(key, defaultValue)
  },
  crossinline enabled: (Preferences) -> Boolean = { true },
  noinline icon: @Composable ((T) -> Unit)? = null,
  noinline summary: @Composable ((T) -> Unit)? = null,
  noinline content: @Composable (MutableState<T>, dismiss: () -> Unit) -> Unit,
) {
  item(key = key, contentType = "DialogPreference") {
    val state = rememberState()
    val value by state
    val title = @Composable { title(value) }
    val openDialog = rememberSaveable { mutableStateOf(false) }
    CustomDialog(openDialog, title = title) { content(state) { openDialog.value = false } }
    val interactionSource = remember { MutableInteractionSource() }
    val enabled = !LocalPreferenceLock.current && enabled(LocalPreferenceFlow.current.getValue())
    Preference(
      title = title,
      modifier =
        modifier(interactionSource)
          .then(
            if (enabled)
              Modifier.clickable(interactionSource, ripple()) {
                openDialog.value = true
              }
            else Modifier
          ),
      enabled = enabled,
      icon = icon?.let { { it(value) } },
      summary = summary?.let { { it(value) } },
    )
  }
}

inline fun LazyListScope.mySliderPreference(
  key: String,
  defaultValue: Float,
  noinline title: @Composable (Float) -> Unit,
  crossinline modifier: @Composable (InteractionSource) -> Modifier = { Modifier.fillMaxWidth() },
  noinline rememberState: @Composable () -> MutableState<Float> = {
    rememberPreferenceState(key, defaultValue)
  },
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  valueSteps: Int = 0,
  crossinline rememberSliderState: @Composable (Float) -> SliderState = {
    rememberSliderState(
      value = it,
      steps = valueSteps,
      trackRange = valueRange,
    )
  },
  crossinline enabled: (Preferences) -> Boolean = { true },
  noinline icon: @Composable ((Float) -> Unit)? = null,
  noinline summary: @Composable ((Float) -> Unit)? = null,
  noinline valueToText: (Float) -> String = { it.toString() },
  noinline textToValue: (String) -> Float = { it.toFloat() },
) {
  item(key = key, contentType = "MySliderPreference") {
    val state = rememberState()
    var value by state
    val sliderState = rememberSliderState(value)
    val sliderValue = sliderState.value
    val interactionSource = remember { MutableInteractionSource() }
    MySliderPreference(
      state = sliderState,
      onValueChange = { value = it },
      title = { title(sliderValue) },
      modifier = modifier(interactionSource),
      interactionSource = interactionSource,
      enabled = !LocalPreferenceLock.current && enabled(LocalPreferenceFlow.current.getValue()),
      icon = icon?.let { { it(sliderValue) } },
      summary = summary?.let { { it(sliderValue) } },
      valueToText = valueToText,
      textToValue = textToValue,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MySliderPreference(
  state: SliderState,
  onValueChange: (Float) -> Unit,
  title: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  interactionSource: MutableInteractionSource? = null,
  enabled: Boolean = true,
  icon: @Composable (() -> Unit)? = null,
  summary: @Composable (() -> Unit)? = null,
  valueToText: (Float) -> String = { it.toString() },
  textToValue: (String) -> Float = { it.toFloat() },
) {
  val dialogState = remember { mutableStateOf(false) }

  Preference(
    title = title,
    modifier =
      modifier.clickable(enabled, interactionSource = interactionSource) {
        dialogState.value = true
      },
    enabled = enabled,
    icon = icon,
    summary = {
      Column {
        summary?.invoke()

        val interactionSource = remember { MutableInteractionSource() }
        Slider(
          state = state,
          onValueChange = { state.value = it },
          enabled = enabled,
          onValueChangeFinished = { onValueChange(state.value) },
          interactionSource = interactionSource,
          thumb = {
            Label(
              label = { PlainTooltip(content = { Text(valueToText(state.value)) }) },
              interactionSource = interactionSource,
              content =
                @Composable {
                  SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    colors = SliderDefaults.colors(),
                    enabled = enabled,
                  )
                },
            )
          },
        )
      }
    },
  )

  TextFieldDialog(
    dialogState,
    title = title,
    initValue = valueToText(state.value),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
  ) {
    val value = textToValue(it.toString())
    onValueChange(value)
    state.value = value
  }
}

inline fun LazyListScope.mySwitchPreference(
  key: String,
  defaultValue: Boolean,
  crossinline title: @Composable (Boolean) -> Unit,
  crossinline modifier: @Composable (InteractionSource) -> Modifier = { Modifier.fillMaxWidth() },
  crossinline rememberState: @Composable () -> MutableState<Boolean> = {
    rememberPreferenceState(key, defaultValue)
  },
  crossinline enabled: (Preferences) -> Boolean = { true },
  noinline icon: @Composable ((Boolean) -> Unit)? = null,
  noinline summary: @Composable ((Boolean) -> Unit)? = null,
) {
  item(key = key, contentType = "MySwitchPreference") {
    val state = rememberState()
    val value by state
    val interactionSource = remember { MutableInteractionSource() }
    MySwitchPreference(
      state = state,
      title = { title(value) },
      modifier = modifier(interactionSource),
      interactionSource = interactionSource,
      enabled = !LocalPreferenceLock.current && enabled(LocalPreferenceFlow.current.getValue()),
      icon = icon?.let { { it(value) } },
      summary = summary?.let { { it(value) } },
    )
  }
}

@Composable
fun MySwitchPreference(
  state: MutableState<Boolean>,
  title: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  interactionSource: MutableInteractionSource? = null,
  enabled: Boolean = true,
  icon: @Composable (() -> Unit)? = null,
  summary: @Composable (() -> Unit)? = null,
) {
  var value by state
  Preference(
    title = title,
    modifier = modifier.toggleable(value, enabled, Role.Switch, interactionSource) { value = it },
    enabled = enabled,
    icon = icon,
    summary = summary,
    widgetContainer = {
      val theme = LocalPreferenceTheme.current
      Switch(
        checked = value,
        onCheckedChange = null,
        modifier =
          Modifier.padding(
            theme.padding.consumable().apply { start = theme.horizontalSpacing }.consume()
          ),
        enabled = enabled,
        thumbContent = {
          Icon(
            if (value) Icons.Outlined.Check else Icons.Outlined.Close,
            "Thumb",
            modifier = Modifier.size(SwitchDefaults.IconSize),
          )
        },
      )
    },
  )
}
