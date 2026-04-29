package com.richardluo.globalIconPack.xposed

import android.os.Build
import android.view.View
import com.richardluo.globalIconPack.reflect.BaseIconFactory
import com.richardluo.globalIconPack.utils.allConstructors
import com.richardluo.globalIconPack.utils.allMethods
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.deoptimize
import com.richardluo.globalIconPack.utils.field
import com.richardluo.globalIconPack.utils.hook
import com.richardluo.globalIconPack.utils.method
import com.richardluo.globalIconPack.utils.tryHook
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class NoShadow : Hook {

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
    tryHook("removeIconShadow") {
      tryDo {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return@tryDo fail()
        // Android 16 qpr2
        val iconOptions = BaseIconFactory.getIconOptionsClass(lpp) ?: return@tryDo fail()
        val addShadowsF = iconOptions.field("addShadows") ?: return@tryDo fail()
        BaseIconFactory.getClass(lpp)
          ?.allMethods("createBadgedIconBitmap")
          ?.hook { before { addShadowsF.set(args[1], false) } }
          .registerToScopeOrFail()
      }

      tryDo {
        tryHook("remove LauncherIcons shadows") {
          tryDo {
            classOf("android.util.LauncherIcons", lpp)
              ?.allMethods("wrapIconDrawableWithShadow")
              ?.hook { replace { args[0] } }
              .registerToScopeOrFail()
          }
          tryDo {
            classOf("com.android.launcher3.Flags", lpp)
              ?.allMethods("enableLauncherIconShapes")
              ?.hook { replace { false } }
              .registerToScopeOrFail()
          }
        }

        classOf("com.android.launcher3.icons.ShadowGenerator", lpp)?.method("addPathShadow")?.hook {
          before { result = null }
        }
      }
    }
  }
}
