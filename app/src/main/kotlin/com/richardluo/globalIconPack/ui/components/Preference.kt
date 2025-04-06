package com.richardluo.globalIconPack.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Label
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.richardluo.globalIconPack.utils.getValue
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.LocalPreferenceFlow
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.Preferences
import me.zhanghai.compose.preference.preferenceTheme
import me.zhanghai.compose.preference.rememberPreferenceState

@Composable
fun myPreferenceTheme() = preferenceTheme(iconColor = MaterialTheme.colorScheme.secondary)

inline fun <T, U> LazyListScope.mapListPreference(
  key: String,
  defaultValue: T,
  crossinline getValueMap: @Composable () -> Map<T, U>,
  crossinline title: @Composable (T) -> Unit,
  modifier: Modifier = Modifier.fillMaxWidth(),
  crossinline rememberState: @Composable () -> MutableState<T> = {
    rememberPreferenceState(key, defaultValue)
  },
  crossinline enabled: (T) -> Boolean = { true },
  noinline icon: @Composable ((T) -> Unit)? = null,
  noinline summary: @Composable ((T, U?) -> Unit)? = null,
  noinline item: @Composable (key: T, value: U, currentKey: T, onClick: () -> Unit) -> Unit,
) {
  item(key = key, contentType = "lazyListPreference") {
    val state = rememberState()
    val valueMap = getValueMap()
    val valueKey by state
    ListPreference(
      state = state,
      modifier = modifier,
      values = valueMap.keys.toList(),
      title = { title(valueKey) },
      enabled = enabled(valueKey),
      icon = icon?.let { { it(valueKey) } },
      summary = summary?.let { { it(valueKey, valueMap[valueKey]) } },
      item = { key, currentKey, onClick -> item(key, valueMap.getValue(key), currentKey, onClick) },
    )
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

  if (dialogState.value) {
    var dialogText by
      rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val text = valueToText(value)
        mutableStateOf(TextFieldValue(text, TextRange(text.length)))
      }
    val onOk = { runCatching { onValueChange(textToValue(dialogText.text)) } }
    InfoDialog(
      dialogState,
      title = { title() },
      content = {
        val focusRequester = remember { FocusRequester() }
        OutlinedTextField(
          value = dialogText,
          onValueChange = { dialogText = it },
          modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
          keyboardActions = KeyboardActions { onOk() },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
        )
        LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
      },
      onOk = { onOk() },
    )
  }
}
