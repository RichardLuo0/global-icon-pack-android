package com.richardluo.globalIconPack;

import android.app.AndroidAppHelper;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageItemInfo;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class Utils {

  public static <K, V> void computeIfMissing(
      Map<K, V> map, K key, Function<? super K, ? extends V> mappingFunction) {
    Objects.requireNonNull(mappingFunction);
    if (!map.containsKey(key)) map.put(key, mappingFunction.apply(key));
  }

  public static <K, V> void callIfContains(Map<K, V> map, K key, Consumer<? super V> consumer) {
    if (map.containsKey(key)) consumer.accept(map.get(key));
  }

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
