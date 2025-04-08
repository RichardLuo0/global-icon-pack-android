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
    if (!::pref.isInitialized)
      pref =
        XSharedPreferences(BuildConfig.APPLICATION_ID).also {
          if (!it.file.canRead()) {
            log("Pref can not be read. Plz open global icon pack at least once.")
            log(it.file.path)
          }
        }
    return pref
  }

  @SuppressLint("WorldReadableFiles")
  @Suppress("KotlinConstantConditions")
  fun getPrefInApp(context: Context): SharedPreferences {
    if (!::pref.isInitialized)
      pref =
        context.getSharedPreferences(
          PreferenceManager.getDefaultSharedPreferencesName(context),
          if (BuildConfig.BUILD_TYPE == "debugApp") Context.MODE_PRIVATE
          else Context.MODE_WORLD_READABLE,
        )
    return pref
  }
}
