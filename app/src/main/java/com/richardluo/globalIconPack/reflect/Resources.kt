package com.richardluo.globalIconPack.reflect;

import android.graphics.drawable.Drawable;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;

public class Resources {
  public static final Method getDrawableForDensity =
      XposedHelpers.findMethodExact(
          android.content.res.Resources.class,
          "getDrawableForDensity",
          int.class,
          int.class,
          android.content.res.Resources.Theme.class);

  public static Drawable getDrawable(
      android.content.res.Resources resources,
      int resId,
      android.content.res.Resources.Theme theme) {
    return getDrawableForDensity(resources, resId, 0, theme);
  }

  public static Drawable getDrawableForDensity(
      android.content.res.Resources resources,
      int resId,
      int iconDpi,
      android.content.res.Resources.Theme theme) {
    try {
      return (Drawable)
          XposedBridge.invokeOriginalMethod(
              getDrawableForDensity, resources, new Object[] {resId, iconDpi, theme});
    } catch (Exception e) {
      XposedBridge.log(e);
      return null;
    }
  }
}
