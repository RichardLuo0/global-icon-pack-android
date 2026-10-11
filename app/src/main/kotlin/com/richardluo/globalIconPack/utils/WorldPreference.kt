@file:Suppress("DEPRECATION")

package com.richardluo.globalIconPack.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.richardluo.globalIconPack.AppPref
import com.richardluo.globalIconPack.BuildConfig
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.ui.repo.XposedServiceRepo
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.service.XposedService

object WorldPreference {
  private lateinit var pref: SharedPreferences
  private const val NAME = BuildConfig.APPLICATION_ID + "_preferences"

  context(xposed: XposedInterface)
  fun get(): SharedPreferences {
    if (!::pref.isInitialized) pref = getPref(NAME)
    return pref
  }

  fun getInApp(xposed: XposedService? = XposedServiceRepo.service.value): SharedPreferences {
    if (!::pref.isInitialized) pref = getPrefInApp(xposed, NAME)
    return pref
  }
}

object AppPreference {
  private lateinit var pref: SharedPreferences
  private const val NAME = "app"

  context(xposed: XposedInterface)
  fun get(): SharedPreferences {
    if (!::pref.isInitialized) pref = getPref(NAME)
    return pref
  }

  fun getInApp(xposed: XposedService? = XposedServiceRepo.service.value): SharedPreferences {
    if (!::pref.isInitialized) pref = getPrefInApp(xposed, NAME)
    return pref
  }
}

// The database path is stored in the xposed remote preferences, but the framework may never bind
// the xposed service to a process that was started for IconPackProvider. Keep a device protected
// copy of it so the provider can always open the database.
object DBPath {
  private const val NAME = "local"

  private fun local(context: Context) =
    context.createDeviceProtectedStorageContext().getSharedPreferences(NAME, Context.MODE_PRIVATE)

  fun get(context: Context): String {
    val remote = runCatching { AppPreference.getInApp() }.getOrNull()
    if (remote != null) return remote.get(AppPref.PATH).also { save(context, it) }
    return local(context).get(AppPref.PATH)
  }

  fun save(context: Context, path: String) =
    local(context).edit { putString(AppPref.PATH.key, path) }
}

context(xposed: XposedInterface)
private fun getPref(name: String) = xposed.getRemotePreferences(name)

private fun getPrefInApp(xposed: XposedService?, name: String) =
  xposed?.getRemotePreferences(name) ?: throw Exception("Xposed service isn't currently available!")
