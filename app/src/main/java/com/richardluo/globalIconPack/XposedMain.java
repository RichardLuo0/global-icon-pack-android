package com.richardluo.globalIconPack;

import android.app.AndroidAppHelper;
import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import androidx.annotation.Nullable;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;
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

    Map<Resources, ApplicationInfo> resourcesMap = new WeakHashMap<>();

    Class<?> applicationPackageManager =
        XposedHelpers.findClass("android.app.ApplicationPackageManager", lpp.classLoader);
    for (Method method : applicationPackageManager.getMethods()) {
      if ("getResourcesForApplication".equals(method.getName())
          && method.getParameterCount() >= 1
          && method.getParameterTypes()[0].equals(ApplicationInfo.class))
        XposedBridge.hookMethod(
            method,
            new XC_MethodHook() {
              @Override
              protected void afterHookedMethod(MethodHookParam param) {
                resourcesMap.put((Resources) param.getResult(), (ApplicationInfo) param.args[0]);
              }
            });
    }

    XC_MethodHook replaceIcon =
        new XC_MethodHook() {
          private boolean isAppIcon = false;
          private int density = 0;

          @Override
          protected void beforeHookedMethod(MethodHookParam param) {
            isAppIcon = false;
            density =
                param.args.length >= 2 && param.args[1] instanceof Integer
                    ? (int) param.args[1]
                    : 0;
            CustomIconPack cip = getCip();
            if (cip == null) return;
            int resId = (int) param.args[0];
            ApplicationInfo appInfo = resourcesMap.get((Resources) param.thisObject);
            if (appInfo == null) return;
            if (resId == appInfo.icon) {
              isAppIcon = true;
              ComponentName cn = cip.getComponentName(appInfo);
              if (cn == null) return;
              IconEntry entry = cip.getIconEntry(cn);
              if (entry != null) param.setResult(cip.getIcon(entry, density));
            }
          }

          @Override
          protected void afterHookedMethod(MethodHookParam param) {
            if (!isAppIcon) return;
            CustomIconPack cip = getCip();
            if (cip == null) return;
            Drawable baseIcon = (Drawable) param.getResult();
            if (baseIcon == null) return;
            param.setResult(cip.generateIcon(baseIcon, density));
          }
        };
    XposedBridge.hookAllMethods(Resources.class, "getDrawable", replaceIcon);
    XposedBridge.hookAllMethods(Resources.class, "getDrawableForDensity", replaceIcon);
  }

  private XSharedPreferences pref;
  private CustomIconPack cip = null;

  XSharedPreferences getPref() {
    if (pref == null) pref = new XSharedPreferences(BuildConfig.APPLICATION_ID);
    if (!pref.getFile().canRead())
      XposedBridge.log("Pref can not be read. Plz open global icon pack at least once");
    return pref;
  }

  @Nullable
  CustomIconPack getCip() {
    if (cip == null) {
      XSharedPreferences pref = getPref();
      String packPackageName = pref.getString("iconPack", "");
      if (packPackageName.isEmpty()) return null;
      cip =
          new CustomIconPack(
              AndroidAppHelper.currentApplication().getPackageManager(), packPackageName, pref);
      cip.loadInternal();
    }
    return cip;
  }
}
