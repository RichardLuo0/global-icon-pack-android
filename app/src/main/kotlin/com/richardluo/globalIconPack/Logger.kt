package com.richardluo.globalIconPack

import de.robv.android.xposed.XposedBridge

const val TAG = "[Global Icon Pack] "

fun log(text: String) = XposedBridge.log(TAG + text)

fun log(t: Throwable) = XposedBridge.log(TAG + t.stackTraceToString())
