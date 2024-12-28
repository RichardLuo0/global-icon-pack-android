package com.richardluo.globalIconPack.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import me.zhanghai.compose.preference.SliderPreference
import me.zhanghai.compose.preference.rememberPreferenceState

@Composable
inline fun ComposableSliderPreference(
  key: String,
  defaultValue: Float,
  crossinline title: @Composable (Float) -> Unit,
  modifier: Modifier = Modifier.fillMaxWidth(),
  crossinline rememberState: @Composable () -> MutableState<Float> = {
    rememberPreferenceState(key, defaultValue)
  },
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  valueSteps: Int = 0,
  crossinline rememberSliderState: @Composable (Float) -> MutableState<Float> = {
    remember { mutableFloatStateOf(it) }
  },
  crossinline enabled: (Float) -> Boolean = { true },
  noinline icon: @Composable ((Float) -> Unit)? = null,
  noinline summary: @Composable ((Float) -> Unit)? = null,
  noinline valueText: @Composable ((Float) -> Unit)? = null,
) {
  val state = rememberState()
  val value by state
  val sliderState = rememberSliderState(value)
  val sliderValue by sliderState
  SliderPreference(
    state = state,
    title = { title(sliderValue) },
    modifier = modifier,
    valueRange = valueRange,
    valueSteps = valueSteps,
    sliderState = sliderState,
    enabled = enabled(value),
    icon = icon?.let { { it(sliderValue) } },
    summary = summary?.let { { it(sliderValue) } },
    valueText = valueText?.let { { it(sliderValue) } },
  )
}
