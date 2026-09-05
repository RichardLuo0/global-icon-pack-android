package com.richardluo.globalIconPack.xposed

import com.richardluo.globalIconPack.BuildConfig
import com.richardluo.globalIconPack.utils.allConstructors
import com.richardluo.globalIconPack.utils.allMethods
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.call
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.field
import com.richardluo.globalIconPack.utils.getAs
import com.richardluo.globalIconPack.utils.hookCompat
import com.richardluo.globalIconPack.utils.method
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface

object BypassCrossUserPermission {
  var gipUid = -1

  context(xposed: XposedInterface)
  fun onHookSystem(lpp: XposedModuleInterface.PackageReadyParam) {
    val computerEngineC = classOf("com.android.server.pm.ComputerEngine", lpp) ?: return

    computerEngineC.allConstructors().hookCompat {
      after {
        val mPackages =
          thisObject.javaClass.field("mPackages")?.getAs<Map<*, *>>(thisObject) ?: return@after
        val p = mPackages[BuildConfig.APPLICATION_ID] ?: return@after
        gipUid = p.javaClass.method("getUid")?.call<Int>(p) ?: return@after
      }
    }

    computerEngineC.allMethods("enforceCrossUserPermission").hookCompat {
      before {
        val callingUid = args.getOrNull(0).asType<Int>() ?: return@before
        if (gipUid == callingUid) result = null
      }
    }

    computerEngineC.allMethods("getInstalledApplications").hookCompat {
      before {
        val callingUid = args.getOrNull(2).asType<Int>() ?: return@before
        if (gipUid != callingUid) return@before
        val flags = args.getOrNull(0).asType<Long>() ?: return@before
        args[0] = flags or 0x00400000 // Add MATCH_ANY_USER
      }
    }

    computerEngineC.allMethods("getPackageInfoInternal").hookCompat {
      before {
        val filterCallingUid = args.getOrNull(3).asType<Int>() ?: return@before
        if (gipUid != filterCallingUid) return@before
        val flags = args.getOrNull(2).asType<Long>() ?: return@before
        args[2] = flags or 0x00400000 // Add MATCH_ANY_USER
      }
    }
  }
}
