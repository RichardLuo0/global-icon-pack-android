package com.richardluo.globalIconPack.xposed

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
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
 * Samsung One UI 9 / Android 17 (API 37) icon replacement hook for One UI Home.
 *
 * Covers:
 * 1. App Drawer (All Apps)
 * 2. Recents / Background apps ("close all apps" overview screen)
 * 3. Honeyspace wrappers & Samsung proprietary icon tray framework
 */
class SamsungOneUI9AppDrawerHook : Hook {

    context(xposed: XposedInterface)
    fun onHookSamsungLauncher(param: XposedModuleInterface.PackageReadyParam) {
        log("SamsungOneUI9: installing launcher & recents hooks for ${param.packageName}")
        hookLauncherActivityInfo()
        hookPackageManager()
        hookLauncherApps()
        hookSamsungHoneySpace(param)
        hookRecents(param)
    }

    // -----------------------------------------------------------------------------------------
    // 1. LauncherActivityInfo hooks
    // -----------------------------------------------------------------------------------------

    context(xposed: XposedInterface)
    private fun hookLauncherActivityInfo() {
        runSafe {
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

            runSafe {
                targetClass.allMethods("semGetApplicationIconForIconTray").hookCompat {
                    before {
                        val arg0 = args.getOrNull(0)
                        val componentName = when (arg0) {
                            is ApplicationInfo -> ComponentName(arg0.packageName, "")
                            is String -> ComponentName(arg0, "")
                            else -> return@before
                        }
                        val sc = getSC() ?: return@before
                        val id = sc.getId(componentName) ?: return@before
                        val density = args.getOrNull(1) as? Int ?: 0
                        val drawable = with(xposed) { sc.getIcon(id, density) } ?: return@before
                        result = drawable
                        log("SamsungOneUI9: served icon for ${componentName.packageName} via ${targetClass.simpleName}.semGetApplicationIconForIconTray ✓")
                    }
                }
            }

            runSafe {
                targetClass.allMethods("getActivityIcon").hookCompat {
                    before {
                        val componentName = args.getOrNull(0) as? ComponentName ?: return@before
                        val sc = getSC() ?: return@before
                        val id = sc.getId(componentName) ?: return@before
                        val drawable = with(xposed) { sc.getIcon(id, 0) } ?: return@before
                        result = drawable
                        log("SamsungOneUI9: served icon for ${componentName.flattenToShortString()} via ${targetClass.simpleName}.getActivityIcon ✓")
                    }
                }
            }

            runSafe {
                targetClass.allMethods("getDrawable").hookCompat {
                    before {
                        val resId = args.getOrNull(1) as? Int ?: return@before
                        if ((resId and 0xff000000.toInt()) == IN_SC) {
                            val sc = getSC() ?: return@before
                            val id = resId and 0x00ffffff
                            val drawable = with(xposed) { sc.getIcon(id, 0) } ?: return@before
                            result = drawable
                            log("SamsungOneUI9: served marked icon 0x${Integer.toHexString(resId)} via ${targetClass.simpleName}.getDrawable ✓")
                        }
                    }
                }
            }
        }
        log("SamsungOneUI9: hooked PackageManager methods ✓")
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
    // 5. Samsung Recents / Overview TaskView hooks ("close all apps" screen)
    // -----------------------------------------------------------------------------------------

    context(xposed: XposedInterface)
    private fun hookRecents(param: XposedModuleInterface.PackageReadyParam) {
        runSafe {
            val taskViewClass = classOf("com.honeyspace.ui.honeypots.tasklist.presentation.TaskView", param)
            taskViewClass?.allMethods("setIconData")?.hookCompat {
                before {
                    val taskView = thisObject ?: return@before
                    val tasks = taskView.javaClass.method("getTasks")?.invoke(taskView) as? List<*>
                    val task = tasks?.firstOrNull()
                    val componentName = (task?.javaClass?.method("getTopComponent")?.invoke(task) as? ComponentName)
                        ?: (taskView.javaClass.method("getTaskPackageName")?.invoke(taskView) as? String)?.let { ComponentName(it, "") }
                        ?: return@before

                    val sc = getSC() ?: return@before
                    val id = sc.getId(componentName) ?: return@before
                    val customDrawable = with(xposed) { sc.getIcon(id, 0) } ?: return@before
                    args[0] = customDrawable
                    log("SamsungOneUI9: replaced Recents TaskView icon for ${componentName.flattenToShortString()} ✓")
                }
            }
            if (taskViewClass != null) log("SamsungOneUI9: hooked TaskView.setIconData for Recents ✓")
        }

        runSafe {
            val deskTaskViewClass = classOf("com.honeyspace.ui.honeypots.tasklist.presentation.DeskTaskView", param)
            deskTaskViewClass?.allMethods("setTaskSceneData")?.hookCompat {
                after {
                    val deskView = thisObject ?: return@after
                    val pkg = deskView.javaClass.method("getTaskPackageName")?.invoke(deskView) as? String ?: return@after
                    val componentName = ComponentName(pkg, "")
                    val sc = getSC() ?: return@after
                    val id = sc.getId(componentName) ?: return@after
                    val customDrawable = with(xposed) { sc.getIcon(id, 0) } ?: return@after
                    val headerIconView = deskView.javaClass.method("getDeskTaskHeaderIconView")?.invoke(deskView)
                    headerIconView?.javaClass?.allMethods("setIconData")?.forEach {
                        it.invoke(headerIconView, customDrawable)
                    }
                    log("SamsungOneUI9: replaced DeskTaskView header icon for $pkg ✓")
                }
            }
            if (deskTaskViewClass != null) log("SamsungOneUI9: hooked DeskTaskView for Recents ✓")
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
