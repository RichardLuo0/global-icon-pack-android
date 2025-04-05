package com.richardluo.globalIconPack.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import me.zhanghai.compose.preference.Preferences
import me.zhanghai.compose.preference.SliderPreference
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

fun LazyListScope.sliderPreference(
  key: String,
  defaultValue: Float,
  title: @Composable (Float) -> Unit,
  modifier: Modifier = Modifier.fillMaxWidth(),
  rememberState: @Composable () -> MutableState<Float> = {
    rememberPreferenceState(key, defaultValue)
  },
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  valueSteps: Int = 0,
  rememberSliderState: @Composable (Float) -> MutableState<Float> = {
    remember { mutableFloatStateOf(it) }
  },
  enabled: (Preferences) -> Boolean = { true },
  icon: @Composable ((Float) -> Unit)? = null,
  summary: @Composable ((Float) -> Unit)? = null,
  valueText: @Composable ((Float) -> Unit)? = null,
  valueToText: (Float) -> String = { it.toString() },
  textToValue: (String) -> Float = { it.toFloat() },
) {
  item(key = key, contentType = "MySliderPreference") {
    var dialogState = remember { mutableStateOf(false) }

    val state = rememberState()
    var value by state
    val sliderState = rememberSliderState(value)
    val sliderValue by sliderState
    SliderPreference(
      state = state,
      title = { title(sliderValue) },
      modifier = modifier.clickable { dialogState.value = true },
      valueRange = valueRange,
      valueSteps = valueSteps,
      sliderState = sliderState,
      enabled = enabled(LocalPreferenceFlow.current.getValue()),
      icon = icon?.let { { it(sliderValue) } },
      summary = summary?.let { { it(sliderValue) } },
      valueText = valueText?.let { { it(sliderValue) } },
    )

    if (dialogState.value) {
      var dialogText by
        rememberSaveable(stateSaver = TextFieldValue.Saver) {
          val text = valueToText(value)
          mutableStateOf(TextFieldValue(text, TextRange(text.length)))
        }
      val onOk = { runCatching { value = textToValue(dialogText.text) } }
      InfoDialog(
        dialogState,
        title = { title(sliderValue) },
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
}
