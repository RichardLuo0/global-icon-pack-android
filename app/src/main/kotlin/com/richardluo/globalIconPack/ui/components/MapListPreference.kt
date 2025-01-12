package com.richardluo.globalIconPack.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.rememberPreferenceState

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
