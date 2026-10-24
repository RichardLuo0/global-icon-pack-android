package com.richardluo.globalIconPack.iconPack.model

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.drawable.Drawable
import android.os.Build
import com.richardluo.globalIconPack.reflect.Resources
import com.richardluo.globalIconPack.utils.Logger.logD
import io.github.libxposed.api.XposedInterface

open class ResourceOwner(val context: Application, protected val pack: String) {
  protected var res = context.packageManager.getResourcesForApplication(pack)
  private val idCache = mutableMapOf<String, Int>()

  private var assetsSeq: Int? = Resources.getAssetsSeq(context.resources)

  private fun updateResources() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      // Dynamic theme color change
      val newAssetsSeq = Resources.getAssetsSeq(context.resources)
      if (assetsSeq == newAssetsSeq) return
      synchronized(this) {
        if (assetsSeq == newAssetsSeq) return
        assetsSeq = newAssetsSeq

        res = context.packageManager.getResourcesForApplication(pack)
        logD("resources updated")
      }
    }
  }

  @SuppressLint("DiscouragedApi")
  fun getIdByName(name: String): Int =
    idCache.getOrPut("$pack/$name") { res.getIdentifier(name, "drawable", pack) }

  context(xposed: XposedInterface)
  fun getIconById(id: Int, iconDpi: Int): Drawable? =
    if (id == 0) null
    else {
      updateResources()
      Resources.getDrawableForDensity(res, id, iconDpi, null)
    }

  context(xposed: XposedInterface)
  fun getIconByName(name: String, iconDpi: Int): Drawable? = getIconById(getIdByName(name), iconDpi)
}
