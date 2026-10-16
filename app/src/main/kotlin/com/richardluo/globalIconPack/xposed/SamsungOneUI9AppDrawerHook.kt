package com.richardluo.globalIconPack.xposed

import android.content.pm.ActivityInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import com.richardluo.globalIconPack.iconPack.getSC
import com.richardluo.globalIconPack.utils.Logger.logD
import com.richardluo.globalIconPack.utils.allMethods
import com.richardluo.globalIconPack.utils.field
import com.richardluo.globalIconPack.utils.getAs
import com.richardluo.globalIconPack.utils.hookCompat
import com.richardluo.globalIconPack.utils.runSafe
import com.richardluo.globalIconPack.utils.withHighByte
import com.richardluo.globalIconPack.xposed.ReplaceIcon.Companion.IN_SC
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface

/**
 * Samsung One UI 9 / Android 17 (API 37) app-drawer icon replacement hook.
 *
 * ## Problem
 *
 * On Samsung Galaxy S26 Ultra (One UI Home 18.0.07.53, Android 17 / m3q):
 *
 *  - **Home screen** icons work fine — they go through the standard `PackageItemInfo`
 *    constructor / `ParceledListSlice` / `PackageInfoCommonUtils` paths that GIP hooks via
 *    `onHookApp`.
 *
 *  - **App drawer (All Apps)** icons do NOT work. Samsung One UI 9 uses the `com.honeyspace`
 *    framework whose `AllAppsIconSupplier` / `IconItemDataCreator` loads icons via:
 *
 *    ```
 *    LauncherApps.getActivityList(pkg, user)
 *      → LauncherActivityInfo.getIcon(density)
 *        → ApplicationPackageManager.getDrawable / getDrawableInternal
 *    ```
 *
 *    This path bypasses every hook GIP applies in `onHookApp` because:
 *
 *    1. `getDrawableInternal` was **removed/renamed** in Samsung's Android 17 build
 *       (LSPosed log: "No methods getDrawableInternal are found"). So the marked-icon
 *       drawable hook never fires inside the launcher process.
 *
 *    2. `com.sec.android.app.launcher` is not routed through `onHookPixelLauncher`, and
 *       even if it were, Samsung removed all `com.android.launcher3.icons.*` classes
 *       (log: "No class com.android.launcher3.icons.BaseIconFactory${'$'}IconOptions is found").
 *
 * ## Fix
 *
 * We hook **two points** in the launcher process:
 *
 * ### Hook A — `LauncherApps.getActivityList()` (batch, primary)
 * After Samsung's framework fetches the full app list, we iterate every
 * [LauncherActivityInfo] and stamp `ActivityInfo.icon` with `id | IN_SC`. This makes GIP's
 * downstream drawable hook fire — IF any version of that hook is active. On Samsung Android 17
 * where `getDrawableInternal` is missing, this alone is not enough, so we add:
 *
 * ### Hook B — `LauncherActivityInfo.getIcon()` (direct, primary on Samsung Android 17)
 * Before returning the icon drawable, we intercept the call, look up GIP's icon for the
 * component, and return it directly — completely bypassing the marked-id / drawable-hook
 * chain. This is the effective fix for Android 17 Samsung builds.
 */
class SamsungOneUI9AppDrawerHook : Hook {

    /**
     * Called from [XposedMain] when the loaded package is `com.sec.android.app.launcher`.
     * Install the two app-drawer hooks.
     */
    context(xposed: XposedInterface)
    fun onHookSamsungLauncher(param: XposedModuleInterface.PackageReadyParam) {
        logD("SamsungOneUI9: installing app-drawer hooks for ${param.packageName}")
        hookGetActivityList()
        hookLauncherActivityInfoGetIcon()
    }

    // -----------------------------------------------------------------------------------------
    // Hook A: LauncherApps.getActivityList
    // -----------------------------------------------------------------------------------------

    /**
     * After `LauncherApps.getActivityList(packageName, user)` returns, stamp GIP's icon ID
     * into the underlying [ActivityInfo] of every [LauncherActivityInfo].
     *
     * This fires when the app drawer loads the full list on open or after package changes.
     */
    context(xposed: XposedInterface)
    private fun hookGetActivityList() {
        runSafe {
            LauncherApps::class.java.allMethods("getActivityList").hookCompat {
                after {
                    @Suppress("UNCHECKED_CAST")
                    val list = result as? List<LauncherActivityInfo> ?: return@after
                    val sc = getSC() ?: return@after

                    // Batch-look up GIP icon IDs for all components at once.
                    val componentNames = list.mapNotNull { it.componentName }
                    val ids = sc.getId(componentNames)

                    var stamped = 0
                    list.forEachIndexed { i, info ->
                        val id = ids.getOrNull(i) ?: return@forEachIndexed
                        if (stampActivityInfoIcon(info, id)) stamped++
                    }
                    logD("SamsungOneUI9: stamped $stamped/${list.size} icons via getActivityList")
                }
            }
            logD("SamsungOneUI9: hooked LauncherApps.getActivityList ✓")
        } ?: logD("SamsungOneUI9: WARN – could not hook LauncherApps.getActivityList")
    }

    // -----------------------------------------------------------------------------------------
    // Hook B: LauncherActivityInfo.getIcon  (primary fix for Samsung Android 17)
    // -----------------------------------------------------------------------------------------

    /**
     * Before `LauncherActivityInfo.getIcon(density)` returns, check if GIP has an icon for
     * this component and return it directly. This fully bypasses the marked-id / drawable-hook
     * chain and is the effective fix for Samsung Android 17 where `getDrawableInternal` is gone.
     */
    context(xposed: XposedInterface)
    private fun hookLauncherActivityInfoGetIcon() {
        runSafe {
            LauncherActivityInfo::class.java.allMethods("getIcon").hookCompat {
                before {
                    val info = thisObject as? LauncherActivityInfo ?: return@before
                    val componentName = info.componentName ?: return@before
                    val sc = getSC() ?: return@before
                    val id = sc.getId(componentName) ?: return@before
                    val density = args.getOrNull(0) as? Int ?: 0
                    // getIcon() requires XposedInterface context receiver
                    val drawable = with(xposed) { sc.getIcon(id, density) } ?: return@before
                    result = drawable
                    logD(
                        "SamsungOneUI9: served icon for " +
                            componentName.flattenToShortString() +
                            " via getIcon ✓"
                    )
                }
            }
            logD("SamsungOneUI9: hooked LauncherActivityInfo.getIcon ✓")
        } ?: logD("SamsungOneUI9: WARN – could not hook LauncherActivityInfo.getIcon")
    }

    // -----------------------------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------------------------

    /** Cached field accessor for `LauncherActivityInfo.mActivityInfo`. */
    private val mActivityInfoField by lazy {
        runSafe { LauncherActivityInfo::class.java.field("mActivityInfo") }
    }

    /**
     * Writes `id | IN_SC` into the [ActivityInfo.icon] field of the [LauncherActivityInfo].
     * Returns `true` if the stamp succeeded.
     */
    private fun stampActivityInfoIcon(info: LauncherActivityInfo, id: Int): Boolean {
        val activityInfo = mActivityInfoField?.getAs<ActivityInfo>(info) ?: return false
        activityInfo.icon = id.withHighByte(IN_SC)
        return true
    }
}
