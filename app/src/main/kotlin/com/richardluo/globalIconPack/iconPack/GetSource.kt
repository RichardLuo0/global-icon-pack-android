package com.richardluo.globalIconPack.iconPack

import android.app.AndroidAppHelper
import com.richardluo.globalIconPack.MODE_PROVIDER
import com.richardluo.globalIconPack.MODE_SHARE
import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.iconPack.model.IconPackConfig
import com.richardluo.globalIconPack.iconPack.source.LocalSource
import com.richardluo.globalIconPack.iconPack.source.RemoteSource
import com.richardluo.globalIconPack.iconPack.source.ShareSource
import com.richardluo.globalIconPack.iconPack.source.Source
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.log

@Volatile private var sc: Source? = null

fun getSC(): Source? {
  if (sc == null) {
    synchronized(LocalSource::class) {
      if (sc == null) {
        initSC()
      }
    }
  }
  return sc
}

private fun initSC() {
  AndroidAppHelper.currentApplication() ?: return
  runCatching {
      val pref = WorldPreference.get()
      val pack =
        pref.get(Pref.ICON_PACK).takeIf { it.isNotEmpty() } ?: throw Exception("No icon pack set")
      val config = IconPackConfig(pref)
      sc =
        when (pref.get(Pref.MODE)) {
          MODE_SHARE -> ShareSource(pack, config)
          MODE_PROVIDER -> RemoteSource(pack, config)
          else -> LocalSource(pack, config)
        }
    }
    .onFailure { log(it) }
}
