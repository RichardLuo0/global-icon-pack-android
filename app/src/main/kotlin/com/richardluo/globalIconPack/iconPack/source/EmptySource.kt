package com.richardluo.globalIconPack.iconPack.source

import android.content.ComponentName
import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.iconPack.model.IconEntry

class EmptySource : Source {

  override fun getId(cn: ComponentName): Int? = null

  override fun getId(cnList: List<ComponentName>): List<Int?> = emptyList()

  override fun getIconEntry(id: Int): IconEntry? = null

  override fun getIconNotAdaptive(
    entry: IconEntry,
    iconDpi: Int,
  ): Drawable? = null

  override fun getIcon(
    name: String,
    iconDpi: Int,
  ): Drawable? = null

  override fun genIconFrom(baseIcon: Drawable): Drawable = baseIcon

  override fun maskIconFrom(baseIcon: Drawable): Drawable = baseIcon
}
