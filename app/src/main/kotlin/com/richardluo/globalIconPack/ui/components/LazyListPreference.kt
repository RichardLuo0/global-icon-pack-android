package com.richardluo.globalIconPack.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.rememberPreferenceState

@OptIn(ExperimentalComposeUiApi::class)
inline fun <T, U> LazyListScope.lazyListPreference(
  key: String,
  defaultValue: T,
  crossinline title: @Composable (T) -> Unit,
  modifier: Modifier = Modifier.fillMaxWidth(),
  crossinline rememberState: @Composable () -> MutableState<T> = {
    rememberPreferenceState(key, defaultValue)
  },
  crossinline enabled: (T) -> Boolean = { true },
  noinline icon: @Composable ((T) -> Unit)? = null,
  noinline summary: @Composable ((T, U?) -> Unit)? = null,
  crossinline load: suspend () -> Map<T, U>,
  noinline item: @Composable (key: T, value: U, currentKey: T, onClick: () -> Unit) -> Unit,
) {
  item(key = key, contentType = "lazyListPreference") {
    val coroutine = rememberCoroutineScope()
    var valueMap by remember { mutableStateOf<Map<T, U>>(mapOf()) }
    LaunchedEffect(Unit) { valueMap = load() }
    val state = rememberState()
    val valueKey by state
    Box(
      modifier =
        modifier.pointerInteropFilter {
          coroutine.launch { valueMap = load() }
          false
        }
    ) {
      ListPreference(
        state = state,
        modifier = modifier,
        values = valueMap.keys.toList(),
        title = { title(valueKey) },
        enabled = enabled(valueKey),
        icon = icon?.let { { it(valueKey) } },
        summary = summary?.let { { it(valueKey, valueMap[valueKey]) } },
        item = { key, currentKey, onClick ->
          item(key, valueMap.getValue(key), currentKey, onClick)
        },
      )
    }
  }
}
