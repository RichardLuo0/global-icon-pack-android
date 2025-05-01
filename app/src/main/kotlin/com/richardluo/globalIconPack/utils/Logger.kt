package com.richardluo.globalIconPack.utils

import android.app.AndroidAppHelper
import android.util.Log
import com.richardluo.globalIconPack.BuildConfig
import de.robv.android.xposed.XposedBridge

private const val TAG = "[Global Icon Pack]"

val currentPackageName: String by lazy { AndroidAppHelper.currentPackageName() }

fun log(text: String) {
  if (isInMod) XposedBridge.log("$TAG $currentPackageName: $text")
  else Log.i("LSPosed-Bridge", "$TAG $text")
}

fun log(t: Throwable) {
  if (isInMod) XposedBridge.log("$TAG $currentPackageName: ${t.stackTraceToString()}")
  else Log.e("LSPosed-Bridge", "$TAG ${t.stackTraceToString()}")
}

fun logD(text: String) {
  if (BuildConfig.DEBUG) log(text)
}
