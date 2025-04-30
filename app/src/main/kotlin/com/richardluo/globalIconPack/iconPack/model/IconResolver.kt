package com.richardluo.globalIconPack.iconPack.model

import android.database.Cursor
import com.richardluo.globalIconPack.iconPack.IconPackDB
import com.richardluo.globalIconPack.iconPack.getBlob
import com.richardluo.globalIconPack.iconPack.getInt
import com.richardluo.globalIconPack.iconPack.getString

class IconResolver(val entry: IconEntry, private val id: Int, private val pack: String) :
  IconEntry by entry {
  fun getIcon(getRO: (String) -> ResourceOwner, iconDpi: Int) =
    entry.getIcon {
      getRO(pack).let { ro ->
        if (id == 0) ro.getIconByName(it, iconDpi) else ro.getIconById(id, iconDpi)
      }
    }

  companion object {
    fun from(c: Cursor): IconResolver {
      val entry = IconEntry.from(c.getBlob(IconPackDB.GetIconColumn.Entry))
      val pack = c.getString(IconPackDB.GetIconColumn.Pack)
      val id = c.getInt(IconPackDB.GetIconColumn.Id)
      return IconResolver(entry, id, pack)
    }
  }
}
