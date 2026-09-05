package com.richardluo.globalIconPack.utils

import android.util.Log
import android.util.Log.ERROR
import android.util.Log.INFO
import com.richardluo.globalIconPack.BuildConfig
import com.richardluo.globalIconPack.reflect.ActivityThread
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference

object Logger {
  var xposed: WeakReference<XposedInterface>? = null

  const val TAG = "[Global Icon Pack]"

  val currentPackageName: String by lazy { ActivityThread.currentPackageName() ?: "" }

  fun log(text: String) {
    val xposed = xposed?.get()
    if (xposed != null) xposed.log(INFO, TAG, "$currentPackageName: $text") else Log.i(TAG, text)
  }

  fun logE(text: String) {
    val xposed = xposed?.get()
    if (xposed != null) xposed.log(ERROR, TAG, "$currentPackageName error: $text")
    else Log.e(TAG, text)
  }

  fun logE(t: Throwable) {
    val xposed = xposed?.get()
    if (xposed != null) xposed.log(ERROR, TAG, "$currentPackageName error", t)
    else Log.e(TAG, "", t)
  }

  context(xposed: XposedInterface)
  fun logD(text: String) {
    if (BuildConfig.DEBUG) log(text)
  }
}
