package com.richardluo.globalIconPack.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Label
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.richardluo.globalIconPack.utils.getValue
import me.zhanghai.compose.preference.LocalPreferenceFlow
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.PreferenceTheme
import me.zhanghai.compose.preference.Preferences
import me.zhanghai.compose.preference.ProvidePreferenceTheme
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.preferenceTheme
import me.zhanghai.compose.preference.rememberPreferenceState

@Composable
fun myPreferenceTheme() = preferenceTheme(iconColor = MaterialTheme.colorScheme.secondary)

@Composable
fun ProvideMyPreferenceTheme(
  theme: PreferenceTheme = myPreferenceTheme(),
  content: @Composable (() -> Unit),
) = ProvidePreferenceTheme(theme, content)

fun LazyListScope.myPreference(
  key: String,
  title: @Composable () -> Unit,
  modifier: Modifier = Modifier.fillMaxWidth(),
  enabled: (Preferences) -> Boolean = { true },
  icon: @Composable (() -> Unit)? = null,
  summary: @Composable (() -> Unit)? = null,
  widgetContainer: @Composable (() -> Unit)? = null,
  onClick: (() -> Unit)? = null,
) {
  item(key = key, contentType = "MyPreference") {
    Preference(
      title = title,
      modifier = modifier,
      enabled = enabled(LocalPreferenceFlow.current.getValue()),
      icon = icon,
      summary = summary,
      widgetContainer = widgetContainer,
      onClick = onClick,
    )
  }
}

inline fun <T, U> LazyListScope.mapListPreference(
  key: String,
  defaultValue: T,
  crossinline getValueMap: @Composable () -> Map<T, U>?,
  crossinline title: @Composable (T) -> Unit,
  modifier: Modifier = Modifier.fillMaxWidth(),
  crossinline rememberState: @Composable () -> MutableState<T> = {
    rememberPreferenceState(key, defaultValue)
  },
  crossinline enabled: (Preferences) -> Boolean = { true },
  noinline icon: @Composable ((T) -> Unit)? = null,
  noinline summary: @Composable ((T, U?) -> Unit)? = null,
  noinline item: @Composable (key: T, value: U, currentKey: T, onClick: () -> Unit) -> Unit,
) {
  item(key = key, contentType = "MapListPreference") {
    val state = rememberState()
    val valueMap = getValueMap()
    var valueKey by state
    val title = @Composable { title(valueKey) }
    val openSelector = rememberSaveable { mutableStateOf(false) }
    LazyListDialog(openSelector, title, valueMap?.keys?.toList(), focusItem = { it == valueKey }) {
      key,
      dismiss ->
      item(key, valueMap!!.getValue(key), valueKey) {
        valueKey = key
        dismiss()
      }
    }
    Preference(
      title = title,
      modifier = modifier,
      enabled = enabled(LocalPreferenceFlow.current.getValue()),
      icon = icon?.let { { it(valueKey) } },
      summary = summary?.let { { it(valueKey, valueMap?.get(valueKey)) } },
    ) {
      openSelector.value = true
    }
  }
}

inline fun <T> LazyListScope.dialogPreference(
  key: String,
  defaultValue: T,
  crossinline title: @Composable (T) -> Unit,
  modifier: Modifier = Modifier.fillMaxWidth(),
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
    Preference(
      title = title,
      modifier = modifier,
      enabled = enabled(LocalPreferenceFlow.current.getValue()),
      icon = icon?.let { { it(value) } },
      summary = summary?.let { { it(value) } },
    ) {
      openDialog.value = true
    }
  }
}

inline fun LazyListScope.mySliderPreference(
  key: String,
  defaultValue: Float,
  noinline title: @Composable (Float) -> Unit,
  modifier: Modifier = Modifier.fillMaxWidth(),
  noinline rememberState: @Composable () -> MutableState<Float> = {
    rememberPreferenceState(key, defaultValue)
  },
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  valueSteps: Int = 0,
  crossinline rememberSliderState: @Composable (Float) -> MutableState<Float> = {
    remember { mutableFloatStateOf(it) }
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
    var sliderValue by sliderState
    MySliderPreference(
      value = value,
      onValueChange = { value = it },
      sliderValue = sliderValue,
      onSliderValueChange = { sliderValue = it },
      title = { title(sliderValue) },
      modifier = modifier,
      valueRange = valueRange,
      valueSteps = valueSteps,
      enabled = enabled(LocalPreferenceFlow.current.getValue()),
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
  value: Float,
  onValueChange: (Float) -> Unit,
  sliderValue: Float,
  onSliderValueChange: (Float) -> Unit,
  title: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  valueSteps: Int = 0,
  enabled: Boolean = true,
  icon: @Composable (() -> Unit)? = null,
  summary: @Composable (() -> Unit)? = null,
  valueToText: (Float) -> String = { it.toString() },
  textToValue: (String) -> Float = { it.toFloat() },
) {
  var lastValue by remember { mutableFloatStateOf(value) }
  SideEffect {
    if (value != lastValue) {
      onSliderValueChange(value)
      lastValue = value
    }
  }
  var dialogState = remember { mutableStateOf(false) }

  Preference(
    title = title,
    modifier = modifier.clickable { dialogState.value = true },
    enabled = enabled,
    icon = icon,
    summary = {
      Column {
        summary?.invoke()
        // onValueChangeFinished() may be invoked before a recomposition has
        // happened for onValueChange(), for example in the clicking case, so make
        // onValueChange() share the latest value to onValueChangeFinished().
        var latestSliderValue = sliderValue
        val interactionSource = remember { MutableInteractionSource() }
        Slider(
          value = sliderValue,
          onValueChange = {
            onSliderValueChange(it)
            latestSliderValue = it
          },
          enabled = enabled,
          valueRange = valueRange,
          steps = valueSteps,
          onValueChangeFinished = { onValueChange(latestSliderValue) },
          interactionSource = interactionSource,
          thumb = {
            Label(
              label = { PlainTooltip(content = { Text(valueToText(sliderValue)) }) },
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
    initValue = valueToText(value),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
  ) {
    onValueChange(textToValue(it))
  }
}

inline fun LazyListScope.mySwitchPreference(
  key: String,
  defaultValue: Boolean,
  crossinline title: @Composable (Boolean) -> Unit,
  modifier: Modifier = Modifier.fillMaxWidth(),
  crossinline rememberState: @Composable () -> MutableState<Boolean> = {
    rememberPreferenceState(key, defaultValue)
  },
  crossinline enabled: (Preferences) -> Boolean = { true },
  noinline icon: @Composable ((Boolean) -> Unit)? = null,
  noinline summary: @Composable ((Boolean) -> Unit)? = null,
) {
  item(key = key, contentType = "SwitchPreference") {
    val state = rememberState()
    val value by state
    SwitchPreference(
      state = state,
      title = { title(value) },
      modifier = modifier,
      enabled = enabled(LocalPreferenceFlow.current.getValue()),
      icon = icon?.let { { it(value) } },
      summary = summary?.let { { it(value) } },
    )
  }
}
