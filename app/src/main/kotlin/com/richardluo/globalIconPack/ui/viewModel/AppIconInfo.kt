package com.richardluo.globalIconPack.ui.viewModel

import com.richardluo.globalIconPack.utils.IconPackCreator.IconEntryWithPack

data class AppIconInfo(
  val app: String,
  val label: String,
  val isSystem: Boolean,
  val entry: IconEntryWithPack?,
) {

  override fun equals(other: Any?): Boolean {
    return if (other !is AppIconInfo) false
    else entry != null && app == other.app && entry == other.entry
  }

  override fun hashCode() = super.hashCode()
}
