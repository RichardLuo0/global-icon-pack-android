package com.richardluo.globalIconPack.xposed

import android.content.ContentProvider
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.ProviderInfo
import com.richardluo.globalIconPack.iconPack.IconPackProvider
import com.richardluo.globalIconPack.utils.ReflectHelper
import com.richardluo.globalIconPack.utils.getValue
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

// TODO Lack db permission
object RunProviderInSystem {
  fun onHookSystem(lpp: LoadPackageParam) {
    val contentProviderHelper =
      ReflectHelper.findClass("com.android.server.am.ContentProviderHelper", lpp) ?: return

    var cph: Any? = null
    fun getCph(param: MethodHookParam): Any? {
      if (cph != null) return cph

      val service = param.thisObject.getValue<Any>("mService") ?: return null
      val context = service.getValue<Context>("mContext") ?: return null
      val info =
        ProviderInfo().apply {
          authority = IconPackProvider.AUTHORITIES
          exported = true
          directBootAware = true
          applicationInfo = ApplicationInfo()
        }
      val provider = IconPackProvider().apply { attachInfo(context, info) }

      val contentProviderHolder =
        ReflectHelper.findClass("android.app.ContentProviderHolder") ?: return null
      val providerF = ReflectHelper.findField(contentProviderHolder, "provider")
      val transport =
        provider.getValue<Any>("mTransport", ContentProvider::class.java) ?: return null
      return contentProviderHolder
        .getConstructor(ProviderInfo::class.java)
        .newInstance(info)
        .apply { providerF?.set(this, transport) }
        .also { cph = it }
    }

    ReflectHelper.hookAllMethodsOrLog(
      contentProviderHelper,
      "getContentProviderImpl",
      arrayOf(null, String::class.java),
      object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
          val name = param.args[1] as? String ?: return
          if (name == IconPackProvider.AUTHORITIES) param.result = getCph(param)
        }
      },
    )
  }
}
