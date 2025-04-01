package com.richardluo.globalIconPack.iconPack

import android.app.AndroidAppHelper
import com.richardluo.globalIconPack.MODE_PROVIDER
import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.log

@Volatile private var ip: IconPack? = null

fun getIP(): IconPack? {
  if (ip == null) {
    synchronized(LocalIconPack::class) {
      if (ip == null) {
        initIP()
      }
    }
  }
  return ip
}

private fun initIP() {
  AndroidAppHelper.currentApplication()?.packageManager?.let { pm ->
    runCatching {
        val pref = WorldPreference.getPrefInMod()
        val pack =
          pref.get(Pref.ICON_PACK)?.takeIf { it.isNotEmpty() }
            ?: throw Exception("No icon pack set")

        val res = pm.getResourcesForApplication(pack)
        val config = IconPackConfig(pref)
        ip =
          when (pref.get(Pref.MODE)) {
            MODE_PROVIDER -> RemoteIconPack(pack, res, config)
            else -> LocalIconPack(pack, res, config)
          }
      }
      .exceptionOrNull()
      ?.let { log(it) }
  }
}
