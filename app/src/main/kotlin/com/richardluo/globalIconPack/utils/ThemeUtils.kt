package com.richardluo.globalIconPack.utils

import android.app.Application
import android.content.res.Configuration
import android.os.Build
import com.richardluo.globalIconPack.reflect.Resources

data class ThemeSig(val night: Int, val seq: Int)

fun currentThemeSig(context: Application): ThemeSig? {
  val c = context.resources?.configuration ?: return null
  return ThemeSig(
    c.uiMode and Configuration.UI_MODE_NIGHT_MASK,
    Resources.getAssetsSeq(c) ?: 0,
  )
}

class ThemeColorMonitor(val context: Application) {
  @Volatile private var assetsSeq: Int? = Resources.getAssetsSeq(context.resources)

  fun update(doUpdate: () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val newAssetsSeq = Resources.getAssetsSeq(context.resources)
      if (assetsSeq == newAssetsSeq) return
      synchronized(this) {
        if (assetsSeq == newAssetsSeq) return
        doUpdate()
        assetsSeq = newAssetsSeq
      }
    }
  }
}
