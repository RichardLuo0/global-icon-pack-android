package com.richardluo.globalIconPack.reflect

import android.content.res.Resources
import android.content.res.Resources.Theme
import android.graphics.drawable.Drawable
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method

object Resources {
  val getDrawableForDensity: Method by lazy {
    XposedHelpers.findMethodExact(
      Resources::class.java,
      "getDrawableForDensity",
      Int::class.javaPrimitiveType,
      Int::class.javaPrimitiveType,
      Theme::class.java,
    )
  }

  fun getDrawable(thisObj: Resources, resId: Int, theme: Theme?): Drawable? {
    return getDrawableForDensity(thisObj, resId, 0, theme)
  }

  fun getDrawableForDensity(
    thisObj: Resources,
    resId: Int,
    iconDpi: Int,
    theme: Theme?,
  ): Drawable? {
    try {
      return XposedBridge.invokeOriginalMethod(
        getDrawableForDensity,
        thisObj,
        arrayOf(resId, iconDpi, theme),
      ) as Drawable
    } catch (e: Exception) {
      XposedBridge.log(e)
      return null
    }
  }
}
