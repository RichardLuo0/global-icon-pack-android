package com.richardluo.globalIconPack.xposed

import android.content.ContentProvider
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.ProviderInfo
import com.richardluo.globalIconPack.iconPack.IconPackProvider
import com.richardluo.globalIconPack.utils.allMethods
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.field
import com.richardluo.globalIconPack.utils.getAs
import com.richardluo.globalIconPack.utils.getValue
import com.richardluo.globalIconPack.utils.hook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

// Not used but keep it here anyway
object RunProviderInSystem {
  fun onHookSystem(lpp: LoadPackageParam) {
    val contentProviderHelper =
      classOf("com.android.server.am.ContentProviderHelper", lpp) ?: return

    var cph: Any? = null
    fun getCph(param: MethodHookParam): Any? {
      if (cph != null) return cph

      val service =
        param.thisObject.javaClass.field("mService")?.getAs<Any>(param.thisObject) ?: return null
      val context = service.javaClass.field("mContext")?.getAs<Context>(service) ?: return null
      val info =
        ProviderInfo().apply {
          authority = IconPackProvider.AUTHORITIES
          exported = true
          directBootAware = true
          applicationInfo = ApplicationInfo()
        }
      val provider = IconPackProvider().apply { attachInfo(context, info) }

      val contentProviderHolder = classOf("android.app.ContentProviderHolder") ?: return null
      val providerF = contentProviderHolder.field("provider")
      val transport =
        ContentProvider::class.java.field("mTransport")?.getAs<Any>(provider) ?: return null
      return contentProviderHolder
        .getConstructor(ProviderInfo::class.java)
        .newInstance(info)
        .apply { providerF?.set(this, transport) }
        .also { cph = it }
    }

    contentProviderHelper.allMethods("getContentProviderImpl").hook {
      before {
        val name = it.args[1] as? String ?: return@before
        if (name == IconPackProvider.AUTHORITIES) it.result = getCph(it)
      }
    }
  }
}
