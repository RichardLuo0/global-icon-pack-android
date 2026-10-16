package com.richardluo.globalIconPack.xposed

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import com.richardluo.globalIconPack.iconPack.getSC
import com.richardluo.globalIconPack.utils.Logger.log
import com.richardluo.globalIconPack.utils.allMethods
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.field
import com.richardluo.globalIconPack.utils.getAs
import com.richardluo.globalIconPack.utils.hookCompat
import com.richardluo.globalIconPack.utils.method
import com.richardluo.globalIconPack.utils.runSafe
import com.richardluo.globalIconPack.utils.withHighByte
import com.richardluo.globalIconPack.xposed.ReplaceIcon.Companion.IN_SC
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface

/**
 * Samsung One UI 9 / Android 17 (API 37) app-drawer icon replacement hook.
 *
 * ## Root Cause in One UI Home 18.x
 *
 * Samsung's One UI 9 launcher (Galaxy S26 series) does NOT use AOSP's
 * `LauncherActivityInfo.getIcon()` or `ApplicationPackageManager.getDrawableInternal()`.
 *
 * Instead, Samsung uses a proprietary icon pipeline via `semGet*IconForIconTray`:
 * 1. `LauncherActivityInfo.semGetBadgedIconForIconTray(int density)`
 * 2. `LauncherActivityInfoWrapper.semGetBadgedIconForIconTray(LauncherActivityInfo, int)`
 * 3. `PackageManager.semGetActivityIconForIconTray(ComponentName, int)`
 * 4. `PackageManagerWrapper.getActivityIconForIconTray(Context, ComponentName, int)`
 * 5. `LiveIconSupplier.getIconFromLauncherApps(Context, ComponentKey)`
 *
 * This class intercepts ALL of these Samsung-specific and AOSP paths directly to ensure
 * themed icons are returned everywhere in the app drawer.
 */
class SamsungOneUI9AppDrawerHook : Hook {

    context(xposed: XposedInterface)
    fun onHookSamsungLauncher(param: XposedModuleInterface.PackageReadyParam) {
        log("SamsungOneUI9: installing app-drawer hooks for ${param.packageName}")
        hookLauncherActivityInfo()
        hookPackageManager()
        hookLauncherApps()
        hookSamsungHoneySpace(param)
    }

    // -----------------------------------------------------------------------------------------
    // 1. LauncherActivityInfo hooks
    // -----------------------------------------------------------------------------------------

    context(xposed: XposedInterface)
    private fun hookLauncherActivityInfo() {
        runSafe {
            // Samsung's proprietary icon tray method on LauncherActivityInfo
            LauncherActivityInfo::class.java.allMethods("semGetBadgedIconForIconTray").hookCompat {
                before {
                    val info = thisObject as? LauncherActivityInfo ?: return@before
                    val componentName = info.componentName ?: return@before
                    val sc = getSC() ?: return@before
                    val id = sc.getId(componentName) ?: return@before
                    val density = args.getOrNull(0) as? Int ?: 0
                    val drawable = with(xposed) { sc.getIcon(id, density) } ?: return@before
                    result = drawable
                    log("SamsungOneUI9: served icon for ${componentName.flattenToShortString()} via LAI.semGetBadgedIconForIconTray ✓")
                }
            }
            log("SamsungOneUI9: hooked LauncherActivityInfo.semGetBadgedIconForIconTray ✓")
        }

        runSafe {
            // Standard AOSP getIcon fallback
            LauncherActivityInfo::class.java.allMethods("getIcon").hookCompat {
                before {
                    val info = thisObject as? LauncherActivityInfo ?: return@before
                    val componentName = info.componentName ?: return@before
                    val sc = getSC() ?: return@before
                    val id = sc.getId(componentName) ?: return@before
                    val density = args.getOrNull(0) as? Int ?: 0
                    val drawable = with(xposed) { sc.getIcon(id, density) } ?: return@before
                    result = drawable
                    log("SamsungOneUI9: served icon for ${componentName.flattenToShortString()} via LAI.getIcon ✓")
                }
            }
            log("SamsungOneUI9: hooked LauncherActivityInfo.getIcon ✓")
        }

        runSafe {
            // Standard AOSP getBadgedIcon fallback
            LauncherActivityInfo::class.java.allMethods("getBadgedIcon").hookCompat {
                before {
                    val info = thisObject as? LauncherActivityInfo ?: return@before
                    val componentName = info.componentName ?: return@before
                    val sc = getSC() ?: return@before
                    val id = sc.getId(componentName) ?: return@before
                    val density = args.getOrNull(0) as? Int ?: 0
                    val drawable = with(xposed) { sc.getIcon(id, density) } ?: return@before
                    result = drawable
                    log("SamsungOneUI9: served icon for ${componentName.flattenToShortString()} via LAI.getBadgedIcon ✓")
                }
            }
            log("SamsungOneUI9: hooked LauncherActivityInfo.getBadgedIcon ✓")
        }
    }

    // -----------------------------------------------------------------------------------------
    // 2. PackageManager & ApplicationPackageManager hooks
    // -----------------------------------------------------------------------------------------

    context(xposed: XposedInterface)
    private fun hookPackageManager() {
        val appPmClass = classOf("android.app.ApplicationPackageManager")
        val classesToHook = listOfNotNull(PackageManager::class.java, appPmClass)

        for (targetClass in classesToHook) {
            runSafe {
                targetClass.allMethods("semGetActivityIconForIconTray").hookCompat {
                    before {
                        val componentName = args.getOrNull(0) as? ComponentName ?: return@before
                        val sc = getSC() ?: return@before
                        val id = sc.getId(componentName) ?: return@before
                        val density = args.getOrNull(1) as? Int ?: 0
                        val drawable = with(xposed) { sc.getIcon(id, density) } ?: return@before
                        result = drawable
                        log("SamsungOneUI9: served icon for ${componentName.flattenToShortString()} via ${targetClass.simpleName}.semGetActivityIconForIconTray ✓")
                    }
                }
            }
        }
        log("SamsungOneUI9: hooked PackageManager semGetActivityIconForIconTray ✓")
    }

    // -----------------------------------------------------------------------------------------
    // 3. LauncherApps hooks (resolveActivity & getActivityList)
    // -----------------------------------------------------------------------------------------

    context(xposed: XposedInterface)
    private fun hookLauncherApps() {
        runSafe {
            LauncherApps::class.java.allMethods("getActivityList").hookCompat {
                after {
                    @Suppress("UNCHECKED_CAST")
                    val list = result as? List<LauncherActivityInfo> ?: return@after
                    val sc = getSC() ?: return@after
                    val componentNames = list.mapNotNull { it.componentName }
                    val ids = sc.getId(componentNames)
                    var stamped = 0
                    list.forEachIndexed { i, info ->
                        val id = ids.getOrNull(i) ?: return@forEachIndexed
                        if (stampActivityInfoIcon(info, id)) stamped++
                    }
                    log("SamsungOneUI9: stamped $stamped/${list.size} icons via getActivityList")
                }
            }
            log("SamsungOneUI9: hooked LauncherApps.getActivityList ✓")
        }

        runSafe {
            LauncherApps::class.java.allMethods("resolveActivity").hookCompat {
                after {
                    val info = result as? LauncherActivityInfo ?: return@after
                    val cn = info.componentName ?: return@after
                    val sc = getSC() ?: return@after
                    val id = sc.getId(cn) ?: return@after
                    stampActivityInfoIcon(info, id)
                }
            }
            log("SamsungOneUI9: hooked LauncherApps.resolveActivity ✓")
        }
    }

    // -----------------------------------------------------------------------------------------
    // 4. Samsung One UI com.honeyspace wrapper hooks
    // -----------------------------------------------------------------------------------------

    context(xposed: XposedInterface)
    private fun hookSamsungHoneySpace(param: XposedModuleInterface.PackageReadyParam) {
        // LauncherActivityInfoWrapper.semGetBadgedIconForIconTray(LauncherActivityInfo, int)
        runSafe {
            val laiWrapper = classOf("com.honeyspace.common.wrapper.LauncherActivityInfoWrapper", param)
            laiWrapper?.allMethods("semGetBadgedIconForIconTray")?.hookCompat {
                before {
                    val info = args.getOrNull(0) as? LauncherActivityInfo ?: return@before
                    val componentName = info.componentName ?: return@before
                    val sc = getSC() ?: return@before
                    val id = sc.getId(componentName) ?: return@before
                    val density = args.getOrNull(1) as? Int ?: 0
                    val drawable = with(xposed) { sc.getIcon(id, density) } ?: return@before
                    result = drawable
                    log("SamsungOneUI9: served icon for ${componentName.flattenToShortString()} via LAIWrapper.semGetBadgedIconForIconTray ✓")
                }
            }
            if (laiWrapper != null) log("SamsungOneUI9: hooked LauncherActivityInfoWrapper ✓")
        }

        // PackageManagerWrapper.getActivityIconForIconTray(Context, ComponentName, int)
        runSafe {
            val pmWrapper = classOf("com.honeyspace.common.wrapper.PackageManagerWrapper", param)
            pmWrapper?.allMethods("getActivityIconForIconTray")?.hookCompat {
                before {
                    val componentName = args.getOrNull(1) as? ComponentName ?: return@before
                    val sc = getSC() ?: return@before
                    val id = sc.getId(componentName) ?: return@before
                    val density = args.getOrNull(2) as? Int ?: 0
                    val drawable = with(xposed) { sc.getIcon(id, density) } ?: return@before
                    result = drawable
                    log("SamsungOneUI9: served icon for ${componentName.flattenToShortString()} via PMWrapper.getActivityIconForIconTray ✓")
                }
            }
            if (pmWrapper != null) log("SamsungOneUI9: hooked PackageManagerWrapper ✓")
        }

        // LiveIconSupplier$Companion.getIconFromLauncherApps(Context, ComponentKey)
        runSafe {
            val liveIconSupplier = classOf("com.honeyspace.ui.common.iconview.LiveIconSupplier\$Companion", param)
                ?: classOf("com.honeyspace.ui.common.iconview.LiveIconSupplier", param)
            liveIconSupplier?.allMethods("getIconFromLauncherApps")?.hookCompat {
                before {
                    val key = args.getOrNull(1) ?: return@before
                    val componentName = key.javaClass.method("getComponentName")?.invoke(key) as? ComponentName ?: return@before
                    val sc = getSC() ?: return@before
                    val id = sc.getId(componentName) ?: return@before
                    val drawable = with(xposed) { sc.getIcon(id, 0) } ?: return@before
                    result = drawable
                    log("SamsungOneUI9: served icon for ${componentName.flattenToShortString()} via LiveIconSupplier.getIconFromLauncherApps ✓")
                }
            }
            if (liveIconSupplier != null) log("SamsungOneUI9: hooked LiveIconSupplier.getIconFromLauncherApps ✓")
        }
    }

    // -----------------------------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------------------------

    private val mActivityInfoField by lazy {
        runSafe { LauncherActivityInfo::class.java.field("mActivityInfo") }
    }

    private fun stampActivityInfoIcon(info: LauncherActivityInfo, id: Int): Boolean {
        val activityInfo = mActivityInfoField?.getAs<ActivityInfo>(info) ?: return false
        activityInfo.icon = id.withHighByte(IN_SC)
        return true
    }
}
