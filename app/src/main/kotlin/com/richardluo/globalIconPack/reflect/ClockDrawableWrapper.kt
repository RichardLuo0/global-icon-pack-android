package com.richardluo.globalIconPack.reflect

import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import androidx.core.graphics.drawable.toDrawable
import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.iconPack.model.ClockMetadata
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.call
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.constructor
import com.richardluo.globalIconPack.utils.field
import com.richardluo.globalIconPack.utils.getAs
import com.richardluo.globalIconPack.utils.method
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Calendar

object ClockDrawableWrapper {
  private var impl: IClockDrawableWrapper? = null

  fun initWithPixelLauncher(lpp: LoadPackageParam) {
    impl = ClockDrawableWrapperPre16QPR2.from(lpp) ?: ClockDrawableWrapper16QPR2.from(lpp)
  }

  fun from(drawable: Drawable, metadata: ClockMetadata): Drawable? = impl?.from(drawable, metadata)
}

private interface IClockDrawableWrapper {

  fun from(drawable: Drawable, metadata: ClockMetadata): Drawable?
}

private const val INVALID_VALUE: Int = -1

private class ClockDrawableWrapperPre16QPR2
private constructor(
  private val constructor: Constructor<*>,
  private val mAnimationInfo: Field,
  private val mThemeInfo: Field,
  private val animationInfoReflect: AnimationInfo,
) : IClockDrawableWrapper {

  companion object {
    fun from(lpp: LoadPackageParam): IClockDrawableWrapper? =
      classOf("com.android.launcher3.icons.ClockDrawableWrapper", lpp)?.let {
        val constructor = it.constructor(AdaptiveIconDrawable::class.java) ?: return null
        val mAnimationInfo = it.field("mAnimationInfo") ?: return null
        val mThemeInfo = it.field("mThemeInfo") ?: return null
        val animationInfoClass =
          classOf("com.android.launcher3.icons.ClockDrawableWrapper\$AnimationInfo", lpp)
            ?: return null
        ClockDrawableWrapperPre16QPR2(
          constructor,
          mAnimationInfo,
          mThemeInfo,
          AnimationInfo(animationInfoClass),
        )
      }
  }

  override fun from(drawable: Drawable, metadata: ClockMetadata): Drawable? {
    // https://cs.android.com/android/_/android/platform/frameworks/libs/systemui/+/main:iconloaderlib/src/com/android/launcher3/icons/ClockDrawableWrapper.java;l=123
    val drawable =
      drawable.mutate().let {
        it as? AdaptiveIconDrawable ?: AdaptiveIconDrawable(Color.WHITE.toDrawable(), it)
      }

    val wrapper = constructor.newInstance(drawable).asType<AdaptiveIconDrawable>() ?: return null
    val animationInfo = mAnimationInfo.get(wrapper) ?: return null
    val foreground = wrapper.foreground.asType<LayerDrawable>() ?: return null

    animationInfoReflect.setup(animationInfo, drawable.constantState, metadata, foreground)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val monochrome = drawable.monochrome
      if (monochrome != null)
        mThemeInfo.set(
          wrapper,
          animationInfoReflect.copyForIcon(
            animationInfo,
            AdaptiveIconDrawable(Color.WHITE.toDrawable(), monochrome.mutate()),
          ),
        )
    }

    animationInfoReflect.applyTime(animationInfo, Calendar.getInstance(), foreground)

    return wrapper.asType()
  }

  private class AnimationInfo(clazz: Class<*>) {
    private val baseDrawableState: Field? = clazz.field("baseDrawableState")
    private var hourLayerIndex: Field? = clazz.field("hourLayerIndex")
    private var minuteLayerIndex: Field? = clazz.field("minuteLayerIndex")
    private var secondLayerIndex: Field? = clazz.field("secondLayerIndex")
    private var defaultHour: Field? = clazz.field("defaultHour")
    private var defaultMinute: Field? = clazz.field("defaultMinute")
    private var defaultSecond: Field? = clazz.field("defaultSecond")

    private var copyForIconM: Method? = clazz.method("copyForIcon")
    private var applyTimeM: Method? = clazz.method("applyTime")

    fun setup(
      info: Any,
      cs: Drawable.ConstantState?,
      metadata: ClockMetadata,
      foreground: LayerDrawable,
    ) {
      baseDrawableState?.set(info, cs)
      hourLayerIndex?.set(info, metadata.hourLayerIndex)
      minuteLayerIndex?.set(info, metadata.minuteLayerIndex)
      secondLayerIndex?.set(info, metadata.secondLayerIndex)
      defaultHour?.set(info, metadata.defaultHour)
      defaultMinute?.set(info, metadata.defaultMinute)
      defaultSecond?.set(info, metadata.defaultSecond)

      val layerCount = foreground.numberOfLayers
      if (hourLayerIndex?.get(info) !in 0 until layerCount) hourLayerIndex?.set(info, INVALID_VALUE)
      if (minuteLayerIndex?.get(info) !in 0 until layerCount)
        minuteLayerIndex?.set(info, INVALID_VALUE)
      if (secondLayerIndex?.get(info) !in 0 until layerCount)
        secondLayerIndex?.set(info, INVALID_VALUE)
      else if (WorldPreference.get().get(Pref.DISABLE_CLOCK_SECONDS)) {
        foreground.setDrawable(secondLayerIndex?.getAs(info)!!, null)
        secondLayerIndex?.set(info, INVALID_VALUE)
      }
    }

    fun copyForIcon(animationInfo: Any, icon: Drawable) =
      copyForIconM?.call<Any>(animationInfo, icon)

    fun applyTime(animationInfo: Any, time: Calendar, foreground: LayerDrawable) =
      applyTimeM?.call<Boolean>(animationInfo, time, foreground)
  }
}

private class ClockDrawableWrapper16QPR2
private constructor(
  private val constructor: Constructor<*>,
  private val clockAnimationInfoReflect: ClockAnimationInfo,
) : IClockDrawableWrapper {

  companion object {
    fun from(lpp: LoadPackageParam): IClockDrawableWrapper? =
      classOf("com.android.launcher3.icons.ClockDrawableWrapper", lpp)?.let {
        val constructor = it.constructor(AdaptiveIconDrawable::class.java) ?: return null
        val clockAnimationInfoClass =
          classOf("com.android.launcher3.icons.ClockDrawableWrapper\$ClockAnimationInfo", lpp)
            ?: return null
        ClockDrawableWrapper16QPR2(
          constructor,
          ClockAnimationInfo(
            clockAnimationInfoClass.constructor() ?: return null,
            clockAnimationInfoClass.method("applyTime") ?: return null,
          ),
        )
      }
  }

  override fun from(drawable: Drawable, metadata: ClockMetadata): Drawable? {
    val drawable =
      drawable.mutate().let {
        it as? AdaptiveIconDrawable ?: AdaptiveIconDrawable(Color.WHITE.toDrawable(), it)
      }
    val foreground = drawable.foreground.asType<LayerDrawable>() ?: return null

    val clockAnimationInfo =
      clockAnimationInfoReflect.newInstance(drawable.constantState, metadata, foreground).also {
        clockAnimationInfoReflect.applyTime(it, Calendar.getInstance(), foreground)
      }

    return constructor.newInstance(drawable, clockAnimationInfo).asType()
  }

  private class ClockAnimationInfo(
    private val constructor: Constructor<*>,
    private val applyTimeM: Method,
  ) {
    fun newInstance(
      cs: Drawable.ConstantState?,
      metadata: ClockMetadata,
      foreground: LayerDrawable,
    ): Any {
      val layerCount = foreground.numberOfLayers
      val secondLayerIndex =
        metadata.secondLayerIndex.takeIf { it in 0 until layerCount } ?: INVALID_VALUE
      val disableSeconds = WorldPreference.get().get(Pref.DISABLE_CLOCK_SECONDS)

      if (disableSeconds && secondLayerIndex != INVALID_VALUE)
        foreground.setDrawable(secondLayerIndex, null)

      return constructor.newInstance(
        metadata.hourLayerIndex.takeIf { it in 0 until layerCount } ?: INVALID_VALUE,
        metadata.minuteLayerIndex.takeIf { it in 0 until layerCount } ?: INVALID_VALUE,
        if (disableSeconds) INVALID_VALUE else secondLayerIndex,
        metadata.defaultHour,
        metadata.defaultMinute,
        metadata.defaultSecond,
        cs,
        0,
        null,
      )
    }

    fun applyTime(animationInfo: Any, time: Calendar, foreground: LayerDrawable) =
      applyTimeM.call<Boolean>(animationInfo, time, foreground)
  }
}
