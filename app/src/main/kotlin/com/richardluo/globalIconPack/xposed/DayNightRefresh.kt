package com.richardluo.globalIconPack.xposed

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.database.ContentObserver
import android.database.sqlite.SQLiteOpenHelper
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.lang.ref.WeakReference
import android.util.SparseArray
import com.richardluo.globalIconPack.iconPack.reloadSourceResources
import com.richardluo.globalIconPack.iconPack.updateSourceConfiguration
import com.richardluo.globalIconPack.utils.Logger.logD
import com.richardluo.globalIconPack.utils.allMethods
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.field
import com.richardluo.globalIconPack.utils.getAs
import com.richardluo.globalIconPack.utils.hookCompat
import com.richardluo.globalIconPack.utils.method
import com.richardluo.globalIconPack.utils.runSafe
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface
import java.util.Queue

class DayNightRefresh : Hook {
  @Volatile private var lastAppliedUiMode = 0
  @Volatile private var lastAppliedAccentColor = 0
  @Volatile private var lastAppliedNeutralColor = 0
  @Volatile private var registeredListener = false

  context(xposed: XposedInterface)
  override fun onHookPixelLauncher(param: XposedModuleInterface.PackageReadyParam) {
    runSafe {
      val iconProvider =
        classOf("com.android.launcher3.icons.LauncherIconProviderImpl", param)
          ?: classOf("com.android.launcher3.icons.IconProvider", param)
          ?: return@runSafe
      val mSystemStateF = iconProvider.field("mSystemState") ?: return@runSafe
      val mContextF = iconProvider.field("mContext") ?: return@runSafe

      iconProvider.allMethods("updateSystemState").hookCompat {
        after {
          val mSystemStateObj = mSystemStateF.get(thisObject) ?: return@after
          val context = mContextF.getAs<Context>(thisObject) ?: return@after
          val uiMode =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

          if (mSystemStateObj is String) {
            val base = mSystemStateObj.replace(Regex(",uiMode=\\d+"), "")
            val newState = "$base,uiMode=$uiMode"
            mSystemStateF.set(thisObject, newState)
            logD("DayNightRefresh (legacy): updated mSystemState to $newState")
          } else {
            val valuesF = mSystemStateObj.javaClass.field("values")
            @Suppress("UNCHECKED_CAST")
            val oldValues = valuesF?.get(mSystemStateObj) as? Array<String>
            if (oldValues != null) {
              val cleanValues = oldValues.filter { !it.startsWith("uiMode=") }.toTypedArray()
              val newValues = cleanValues + "uiMode=$uiMode"
              val constructor =
                mSystemStateObj.javaClass.getDeclaredConstructor(Array<String>::class.java)
              constructor.isAccessible = true
              val newState = constructor.newInstance(newValues)
              mSystemStateF.set(thisObject, newState)
              logD("DayNightRefresh: updated PersistedItemState with uiMode=$uiMode")
            }
          }
        }
      }

      val statefulActivity =
        classOf("com.android.launcher3.statemanager.StatefulActivity", param) ?: return@runSafe
      val mOldConfigF = statefulActivity.field("mOldConfig")
      val mainHandler = Handler(Looper.getMainLooper())
      var currentActivity: WeakReference<Activity>? = null
      var pendingReason = "theme"

      fun checkColorsChanged(context: Context): Boolean {
        val pref = context.getSharedPreferences("gip_theme_state", Context.MODE_PRIVATE)
        val currentUiMode =
          context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val currentAccent =
          runCatching { context.getColor(android.R.color.system_accent1_500) }.getOrDefault(0)
        val currentNeutral =
          runCatching { context.getColor(android.R.color.system_neutral1_500) }.getOrDefault(0)

        val savedUiMode = pref.getInt("uiMode", 0)
        val savedAccent = pref.getInt("accent", 0)
        val savedNeutral = pref.getInt("neutral", 0)

        val changed =
          (savedUiMode != 0 && currentUiMode != 0 && currentUiMode != savedUiMode) ||
            (savedAccent != 0 && currentAccent != 0 && currentAccent != savedAccent) ||
            (savedNeutral != 0 && currentNeutral != 0 && currentNeutral != savedNeutral)

        if (changed || savedAccent == 0) {
          pref.edit()
            .putInt("uiMode", currentUiMode)
            .putInt("accent", currentAccent)
            .putInt("neutral", currentNeutral)
            .apply()
        }
        return changed
      }

      fun doRefresh(context: Context, newConfig: Configuration?, reason: String) {
        if (newConfig != null) {
          lastAppliedUiMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
          updateSourceConfiguration(newConfig)
        }
        val currentAccent =
          runCatching { context.getColor(android.R.color.system_accent1_500) }.getOrDefault(0)
        val currentNeutral =
          runCatching { context.getColor(android.R.color.system_neutral1_500) }.getOrDefault(0)
        val currentUiMode =
          context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        context.getSharedPreferences("gip_theme_state", Context.MODE_PRIVATE).edit()
          .putInt("uiMode", currentUiMode)
          .putInt("accent", currentAccent)
          .putInt("neutral", currentNeutral)
          .apply()

        reloadSourceResources()

        val appStateClass = classOf("com.android.launcher3.LauncherAppState", param)
        val instanceField = appStateClass?.field("INSTANCE")
        val singletonObj = instanceField?.get(null)
        val getM = singletonObj?.javaClass?.method("get", Context::class.java)
        val appState = getM?.invoke(singletonObj, context) ?: return

        val iconProviderObj = appStateClass.field("iconProvider")?.get(appState)
        if (iconProviderObj != null) {
          val updateSystemStateM =
            iconProviderObj.javaClass.method("updateSystemState")
              ?: iconProviderObj.javaClass.superclass?.method("updateSystemState")
          updateSystemStateM?.isAccessible = true
          updateSystemStateM?.invoke(iconProviderObj)
        }

        val iconCacheObj = appStateClass.field("iconCache")?.get(appState)
        if (iconCacheObj != null) {
          clearIconCache(iconCacheObj)
        }

        val modelF = appStateClass.field("model")
        val model = modelF?.get(appState)
        val forceReloadM = model?.javaClass?.method("forceReload", String::class.java)
        val reloadResult = forceReloadM?.invoke(model, reason)
        if (reloadResult != null) {
          logD("DayNightRefresh: triggered LauncherModel.forceReload due to $reason")
        } else {
          logD("DayNightRefresh: $reason changed, fallback recreating launcher")
          currentActivity?.get()?.recreate()
        }
      }

      val refreshRunnable = Runnable {
        val act = currentActivity?.get() ?: return@Runnable
        doRefresh(act, act.resources.configuration, pendingReason)
      }

      fun scheduleRefresh(reason: String) {
        pendingReason = reason
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, 300)
      }

      fun registerListeners(appContext: Context) {
        if (registeredListener) return
        registeredListener = true

        runSafe {
          val filter = android.content.IntentFilter(android.content.Intent.ACTION_WALLPAPER_CHANGED)
          val receiver =
            object : android.content.BroadcastReceiver() {
              override fun onReceive(context: Context, intent: android.content.Intent?) {
                if (checkColorsChanged(context)) {
                  logD("DayNightRefresh: ACTION_WALLPAPER_CHANGED detected color change")
                  scheduleRefresh("wallpaper")
                }
              }
            }
          if (android.os.Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
          } else {
            appContext.registerReceiver(receiver, filter)
          }
        }

        runSafe {
          val wm = appContext.getSystemService(Context.WALLPAPER_SERVICE) as? WallpaperManager
          wm?.addOnColorsChangedListener({ _, _ ->
            if (checkColorsChanged(appContext)) {
              logD("DayNightRefresh: addOnColorsChangedListener detected color change")
              scheduleRefresh("wallpaper")
            }
          }, mainHandler)
        }
      }

      statefulActivity.method("onCreate", android.os.Bundle::class.java)?.hookCompat {
        before {
          val activity = thisObject as? Activity ?: return@before
          currentActivity = WeakReference(activity)
          registerListeners(activity.applicationContext)
          if (checkColorsChanged(activity)) {
            logD("DayNightRefresh: onCreate detected color/theme change, refreshing cache")
            doRefresh(activity, activity.resources.configuration, "onCreate")
          }
        }
      }

      statefulActivity.method("handleConfigurationChanged", Configuration::class.java)?.hookCompat {
        before {
          val newConfig = args[0] as? Configuration ?: return@before
          val oldConfig = mOldConfigF?.getAs<Configuration>(thisObject) ?: return@before
          val oldUiMode = oldConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
          val newUiMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
          val activity = thisObject as? Activity ?: return@before
          if (oldUiMode != 0 && newUiMode != 0 && oldUiMode != newUiMode) {
            mainHandler.removeCallbacks(refreshRunnable)
            doRefresh(activity, newConfig, "uiMode")
          }
        }
      }

      statefulActivity.method("onResume")?.hookCompat {
        after {
          val activity = thisObject as? Activity ?: return@after
          currentActivity = WeakReference(activity)
          registerListeners(activity.applicationContext)
          if (checkColorsChanged(activity)) {
            logD("DayNightRefresh: onResume detected color/theme change")
            scheduleRefresh("onResume")
          }
        }
      }
    }
  }

  private fun clearIconCache(iconCache: Any) {
    runSafe {
      val cacheClass = iconCache.javaClass
      val baseClass = cacheClass.superclass

      val cacheF = cacheClass.field("cache") ?: baseClass?.field("cache")
      cacheF?.isAccessible = true
      (cacheF?.get(iconCache) as? MutableMap<*, *>)?.clear()

      val defaultIconF = cacheClass.field("defaultIcon") ?: baseClass?.field("defaultIcon")
      defaultIconF?.isAccessible = true
      defaultIconF?.set(iconCache, null)

      val userFlagOpMapF = cacheClass.field("userFlagOpMap") ?: baseClass?.field("userFlagOpMap")
      userFlagOpMapF?.isAccessible = true
      (userFlagOpMapF?.get(iconCache) as? SparseArray<*>)?.clear()

      val iconPoolF = cacheClass.field("iconPool")
      val iconPool = iconPoolF?.get(iconCache)
      if (iconPool != null) {
        val poolF = iconPool.javaClass.field("pool")
        poolF?.isAccessible = true
        (poolF?.get(iconPool) as? Queue<*>)?.clear()
      }

      val iconDbF = cacheClass.field("iconDb") ?: baseClass?.field("iconDb")
      iconDbF?.isAccessible = true
      val iconDb = iconDbF?.get(iconCache)
      if (iconDb != null) {
        val deleteM =
          iconDb.javaClass.method("delete", String::class.java, Array<String>::class.java)
        if (deleteM != null) {
          deleteM.isAccessible = true
          deleteM.invoke(iconDb, null, null)
        } else {
          val mOpenHelperF = iconDb.javaClass.field("mOpenHelper")
          mOpenHelperF?.isAccessible = true
          val helper = mOpenHelperF?.get(iconDb)
          if (helper is SQLiteOpenHelper) {
            val db = helper.writableDatabase
            db.delete("icons", null, null)
          }
        }
      }
      logD("DayNightRefresh: cleared IconCache memory and SQLite cache")
    }
  }
}
