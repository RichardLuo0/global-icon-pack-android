package com.richardluo.globalIconPack.reflect

import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.Resources.Theme
import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.utils.Logger.logE
import com.richardluo.globalIconPack.utils.field
import com.richardluo.globalIconPack.utils.getOrNull
import com.richardluo.globalIconPack.utils.method
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Method

object Resources {
  val getDrawableForDensityM: Method? by lazy {
    Resources::class
      .java
      .method(
        "getDrawableForDensity",
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
        Theme::class.java,
      )
  }

  context(xposed: XposedInterface)
  fun getDrawableForDensity(
    thisObj: Resources,
    resId: Int,
    iconDpi: Int,
    theme: Theme?,
  ): Drawable? {
    return runCatching {
      xposed
        .getInvoker(getDrawableForDensityM ?: return null)
        .setType(XposedInterface.Invoker.Type.ORIGIN)
        .invoke(
          thisObj,
          resId,
          iconDpi,
          theme,
        ) as? Drawable
    }
      .getOrNull { logE(it) }
  }

  private val assetsSeq: Field? by lazy {
    Configuration::class.java.field("assetsSeq")
  }

  fun getAssetsSeq(thisObj: Resources) = getAssetsSeq(thisObj.configuration)

  fun getAssetsSeq(config: Configuration) = assetsSeq?.get(config) as Int?
}
