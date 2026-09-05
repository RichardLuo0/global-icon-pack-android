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
import com.richardluo.globalIconPack.utils.hookCompat
import com.richardluo.globalIconPack.utils.method
import com.richardluo.globalIconPack.utils.tryHook
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface

class NoShadow : Hook {

  context(xposed: XposedInterface)
  override fun onHookPixelLauncher(param: XposedModuleInterface.PackageReadyParam) =
    removeIconShadow(param)

  context(xposed: XposedInterface)
  override fun onHookSystemUI(param: XposedModuleInterface.PackageReadyParam) {
    // Remove bubble shadow
    classOf("com.android.wm.shell.bubbles.BadgedImageView", param)
      ?.allConstructors()
      ?.deoptimize()
      ?.hookCompat { after { thisObject.asType<View>()?.outlineProvider = null } }
    removeIconShadow(param)
  }

  context(xposed: XposedInterface)
  override fun onHookSettings(param: XposedModuleInterface.PackageReadyParam) =
    removeIconShadow(param)

  context(xposed: XposedInterface)
  private fun removeIconShadow(param: XposedModuleInterface.PackageReadyParam) {
    tryHook("removeIconShadow") {
      tryDo {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return@tryDo fail()
        // Android 16 qpr2
        val iconOptions = BaseIconFactory.getIconOptionsClass(param) ?: return@tryDo fail()
        val addShadowsF = iconOptions.field("addShadows") ?: return@tryDo fail()
        BaseIconFactory.getClass(param)
          ?.allMethods("createBadgedIconBitmap")
          ?.hookCompat { before { addShadowsF.set(args[1], false) } }
          .registerToScopeOrFail()
      }

      tryDo {
        tryHook("remove LauncherIcons shadows") {
          tryDo {
            classOf("android.util.LauncherIcons", param)
              ?.allMethods("wrapIconDrawableWithShadow")
              ?.hook { args[0] }
              .registerToScopeOrFail()
          }
          tryDo {
            classOf("com.android.launcher3.Flags", param)
              ?.allMethods("enableLauncherIconShapes")
              ?.hook { false }
              .registerToScopeOrFail()
          }
        }

        classOf("com.android.launcher3.icons.ShadowGenerator", param)
          ?.method("addPathShadow")
          ?.hook {
            null
          }
      }
    }
  }
}
