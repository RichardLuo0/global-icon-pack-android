package com.richardluo.globalIconPack.ui.model

import com.richardluo.globalIconPack.iconPack.model.IconEntry

interface VariantIcon

data class VariantPackIcon(val pack: IconPack, val entry: IconEntry) : VariantIcon

class OriginalIcon : VariantIcon {
  override fun equals(other: Any?): Boolean = other is OriginalIcon

  override fun hashCode(): Int {
    return javaClass.hashCode()
  }
}
