package com.richardluo.globalIconPack.xposed

import android.view.View
import com.richardluo.globalIconPack.reflect.BaseIconFactory
import com.richardluo.globalIconPack.utils.allConstructors
import com.richardluo.globalIconPack.utils.allMethods
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.hook
import com.richardluo.globalIconPack.utils.rGet
import com.richardluo.globalIconPack.utils.rSet
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class NoShadow : Hook {
  companion object {
    const val MODE_DEFAULT = 0
    const val MODE_WITH_SHADOW = 2
    const val MODE_HARDWARE = 3
    const val MODE_HARDWARE_WITH_SHADOW = 4
  }

  override fun onHookPixelLauncher(lpp: LoadPackageParam) = removeIconShadow(lpp)

  override fun onHookSystemUI(lpp: LoadPackageParam) {
    // Remove bubble shadow
    classOf("com.android.wm.shell.bubbles.BadgedImageView", lpp).allConstructors().hook {
      after { it.thisObject.asType<View>()?.outlineProvider = null }
    }
    removeIconShadow(lpp)
  }

  override fun onHookSettings(lpp: LoadPackageParam) = removeIconShadow(lpp)

  private fun removeIconShadow(lpp: LoadPackageParam) {
    classOf("android.util.LauncherIcons").allMethods("wrapIconDrawableWithShadow").hook {
      replace { it.args[0] }
    }
    BaseIconFactory.getClazz(lpp).allMethods("drawIconBitmap").hook {
      before {
        val bitmapGenerationMode = it.args.rGet(-2) as? Int ?: return@before
        it.args.rSet(
          -2,
          when (bitmapGenerationMode) {
            MODE_WITH_SHADOW -> MODE_DEFAULT
            MODE_HARDWARE_WITH_SHADOW -> MODE_HARDWARE
            else -> bitmapGenerationMode
          },
        )
      }
    }
  }
}
