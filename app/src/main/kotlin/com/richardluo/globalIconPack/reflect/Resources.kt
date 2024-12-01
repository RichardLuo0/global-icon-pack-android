package com.richardluo.globalIconPack.reflect

import android.content.res.Resources
import android.content.res.Resources.Theme
import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.log
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Method

object Resources {
  val getDrawableForDensity: Method? by lazy {
    ReflectHelper.findMethodFirstMatch(
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
    return runCatching {
        XposedBridge.invokeOriginalMethod(
          getDrawableForDensity,
          thisObj,
          arrayOf(resId, iconDpi, theme),
        ) as Drawable
      }
      .getOrElse {
        log(it)
        null
      }
  }
}
