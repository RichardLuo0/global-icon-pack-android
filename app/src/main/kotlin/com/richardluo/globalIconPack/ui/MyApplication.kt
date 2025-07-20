package com.richardluo.globalIconPack.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import com.topjohnwu.superuser.Shell

class MyApplication : Application() {
  companion object {
    @SuppressLint("StaticFieldLeak")
    lateinit var context: Context
      private set

    init {
      Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER))
    }
  }

  override fun onCreate() {
    super.onCreate()
    context = applicationContext
  }
}
