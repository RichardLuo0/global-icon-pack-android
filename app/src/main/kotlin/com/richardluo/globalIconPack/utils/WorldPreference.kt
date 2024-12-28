@file:Suppress("DEPRECATION")

package com.richardluo.globalIconPack.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.richardluo.globalIconPack.BuildConfig
import de.robv.android.xposed.XSharedPreferences

object WorldPreference {
  private lateinit var pref: SharedPreferences

  fun getPrefInMod(): SharedPreferences {
    if (!WorldPreference::pref.isInitialized)
      pref =
        XSharedPreferences(BuildConfig.APPLICATION_ID).also {
          if (!it.file.canRead())
            log("Pref can not be read. Plz open global icon pack at least once.")
        }
    return pref
  }

  @SuppressLint("WorldReadableFiles")
  fun getPrefInApp(context: Context): SharedPreferences {
    if (!WorldPreference::pref.isInitialized)
      pref =
        context.getSharedPreferences(
          PreferenceManager.getDefaultSharedPreferencesName(context),
          if (BuildConfig.DEBUG) Context.MODE_PRIVATE else Context.MODE_WORLD_READABLE,
        )
    return pref
  }
}
