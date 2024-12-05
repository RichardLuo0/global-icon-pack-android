package com.richardluo.globalIconPack

import android.app.AndroidAppHelper
import android.util.Log
import de.robv.android.xposed.XposedBridge

private const val TAG = "[Global Icon Pack]"

val currentPackageName: String by lazy { AndroidAppHelper.currentPackageName() }

fun log(text: String) = XposedBridge.log("$TAG $currentPackageName: $text")

fun log(t: Throwable) = XposedBridge.log("$TAG $currentPackageName: ${t.stackTraceToString()}")

fun logInApp(text: String) = Log.e("LSPosed-Bridge", "$TAG app: $text")
