package com.richardluo.globalIconPack

import android.app.Application
import android.util.Log
import de.robv.android.xposed.XposedBridge

private const val TAG = "[Global Icon Pack]"

val currentProcessName: String by lazy { Application.getProcessName() }

fun log(text: String) = XposedBridge.log("$TAG $currentProcessName: $text")

fun log(t: Throwable) = XposedBridge.log("$TAG $currentProcessName: ${t.stackTraceToString()}")

fun logInApp(text: String) = Log.e("LSPosed-Bridge", "$TAG $currentProcessName: $text")
