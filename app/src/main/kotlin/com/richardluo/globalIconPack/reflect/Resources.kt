package com.richardluo.globalIconPack.reflect

import android.content.res.Resources
import android.content.res.Resources.Theme
import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.utils.getOrNull
import com.richardluo.globalIconPack.utils.log
import com.richardluo.globalIconPack.utils.method
import de.robv.android.xposed.XposedBridge
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

  fun getDrawableForDensity(
    thisObj: Resources,
    resId: Int,
    iconDpi: Int,
    theme: Theme?,
  ): Drawable? {
    return runCatching {
        XposedBridge.invokeOriginalMethod(
          getDrawableForDensityM,
          thisObj,
          arrayOf(resId, iconDpi, theme),
        ) as Drawable
      }
      .getOrNull { log(it) }
  }
}
