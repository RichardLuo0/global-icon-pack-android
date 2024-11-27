package com.richardluo.globalIconPack.reflect

import android.graphics.drawable.Drawable
import android.os.Bundle
import com.richardluo.globalIconPack.ClockMetadata
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Method
import java.util.function.IntFunction

object ClockDrawableWrapper {
  private lateinit var clazz: Class<*>

  private lateinit var forExtras: Method

  fun initWithPixelLauncher(lpp: LoadPackageParam) {
    clazz =
      XposedHelpers.findClass("com.android.launcher3.icons.ClockDrawableWrapper", lpp.classLoader)
    if (!::clazz.isInitialized) return
    forExtras =
      XposedHelpers.findMethodExact(clazz, "forExtras", Bundle::class.java, IntFunction::class.java)
  }

  fun from(drawable: Drawable, metadata: ClockMetadata): Drawable? {
    if (!::forExtras.isInitialized) return null
    val bundle = Bundle()
    // Dummy res id
    bundle.putInt("com.android.launcher3.LEVEL_PER_TICK_ICON_ROUND", 0x7f000000)
    bundle.putInt("com.android.launcher3.HOUR_LAYER_INDEX", metadata.hourLayerIndex)
    bundle.putInt("com.android.launcher3.MINUTE_LAYER_INDEX", metadata.minuteLayerIndex)
    bundle.putInt("com.android.launcher3.SECOND_LAYER_INDEX", metadata.secondLayerIndex)
    bundle.putInt("com.android.launcher3.DEFAULT_HOUR", metadata.defaultHour)
    bundle.putInt("com.android.launcher3.DEFAULT_MINUTE", metadata.defaultMinute)
    bundle.putInt("com.android.launcher3.DEFAULT_SECOND", metadata.defaultSecond)
    return forExtras.invoke(
      null,
      bundle,
      object : IntFunction<Drawable> {
        override fun apply(dummy: Int): Drawable = drawable
      },
    ) as Drawable?
  }
}
