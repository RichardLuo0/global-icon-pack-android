package com.richardluo.globalIconPack.iconPack

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.reflect.Resources.getDrawableForDensity

open class ResourceOwner(protected val pack: String) {
  protected val res =
    AndroidAppHelper.currentApplication().packageManager.getResourcesForApplication(pack)
  private val idCache = mutableMapOf<String, Int>()

  @SuppressLint("DiscouragedApi")
  fun getIdByName(name: String): Int =
    idCache.getOrPut("$pack/$name") { res.getIdentifier(name, "drawable", pack) }

  fun getIconById(id: Int, iconDpi: Int): Drawable? =
    if (id == 0) null else getDrawableForDensity(res, id, iconDpi, null)

  fun getIconByName(name: String, iconDpi: Int): Drawable? = getIconById(getIdByName(name), iconDpi)
}
