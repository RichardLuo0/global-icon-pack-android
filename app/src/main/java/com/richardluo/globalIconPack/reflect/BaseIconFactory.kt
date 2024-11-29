package com.richardluo.globalIconPack.reflect

import android.app.AndroidAppHelper
import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.letAll
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Method

object BaseIconFactory {
  var clazz: Class<*>? = null
    private set

  private var getNormalizer: Method? = null
  private var getScale: Method? = null
  private var obtain: Method? = null

  fun initWithLauncher3(lpp: LoadPackageParam) {
    clazz = clazz ?: ReflectHelper.findClass("com.android.launcher3.icons.BaseIconFactory", lpp)
    getNormalizer =
      getNormalizer ?: clazz?.let { ReflectHelper.findMethodFirstMatch(it, "getNormalizer") }
    getScale =
      getScale
        ?: ReflectHelper.findMethodFirstMatch(
          "com.android.launcher3.icons.IconNormalizer",
          lpp,
          "getScale",
          Drawable::class.java,
        )
    obtain =
      obtain
        ?: ReflectHelper.findMethodFirstMatch(
          "com.android.launcher3.icons.LauncherIcons",
          lpp,
          "obtain",
        )
  }

  fun getScale(drawable: Drawable): Float? {
    return obtain?.invoke(null, AndroidAppHelper.currentApplication())?.let { factory ->
      letAll(getNormalizer, getScale) { getNormalizer, getScale ->
        getScale.invoke(getNormalizer.invoke(factory), drawable, null, null, null) as Float
      }
    }
  }
}
