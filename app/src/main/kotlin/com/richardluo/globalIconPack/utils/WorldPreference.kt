@file:Suppress("DEPRECATION")

package com.richardluo.globalIconPack.utils

import android.content.SharedPreferences
import com.richardluo.globalIconPack.BuildConfig
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

context(xposed: XposedInterface)
private fun getPref(name: String) = xposed.getRemotePreferences(name)

private fun getPrefInApp(xposed: XposedService?, name: String) =
  xposed?.getRemotePreferences(name) ?: throw Exception("Xposed service isn't currently available!")
