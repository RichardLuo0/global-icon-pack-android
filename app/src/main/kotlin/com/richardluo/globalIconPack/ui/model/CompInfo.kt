package com.richardluo.globalIconPack.ui.model

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Parcelable
import com.richardluo.globalIconPack.iconPack.source.getComponentName
import com.richardluo.globalIconPack.utils.asType
import kotlinx.parcelize.Parcelize

abstract class CompInfo : Parcelable {
  abstract val componentName: ComponentName
  abstract val label: String

  abstract fun getIcon(context: Context): Drawable?

  override fun equals(other: Any?): Boolean {
    return other is CompInfo && componentName == other.componentName
  }

  override fun hashCode() = componentName.hashCode()
}

@Parcelize
class AppCompInfo(
  override val componentName: ComponentName,
  override val label: String,
  val info: ApplicationInfo,
) : CompInfo() {
  constructor(
    context: Context,
    info: ApplicationInfo,
  ) : this(
    getComponentName(info.packageName),
    info.loadLabel(context.packageManager).toString(),
    info,
  )

  override fun getIcon(context: Context) = context.packageManager.getApplicationIcon(info)
}

@Parcelize
class ActivityCompInfo(
  override val componentName: ComponentName,
  override val label: String,
  val info: ActivityInfo,
) : CompInfo() {
  constructor(
    context: Context,
    info: ActivityInfo,
  ) : this(
    ComponentName(info.packageName, info.name),
    if (info.nonLocalizedLabel != null || info.labelRes != 0)
      info.loadLabel(context.packageManager).toString()
    else info.name.substringAfterLast("."),
    info,
  )

  override fun getIcon(context: Context): Drawable? = info.loadIcon(context.packageManager)
}

@Parcelize
class ShortcutCompInfo(
  override val componentName: ComponentName,
  override val label: String,
  val info: ShortcutInfo,
) : CompInfo() {
  constructor(
    info: ShortcutInfo
  ) : this(getComponentName(info), info.getLabel()?.toString() ?: info.id, info)

  override fun getIcon(context: Context) =
    context
      .getSystemService(Context.LAUNCHER_APPS_SERVICE)
      .asType<LauncherApps>()
      ?.getShortcutIconDrawable(info, 0)
}

private fun ShortcutInfo.getLabel() = shortLabel?.ifEmpty { longLabel }
