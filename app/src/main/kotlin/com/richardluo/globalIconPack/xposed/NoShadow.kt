package com.richardluo.globalIconPack.xposed

import android.os.Build
import android.view.View
import com.richardluo.globalIconPack.reflect.BaseIconFactory
import com.richardluo.globalIconPack.utils.allConstructors
import com.richardluo.globalIconPack.utils.allMethods
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.deoptimize
import com.richardluo.globalIconPack.utils.elseIf
import com.richardluo.globalIconPack.utils.field
import com.richardluo.globalIconPack.utils.hook
import com.richardluo.globalIconPack.utils.rGet
import com.richardluo.globalIconPack.utils.rSet
import com.richardluo.globalIconPack.utils.runSafeIf
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
    classOf("com.android.wm.shell.bubbles.BadgedImageView", lpp)
      ?.allConstructors()
      ?.deoptimize()
      ?.hook { after { thisObject.asType<View>()?.outlineProvider = null } }
    removeIconShadow(lpp)
  }

  override fun onHookSettings(lpp: LoadPackageParam) = removeIconShadow(lpp)

  private fun removeIconShadow(lpp: LoadPackageParam) {
    runSafeIf {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return@runSafeIf null
        // Android 16 qpr2
        val iconOptions = BaseIconFactory.getIconOptionsClass(lpp) ?: return@runSafeIf null
        val addShadowsF = iconOptions.field("addShadows") ?: return@runSafeIf null
        BaseIconFactory.getClass(lpp)?.allMethods("createBadgedIconBitmap")?.hook {
          before { addShadowsF.set(args[1], false) }
        }
      }
      .elseIf {
        classOf("android.util.LauncherIcons")?.allMethods("wrapIconDrawableWithShadow")?.hook {
          replace { args[0] }
        }
        BaseIconFactory.getClass(lpp)?.allMethods("drawIconBitmap")?.hook {
          before {
            val bitmapGenerationMode = args.rGet(-2) as? Int ?: return@before
            args.rSet(
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
}
