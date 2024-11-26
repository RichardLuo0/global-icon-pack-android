package com.richardluo.globalIconPack;

import android.app.AndroidAppHelper;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageItemInfo;

public class Utils {

  public static ComponentName getComponentName(PackageItemInfo info) {
    return info instanceof ApplicationInfo
        ? getComponentName(info.packageName)
        : new ComponentName(info.packageName, info.name);
  }

  public static ComponentName getComponentName(String packageName) {
    Intent intent =
        AndroidAppHelper.currentApplication()
            .getPackageManager()
            .getLaunchIntentForPackage(packageName);
    return intent == null ? null : intent.getComponent();
  }
}
