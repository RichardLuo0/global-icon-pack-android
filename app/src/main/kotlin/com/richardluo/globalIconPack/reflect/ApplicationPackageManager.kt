package com.richardluo.globalIconPack.reflect

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.richardluo.globalIconPack.utils.call
import com.richardluo.globalIconPack.utils.method

object ApplicationPackageManager {
  private val getInstalledApplicationsAsUserM by lazy {
    PackageManager::class
      .java
      .method(
        "getInstalledApplicationsAsUser",
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
      )
  }

  fun getInstalledApplicationsAsUser(
    thisObj: PackageManager,
    flags: Int,
    userId: Int,
  ): List<ApplicationInfo>? = getInstalledApplicationsAsUserM?.call(thisObj, flags, userId)

  private val getPackageInfoAsUserM by lazy {
    PackageManager::class
      .java
      .method(
        "getPackageInfoAsUser",
        String::class.java,
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
      )
  }

  fun getPackageInfoAsUser(thisObj: PackageManager, packageName: String, flags: Int, userId: Int) =
    getPackageInfoAsUserM?.call<PackageInfo>(thisObj, packageName, flags, userId)
}
