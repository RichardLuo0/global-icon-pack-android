package com.richardluo.globalIconPack.ui.viewModel

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import com.richardluo.globalIconPack.ui.model.AppCompIcon
import com.richardluo.globalIconPack.ui.viewModel.IAppsFilter.Type
import com.richardluo.globalIconPack.utils.filter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface IAppsFilter {
  enum class Type {
    User,
    System,
  }

  val searchText: TextFieldState
  val filterType: MutableState<Type>

  fun createFilteredIconsFlow(icons: Flow<Array<List<AppCompIcon>>?>) = snapshotFlow {
    filterType.value
  }
    .combine(icons) { type, icons ->
      icons ?: return@combine null
      icons[type.ordinal]
    }
    .filter(snapshotFlow { searchText.text }) { (info), text ->
      info.label.contains(text, ignoreCase = true) ||
        info.componentName.packageName.contains(text, ignoreCase = true)
    }
}

class AppsFilter : IAppsFilter {
  override val searchText = TextFieldState()
  override val filterType = mutableStateOf(Type.User)
}
