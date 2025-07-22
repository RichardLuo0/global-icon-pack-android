@file:Suppress("DEPRECATION")

package com.richardluo.globalIconPack.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.richardluo.globalIconPack.BuildConfig
import com.richardluo.globalIconPack.ui.MyApplication
import de.robv.android.xposed.XSharedPreferences
import java.io.File

object WorldPreference {
  private lateinit var pref: SharedPreferences
  private const val NAME = BuildConfig.APPLICATION_ID + "_preferences"

  fun get(): SharedPreferences {
    if (!::pref.isInitialized) pref = getPref(NAME)
    return pref
  }

  fun getFile() = getPrefFile(NAME)
}

object AppPreference {
  private lateinit var pref: SharedPreferences
  private const val NAME = "app"

  fun get(): SharedPreferences {
    if (!::pref.isInitialized) pref = getPref(NAME)
    return pref
  }

  fun getFile() = getPrefFile(NAME)
}

@SuppressLint("WorldReadableFiles")
@Suppress("KotlinConstantConditions")
private fun getPref(name: String) =
  if (isInMod)
    XSharedPreferences(BuildConfig.APPLICATION_ID, name).also {
      if (!it.file.canRead())
        log("Pref can not be read. Plz open global icon pack at least once: " + it.file.path)
    }
  else
    MyApplication.context.getSharedPreferences(
      name,
      if (BuildConfig.BUILD_TYPE == "debugApp") Context.MODE_PRIVATE
      else Context.MODE_WORLD_READABLE,
    )

private val getSharedPreferencesPath by lazy {
  Context::class.java.method("getSharedPreferencesPath", String::class.java)
}

private fun getPrefFile(name: String) =
  if (isInMod) XSharedPreferences(BuildConfig.APPLICATION_ID, name).file
  else getSharedPreferencesPath?.call<File>(MyApplication.context, name)
