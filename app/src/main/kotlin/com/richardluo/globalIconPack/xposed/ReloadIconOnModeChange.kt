package com.richardluo.globalIconPack.xposed

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import com.richardluo.globalIconPack.reflect.ActivityThread
import com.richardluo.globalIconPack.utils.Logger.logD
import com.richardluo.globalIconPack.utils.after
import com.richardluo.globalIconPack.utils.allMethods
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.before
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.field
import com.richardluo.globalIconPack.utils.hook
import com.richardluo.globalIconPack.utils.method
import com.richardluo.globalIconPack.utils.runSafe
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method
import java.util.WeakHashMap

class ReloadIconOnModeChange : Hook {

  private class LoadIcon(
    val method: Method,
    val thisObj: Any?,
    val args: Array<out Any?>,
    var configuration: Configuration? =
      ActivityThread.currentApplication()?.resources?.configuration,
  ) {
    operator fun invoke(): Any? = method(thisObj, *args)

    fun isNewConfiguration(): Boolean {
      val newConfiguration = args[0].asType<Context>()?.resources?.configuration
      if (configuration == newConfiguration) return false
      configuration = newConfiguration
      return true
    }
  }

  context(xposed: XposedInterface)
  override fun onHookPixelLauncher(param: XposedModuleInterface.PackageReadyParam) {
    val bitmapPackageNameMap = WeakHashMap<Any, LoadIcon>()

    val bitmapInfo = classOf("com.android.launcher3.icons.BitmapInfo", param) ?: return
    val iconF = bitmapInfo.field("icon") ?: return

    runSafe {
      val launcherActivityCachingLogicC =
        classOf("com.android.launcher3.icons.cache.LauncherActivityCachingLogic", param)
          ?: return@runSafe
      launcherActivityCachingLogicC.method("loadIcon")?.hook {
        return@hook after {
          bitmapPackageNameMap[it ?: return@after] =
            LoadIcon(
              executable.asType() ?: return@after,
              thisObject,
              args.toTypedArray(),
            )
        }
      }
    }

    runSafe {
      val appInfoCachingLogicC =
        classOf("com.android.launcher3.icons.cache.AppInfoCachingLogic", param) ?: return@runSafe
      val loadIconM = appInfoCachingLogicC.method("loadIcon") ?: return@runSafe
      loadIconM.hook {
        return@hook after {
          bitmapPackageNameMap[it ?: return@after] =
            LoadIcon(
              executable.asType() ?: return@after,
              thisObject,
              args.toTypedArray(),
            )
        }
      }
    }

    // Nothing OS
    runSafe {
      if (param.packageName != "com.nothing.launcher") return@runSafe
      val iconCache = classOf("com.android.launcher3.icons.IconCache", param)

      val iconCacheSuperC = iconCache?.superclass ?: return@runSafe
      iconCacheSuperC.method("getAppIconInfo", null, ApplicationInfo::class.java)?.hook {
        return@hook after {
          bitmapPackageNameMap[it ?: return@after] =
            LoadIcon(
              executable.asType() ?: return@after,
              thisObject,
              args.toTypedArray(),
            )
        }
      }
    }

    bitmapInfo
      .allMethods("newIcon")
      .maxBy { it.parameterCount }
      .hook {
        return@hook before {
          val loadIcon = bitmapPackageNameMap[thisObject] ?: return@before
          if (!loadIcon.isNewConfiguration()) return@before

          iconF.set(
            thisObject,
            iconF.get(loadIcon()),
          )
          logD("changed com.android.launcher3.icons.BitmapInfo.newIcon")
        }
      }
  }
}
