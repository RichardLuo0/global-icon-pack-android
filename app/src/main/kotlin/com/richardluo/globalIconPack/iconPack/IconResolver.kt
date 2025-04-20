package com.richardluo.globalIconPack.iconPack

import android.database.Cursor
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.iconPack.database.IconPackDB.GetIconColumn
import com.richardluo.globalIconPack.iconPack.database.getBlob
import com.richardluo.globalIconPack.iconPack.database.getInt
import com.richardluo.globalIconPack.iconPack.database.getString

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
      val entry = IconEntry.from(c.getBlob(GetIconColumn.Entry))
      val pack = c.getString(GetIconColumn.Pack)
      val id = c.getInt(GetIconColumn.Id)
      return IconResolver(entry, id, pack)
    }
  }
}
