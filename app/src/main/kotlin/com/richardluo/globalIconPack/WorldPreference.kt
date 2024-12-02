@file:Suppress("DEPRECATION")

package com.richardluo.globalIconPack

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import de.robv.android.xposed.XSharedPreferences

object WorldPreference {
  private lateinit var pref: SharedPreferences

  fun getReadablePref(): SharedPreferences {
    if (!::pref.isInitialized)
      pref =
        XSharedPreferences(BuildConfig.APPLICATION_ID).also {
          if (!it.file.canRead())
            log("Pref can not be read. Plz open global icon pack at least once.")
        }
    return pref
  }

  @SuppressLint("WorldReadableFiles")
  fun getWritablePref(context: Context): SharedPreferences {
    if (!::pref.isInitialized)
      pref =
        context.getSharedPreferences(
          PreferenceManager.getDefaultSharedPreferencesName(context),
          Context.MODE_WORLD_READABLE,
        )
    return pref
  }
}
