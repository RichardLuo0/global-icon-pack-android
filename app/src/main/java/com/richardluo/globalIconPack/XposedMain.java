package com.richardluo.globalIconPack;

import static com.richardluo.globalIconPack.CustomIconPackKt.getCip;
import static com.richardluo.globalIconPack.Utils.getComponentName;
import static com.richardluo.globalIconPack.reflect.Resources.getDrawableForDensity;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageItemInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.WeakHashMap;

public class XposedMain implements IXposedHookLoadPackage {

  @Override
  public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpp) {
    if (!lpp.isFirstApplication) return;

    if ("com.google.android.apps.nexuslauncher".equals(lpp.packageName)) {
      // Do not force shape
      Class<?> baseIconFactory =
          XposedHelpers.findClass("com.android.launcher3.icons.BaseIconFactory", lpp.classLoader);
      XposedBridge.hookAllMethods(
          baseIconFactory,
          "normalizeAndWrapToAdaptiveIcon",
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
              if (getPref().getBoolean("noForceShape", true)) param.args[1] = false;
            }
          });
    }

    final Class<?> apm =
        XposedHelpers.findClass("android.app.ApplicationPackageManager", lpp.classLoader);

    // Resource id always starts with 0x7f, change it to 0xff to indict that this is an icon
    // Assume the icon res id is only used in getDrawable()
    XC_MethodHook replaceIconResId =
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param) {
            PackageItemInfo info = (PackageItemInfo) param.thisObject;
            if (info.icon != 0) info.icon |= 0xff000000;
          }
        };
    XposedBridge.hookAllConstructors(ActivityInfo.class, replaceIconResId);
    XposedBridge.hookAllConstructors(ApplicationInfo.class, replaceIconResId);

    final Map<Resources, String> resourcesToPkgName = new WeakHashMap<>();

    XC_MethodHook storePackageName =
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param) {
            String packageName = null;
            Object arg0 = param.args[0];
            if (arg0 instanceof String) packageName = (String) arg0;
            else if (arg0 instanceof ApplicationInfo)
              packageName = ((ApplicationInfo) arg0).packageName;
            if (packageName != null)
              resourcesToPkgName.put((Resources) param.getResult(), packageName);
          }
        };
    XposedBridge.hookAllMethods(apm, "getResourcesForApplication", storePackageName);

    XC_MethodReplacement replaceIcon =
        new XC_MethodReplacement() {

          @Override
          protected Drawable replaceHookedMethod(MethodHookParam param) throws Exception {
            Drawable drawable = null;
            int resId = (int) param.args[0];
            int density = (int) param.args[1];
            if ((resId & 0xff000000) == 0xff000000) {
              // Restore res id
              param.args[0] = resId & 0x00ffffff | 0x7f000000;
              CustomIconPack cip = getCip(getPref());
              if (cip != null) {
                drawable = getIcon((Resources) param.thisObject, cip, density);
                if (drawable == null) drawable = callOriginalMethod(param);
                drawable = cip.generateIcon(drawable, density);
              }
            }
            if (drawable == null) drawable = callOriginalMethod(param);
            return drawable;
          }

          private Drawable getIcon(Resources thisObject, CustomIconPack cip, int density) {
            String packageName = resourcesToPkgName.get(thisObject);
            if (packageName == null) return null;
            ComponentName cn = getComponentName(packageName);
            if (cn == null) return null;
            IconEntry entry = cip.getIconEntry(cn);
            if (entry == null) return null;
            return cip.getIcon(entry, density);
          }

          private Drawable callOriginalMethod(MethodHookParam param)
              throws InvocationTargetException, IllegalAccessException {
            return (Drawable)
                XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
          }
        };
    XposedBridge.hookMethod(getDrawableForDensity, replaceIcon);
  }

  private XSharedPreferences pref;

  XSharedPreferences getPref() {
    if (pref == null) {
      pref = new XSharedPreferences(BuildConfig.APPLICATION_ID);
      if (!pref.getFile().canRead())
        XposedBridge.log("Pref can not be read. Plz open global icon pack at least once.");
    }
    return pref;
  }
}
