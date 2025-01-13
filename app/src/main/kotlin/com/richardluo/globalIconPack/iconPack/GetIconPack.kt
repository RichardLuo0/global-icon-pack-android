package com.richardluo.globalIconPack.iconPack

import android.app.AndroidAppHelper
import com.richardluo.globalIconPack.MODE_PROVIDER
import com.richardluo.globalIconPack.PrefDef
import com.richardluo.globalIconPack.PrefKey
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
          pref.getString(PrefKey.ICON_PACK, PrefDef.ICON_PACK)?.takeIf { it.isNotEmpty() }
            ?: throw Exception("No icon pack set")
        val res = pm.getResourcesForApplication(pack)
        ip =
          when (pref.getString(PrefKey.MODE, PrefDef.MODE)) {
            MODE_PROVIDER -> RemoteIconPack(pref, pack, res)
            else -> LocalIconPack(pref, pack, res)
          }
      }
      .exceptionOrNull()
      ?.let { log(it) }
  }
}
