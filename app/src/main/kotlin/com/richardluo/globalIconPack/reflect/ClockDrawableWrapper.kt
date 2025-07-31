package com.richardluo.globalIconPack.reflect

import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import androidx.core.graphics.drawable.toDrawable
import com.richardluo.globalIconPack.iconPack.model.ClockMetadata
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.call
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.constructor
import com.richardluo.globalIconPack.utils.field
import com.richardluo.globalIconPack.utils.method
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Calendar

object ClockDrawableWrapper {
  private const val INVALID_VALUE: Int = -1

  private var constructor: Constructor<*>? = null
  private var mAnimationInfo: Field? = null
  private var mThemeInfo: Field? = null

  fun initWithPixelLauncher(lpp: LoadPackageParam) {
    classOf("com.android.launcher3.icons.ClockDrawableWrapper", lpp)?.also {
      constructor = it.constructor(AdaptiveIconDrawable::class.java)
      mAnimationInfo = it.field("mAnimationInfo")
      mThemeInfo = it.field("mThemeInfo")
    }
    AnimationInfo.initWithPixelLauncher(lpp)
  }

  fun from(drawable: Drawable, metadata: ClockMetadata): Drawable? {
    // https://cs.android.com/android/_/android/platform/frameworks/libs/systemui/+/main:iconloaderlib/src/com/android/launcher3/icons/ClockDrawableWrapper.java;l=123
    val drawable =
      drawable.mutate().let {
        it as? AdaptiveIconDrawable ?: AdaptiveIconDrawable(Color.WHITE.toDrawable(), it)
      }

    val wrapper = constructor?.newInstance(drawable)?.asType<AdaptiveIconDrawable>() ?: return null
    val animationInfo = mAnimationInfo?.get(wrapper) ?: return null
    val foreground = wrapper.foreground.asType<LayerDrawable>() ?: return null

    AnimationInfo.setup(animationInfo, drawable.constantState, metadata, foreground.numberOfLayers)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val monochrome = drawable.monochrome
      if (monochrome != null)
        mThemeInfo?.set(
          wrapper,
          AnimationInfo.copyForIcon(
            animationInfo,
            AdaptiveIconDrawable(Color.WHITE.toDrawable(), monochrome.mutate()),
          ),
        )
    }

    AnimationInfo.applyTime(animationInfo, Calendar.getInstance(), foreground)

    return wrapper.asType()
  }

  private object AnimationInfo {
    private var baseDrawableState: Field? = null
    private var hourLayerIndex: Field? = null
    private var minuteLayerIndex: Field? = null
    private var secondLayerIndex: Field? = null
    private var defaultHour: Field? = null
    private var defaultMinute: Field? = null
    private var defaultSecond: Field? = null

    private var copyForIconM: Method? = null
    private var applyTimeM: Method? = null

    fun initWithPixelLauncher(lpp: LoadPackageParam) {
      classOf("com.android.launcher3.icons.ClockDrawableWrapper\$AnimationInfo", lpp)?.also {
        baseDrawableState = it.field("baseDrawableState")
        hourLayerIndex = it.field("hourLayerIndex")
        minuteLayerIndex = it.field("minuteLayerIndex")
        secondLayerIndex = it.field("secondLayerIndex")
        defaultHour = it.field("defaultHour")
        defaultMinute = it.field("defaultMinute")
        defaultSecond = it.field("defaultSecond")

        copyForIconM = it.method("copyForIcon")
        applyTimeM = it.method("applyTime")
      }
    }

    fun setup(info: Any, cs: Drawable.ConstantState?, metadata: ClockMetadata, layerCount: Int) {
      baseDrawableState?.set(info, cs)
      hourLayerIndex?.set(info, metadata.hourLayerIndex)
      minuteLayerIndex?.set(info, metadata.minuteLayerIndex)
      secondLayerIndex?.set(info, metadata.secondLayerIndex)
      defaultHour?.set(info, metadata.defaultHour)
      defaultMinute?.set(info, metadata.defaultMinute)
      defaultSecond?.set(info, metadata.defaultSecond)

      if (hourLayerIndex?.get(info) !in 0 until layerCount) hourLayerIndex?.set(info, INVALID_VALUE)
      if (minuteLayerIndex?.get(info) !in 0 until layerCount)
        minuteLayerIndex?.set(info, INVALID_VALUE)
      if (secondLayerIndex?.get(info) !in 0 until layerCount)
        secondLayerIndex?.set(info, INVALID_VALUE)
    }

    fun copyForIcon(animationInfo: Any, icon: Drawable) =
      copyForIconM?.call<Any>(animationInfo, icon)

    fun applyTime(animationInfo: Any, time: Calendar, foreground: LayerDrawable) =
      applyTimeM?.call<Boolean>(animationInfo, time, foreground)
  }
}
