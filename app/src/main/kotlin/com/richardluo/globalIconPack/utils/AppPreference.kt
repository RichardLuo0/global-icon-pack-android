package com.richardluo.globalIconPack.utils

import android.content.Context
import android.content.SharedPreferences

object AppPreference {
  private lateinit var pref: SharedPreferences

  fun get(context: Context): SharedPreferences {
    if (!::pref.isInitialized)
      pref =
        context
          .createDeviceProtectedStorageContext()
          .getSharedPreferences("app", Context.MODE_PRIVATE)
    return pref
  }
}
