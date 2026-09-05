package com.richardluo.globalIconPack.iconPack.model

import android.database.Cursor
import com.richardluo.globalIconPack.iconPack.IconPackDB
import com.richardluo.globalIconPack.iconPack.getBlob
import com.richardluo.globalIconPack.iconPack.getInt
import com.richardluo.globalIconPack.iconPack.getString
import io.github.libxposed.api.XposedInterface

class IconResolver(val entry: IconEntry, private val id: Int, private val pack: String) :
  IconEntry by entry {
  context(xposed: XposedInterface)
  fun getIcon(getRO: (String) -> ResourceOwner, iconDpi: Int) = entry.getIcon {
    getRO(pack).let { ro ->
      if (id == 0) ro.getIconByName(it, iconDpi) else ro.getIconById(id, iconDpi)
    }
  }

  companion object {
    fun from(c: Cursor): IconResolver {
      val entry = IconEntry.from(c.getBlob(IconPackDB.GetIconCol.Entry))
      val pack = c.getString(IconPackDB.GetIconCol.Pack)
      val id = c.getInt(IconPackDB.GetIconCol.Id)
      return IconResolver(entry, id, pack)
    }
  }
}
