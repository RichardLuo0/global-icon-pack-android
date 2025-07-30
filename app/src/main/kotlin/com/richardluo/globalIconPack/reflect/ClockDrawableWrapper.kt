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
      (drawable as? AdaptiveIconDrawable
          ?: AdaptiveIconDrawable(Color.WHITE.toDrawable(), drawable))
        .mutate() as? AdaptiveIconDrawable ?: return null

    val wrapper = constructor?.newInstance(drawable)?.asType<AdaptiveIconDrawable>() ?: return null
    val animationInfo = mAnimationInfo?.get(wrapper) ?: return null
    AnimationInfo.setup(animationInfo, drawable.constantState, metadata)

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

    wrapper.foreground.asType<LayerDrawable>()?.let {
      AnimationInfo.applyTime(animationInfo, Calendar.getInstance(), it)
    }

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

    fun setup(animationInfo: Any, cs: Drawable.ConstantState?, metadata: ClockMetadata) {
      baseDrawableState?.set(animationInfo, cs)
      hourLayerIndex?.set(animationInfo, metadata.hourLayerIndex)
      minuteLayerIndex?.set(animationInfo, metadata.minuteLayerIndex)
      secondLayerIndex?.set(animationInfo, metadata.secondLayerIndex)
      defaultHour?.set(animationInfo, metadata.defaultHour)
      defaultMinute?.set(animationInfo, metadata.defaultMinute)
      defaultSecond?.set(animationInfo, metadata.defaultSecond)
    }

    fun copyForIcon(animationInfo: Any, icon: Drawable) =
      copyForIconM?.call<Any>(animationInfo, icon)

    fun applyTime(animationInfo: Any, time: Calendar, foregroundDrawable: LayerDrawable) =
      applyTimeM?.call<Boolean>(animationInfo, time, foregroundDrawable)
  }
}
