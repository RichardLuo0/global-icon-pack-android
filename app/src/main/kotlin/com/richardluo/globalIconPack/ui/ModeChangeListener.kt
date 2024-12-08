package com.richardluo.globalIconPack.ui

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.richardluo.globalIconPack.MODE_PROVIDER
import com.richardluo.globalIconPack.PrefKey
import com.richardluo.globalIconPack.iconPack.BootReceiver
import com.richardluo.globalIconPack.iconPack.KeepAliveService
import com.richardluo.globalIconPack.iconPack.database.IconPackDB
import com.richardluo.globalIconPack.utils.registerAndCallOnSharedPreferenceChangeListener
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ModeChangeListener(private val activity: Activity) :
  SharedPreferences.OnSharedPreferenceChangeListener {
  private val iconPackChangeListener by lazy { IconPackDB(activity) }

  override fun onSharedPreferenceChanged(pref: SharedPreferences, key: String?) {
    if (key != PrefKey.MODE) return
    when (pref.getString(PrefKey.MODE, MODE_PROVIDER)) {
      MODE_PROVIDER -> {
        // Init db
        KeepAliveService.startForeground(activity)
        runBlocking {
          launch {
            pref.registerAndCallOnSharedPreferenceChangeListener(
              iconPackChangeListener,
              PrefKey.ICON_PACK,
            )
          }
        }
        // Ask for notification permission used for foreground service
        if (
          activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        )
          activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 200)
        // Enable boot receiver
        activity.packageManager.setComponentEnabledSetting(
          ComponentName(activity, BootReceiver::class.java),
          PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
          PackageManager.DONT_KILL_APP,
        )
      }
      else -> {
        KeepAliveService.stopForeground(activity)
        pref.unregisterOnSharedPreferenceChangeListener(iconPackChangeListener)
        activity.packageManager.setComponentEnabledSetting(
          ComponentName(activity, BootReceiver::class.java),
          PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
          PackageManager.DONT_KILL_APP,
        )
      }
    }
  }
}
