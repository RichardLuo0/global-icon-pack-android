package com.richardluo.globalIconPack;

import static com.richardluo.globalIconPack.CustomIconPackKt.getCip;
import static com.richardluo.globalIconPack.Utils.callIfContains;
import static com.richardluo.globalIconPack.Utils.computeIfMissing;
import static com.richardluo.globalIconPack.Utils.getComponentName;

import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageItemInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;
import java.util.HashMap;
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

    final Class<?> applicationPackageManager =
        XposedHelpers.findClass("android.app.ApplicationPackageManager", lpp.classLoader);

    final Map<String, ComponentName> resIdToCN = new HashMap<>();

    XposedBridge.hookAllMethods(
        applicationPackageManager,
        "loadUnbadgedItemIcon",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param) {
            PackageItemInfo info = (PackageItemInfo) param.args[0];
            if (info == null) return;
            int resId = info.icon;
            computeIfMissing(resIdToCN, getUniqueResId(info, resId), k -> getComponentName(info));
          }
        });

    XposedBridge.hookAllMethods(
        ComponentInfo.class,
        "getIconResource",
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param) {
            PackageItemInfo info = (PackageItemInfo) param.thisObject;
            if (info == null) return;
            int resId = (int) param.getResult();
            computeIfMissing(resIdToCN, getUniqueResId(info, resId), k -> getComponentName(info));
          }
        });

    final Map<Resources, ApplicationInfo> appInfoMap = new WeakHashMap<>();

    for (Method method : applicationPackageManager.getMethods()) {
      if ("getResourcesForApplication".equals(method.getName())
          && method.getParameterCount() >= 1
          && method.getParameterTypes()[0].equals(ApplicationInfo.class))
        XposedBridge.hookMethod(
            method,
            new XC_MethodHook() {
              @Override
              protected void afterHookedMethod(MethodHookParam param) {
                appInfoMap.put((Resources) param.getResult(), (ApplicationInfo) param.args[0]);
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
            ApplicationInfo info = appInfoMap.get((Resources) param.thisObject);
            if (info == null) return;
            int resId = (int) param.args[0];
            callIfContains(
                resIdToCN,
                getUniqueResId(info, resId),
                cn -> {
                  isAppIcon = true;
                  if (cn == null) return;
                  CustomIconPack cip = getCip(getPref());
                  if (cip == null) return;
                  IconEntry entry = cip.getIconEntry(cn);
                  if (entry != null) param.setResult(cip.getIcon(entry, density));
                });
          }

          @Override
          protected void afterHookedMethod(MethodHookParam param) {
            if (!isAppIcon) return;
            Drawable baseIcon = (Drawable) param.getResult();
            if (baseIcon == null) return;
            CustomIconPack cip = getCip(getPref());
            if (cip == null) return;
            param.setResult(cip.generateIcon(baseIcon, density));
          }
        };
    XposedBridge.hookAllMethods(Resources.class, "getDrawable", replaceIcon);
    XposedBridge.hookAllMethods(Resources.class, "getDrawableForDensity", replaceIcon);
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

  String getUniqueResId(PackageItemInfo info, int resId) {
    return info.packageName + "#" + resId;
  }
}
