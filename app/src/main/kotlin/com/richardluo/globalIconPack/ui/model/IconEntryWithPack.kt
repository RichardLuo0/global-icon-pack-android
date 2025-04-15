package com.richardluo.globalIconPack.ui.model

import com.richardluo.globalIconPack.iconPack.database.IconEntry

class IconEntryWithPack(val entry: IconEntry, val pack: IconPack) {

  override fun equals(other: Any?) =
    other is IconEntryWithPack && other.pack == pack && other.entry == entry

  override fun hashCode(): Int {
    var result = entry.hashCode()
    result = 31 * result + pack.hashCode()
    return result
  }
}
