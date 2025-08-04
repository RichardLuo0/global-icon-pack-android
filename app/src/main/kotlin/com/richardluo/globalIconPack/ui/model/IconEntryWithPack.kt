package com.richardluo.globalIconPack.ui.model

import android.os.Parcelable
import com.richardluo.globalIconPack.iconPack.model.IconEntry
import com.richardluo.globalIconPack.iconPack.model.IconEntryParceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith

@Parcelize
class IconEntryWithPack(val entry: @WriteWith<IconEntryParceler> IconEntry, val pack: IconPack) :
  Parcelable {

  override fun equals(other: Any?) =
    other is IconEntryWithPack && other.pack == pack && other.entry == entry

  override fun hashCode(): Int {
    var result = entry.hashCode()
    result = 31 * result + pack.hashCode()
    return result
  }
}
