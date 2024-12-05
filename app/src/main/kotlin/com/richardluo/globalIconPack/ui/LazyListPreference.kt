package com.richardluo.globalIconPack.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.rememberPreferenceState

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
  noinline summary: @Composable ((T) -> Unit)? = null,
  crossinline load: () -> Map<T, U>,
  noinline item:
    @Composable
    (value: T, currentValue: T, valueMap: Map<T, U>, onClick: () -> Unit) -> Unit,
) {
  item(key = key, contentType = "ListPreference") {
    var valueMap by remember { mutableStateOf<Map<T, U>>(mapOf()) }
    LaunchedEffect(Unit) { valueMap = load() }

    val state = rememberState()
    val value by state
    ListPreference(
      state = state,
      values = valueMap.keys.toList(),
      title = { title(value) },
      modifier = modifier,
      enabled = enabled(value),
      icon = icon?.let { { it(value) } },
      summary = summary?.let { { it(value) } },
      item = { valueC, currentValue, onClick -> item(valueC, currentValue, valueMap, onClick) },
    )
  }
}
