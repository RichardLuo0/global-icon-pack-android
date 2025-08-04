package com.richardluo.globalIconPack.ui

import android.annotation.SuppressLint
import android.app.Application
import com.topjohnwu.superuser.Shell

class MyApplication : Application() {
  companion object {
    @SuppressLint("StaticFieldLeak")
    lateinit var context: Application
      private set

    init {
      Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER))
    }
  }

  override fun onCreate() {
    super.onCreate()
    context = this
  }
}
