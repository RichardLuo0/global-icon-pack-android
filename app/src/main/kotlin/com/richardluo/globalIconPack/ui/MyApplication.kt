package com.richardluo.globalIconPack.ui

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import com.richardluo.globalIconPack.ui.repo.XposedServiceRepo
import com.topjohnwu.superuser.Shell
import org.lsposed.hiddenapibypass.HiddenApiBypass

class MyApplication : Application() {
  companion object {
    @SuppressLint("StaticFieldLeak")
    lateinit var context: Application
      private set

    init {
      Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER))
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) HiddenApiBypass.addHiddenApiExemptions("")
    }
  }

  override fun onCreate() {
    super.onCreate()
    context = this
    XposedServiceRepo.register()
  }
}
