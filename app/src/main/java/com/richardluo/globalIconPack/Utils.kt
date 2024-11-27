package com.richardluo.globalIconPack

import androidx.annotation.CheckResult
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge

@Suppress("UNCHECKED_CAST")
fun <R> callOriginalMethod(param: MethodHookParam): R {
  return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args) as R
}

@CheckResult
fun withHighByteSet(id: Int, flag: Int): Int {
  return id and 0x00FFFFFF or flag
}

fun isHighTwoByte(id: Int, flag: Int): Boolean {
  return (id and 0xff000000.toInt()) == flag
}
