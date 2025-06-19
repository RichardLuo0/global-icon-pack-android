package com.richardluo.globalIconPack.reflect

import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.core.graphics.drawable.toDrawable
import com.richardluo.globalIconPack.iconPack.model.ClockMetadata
import com.richardluo.globalIconPack.utils.call
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.method
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Method
import java.util.function.IntFunction

object ClockDrawableWrapper {
  private var forExtras: Method? = null

  fun initWithPixelLauncher(lpp: LoadPackageParam) {
    forExtras =
      classOf("com.android.launcher3.icons.ClockDrawableWrapper", lpp)
        // The compiled code uses a intermediate class, I assume it is always compatible with
        // IntFunction
        ?.method("forExtras", Bundle::class.java)
  }

  fun from(drawable: Drawable, metadata: ClockMetadata): Drawable? {
    return forExtras?.let {
      val bundle = Bundle()
      // Dummy res id
      bundle.putInt("com.android.launcher3.LEVEL_PER_TICK_ICON_ROUND", 0x7f000000)
      bundle.putInt("com.android.launcher3.HOUR_LAYER_INDEX", metadata.hourLayerIndex)
      bundle.putInt("com.android.launcher3.MINUTE_LAYER_INDEX", metadata.minuteLayerIndex)
      bundle.putInt("com.android.launcher3.SECOND_LAYER_INDEX", metadata.secondLayerIndex)
      bundle.putInt("com.android.launcher3.DEFAULT_HOUR", metadata.defaultHour)
      bundle.putInt("com.android.launcher3.DEFAULT_MINUTE", metadata.defaultMinute)
      bundle.putInt("com.android.launcher3.DEFAULT_SECOND", metadata.defaultSecond)
      it.call(
        null,
        bundle,
        object : IntFunction<Drawable> {
          override fun apply(dummy: Int): Drawable =
            drawable as? AdaptiveIconDrawable
              ?: AdaptiveIconDrawable(Color.WHITE.toDrawable(), drawable)
        },
      )
    }
  }
}
