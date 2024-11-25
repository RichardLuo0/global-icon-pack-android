package com.richardluo.globalIconPack.reflect;

import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;

public class Resources {

  public static Method getDrawable =
      XposedHelpers.findMethodExact(
          android.content.res.Resources.class,
          "getDrawable",
          int.class,
          android.content.res.Resources.Theme.class);

  public static Method getDrawableForDensity =
      XposedHelpers.findMethodExact(
          android.content.res.Resources.class,
          "getDrawableForDensity",
          int.class,
          int.class,
          android.content.res.Resources.Theme.class);
}
