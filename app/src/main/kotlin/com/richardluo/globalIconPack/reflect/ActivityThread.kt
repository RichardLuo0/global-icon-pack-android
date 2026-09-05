package com.richardluo.globalIconPack.reflect

import android.app.Application
import com.richardluo.globalIconPack.utils.call
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.method

object ActivityThread {
  private val clazz: Class<*>? by lazy {
    classOf("android.app.ActivityThread")
  }

  private val currentApplicationM by lazy {
    clazz?.method("currentApplication")
  }

  fun currentApplication(): Application? = currentApplicationM?.call()

  private val currentPackageNameM by lazy {
    clazz?.method("currentPackageName")
  }

  fun currentPackageName(): String? = currentPackageNameM?.call()
}
