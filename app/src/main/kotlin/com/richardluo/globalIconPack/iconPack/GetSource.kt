package com.richardluo.globalIconPack.iconPack

import com.richardluo.globalIconPack.MODE_PROVIDER
import com.richardluo.globalIconPack.MODE_SHARE
import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.iconPack.model.IconPackConfig
import com.richardluo.globalIconPack.iconPack.source.EmptySource
import com.richardluo.globalIconPack.iconPack.source.LocalSource
import com.richardluo.globalIconPack.iconPack.source.RemoteSource
import com.richardluo.globalIconPack.iconPack.source.ShareSource
import com.richardluo.globalIconPack.iconPack.source.Source
import com.richardluo.globalIconPack.reflect.ActivityThread
import com.richardluo.globalIconPack.utils.Logger.log
import com.richardluo.globalIconPack.utils.Logger.logE
import com.richardluo.globalIconPack.utils.WorldPreference
import io.github.libxposed.api.XposedInterface

@Volatile private var sc: Source? = null

context(xposed: XposedInterface)
fun getSC(): Source? {
  if (sc == null) {
    synchronized(Source::class) {
      if (sc == null) {
        initSC()
      }
    }
  }
  return sc
}

context(xposed: XposedInterface)
private fun initSC() {
  val context = ActivityThread.currentApplication() ?: return
  runCatching {
    val pref = WorldPreference.get()
    val pack = pref.get(Pref.ICON_PACK)
    val config = IconPackConfig(pref)
    sc =
      if (pack.isEmpty()) {
        log("No icon pack is set")
        EmptySource()
      } else
        when (pref.get(Pref.MODE)) {
          MODE_SHARE -> ShareSource(context, xposed, pack, config)
          MODE_PROVIDER -> RemoteSource(context, xposed, pack, config)
          else -> LocalSource(context, xposed, pack, config)
        }
  }
    .onFailure { logE(it) }
}
