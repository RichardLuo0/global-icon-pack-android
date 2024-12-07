package com.richardluo.globalIconPack.iconPack

import android.app.AndroidAppHelper
import com.richardluo.globalIconPack.MODE_PROVIDER
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
        ip =
          when (pref.getString(PrefKey.MODE, MODE_PROVIDER)) {
            MODE_PROVIDER -> RemoteIconPack(pref) { pm.getResourcesForApplication(it) }
            else -> LocalIconPack(pref, true) { pm.getResourcesForApplication(it) }
          }
      }
      .exceptionOrNull()
      ?.let { log(it) }
  }
}
