package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.richardluo.globalIconPack.MODE_PROVIDER
import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.iconPack.BootReceiver
import com.richardluo.globalIconPack.iconPack.KeepAliveService
import com.richardluo.globalIconPack.iconPack.database.IconPackDB
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.getInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import me.zhanghai.compose.preference.Preferences

class MainVM(app: Application) : AndroidViewModel(app) {
  private val context: Context
    get() = getApplication()

  private val iconPackDB by getInstance { IconPackDB(app) }

  // Hold a strong reference to icon cache so it never gets recycled before MainVM is destroyed
  private val iconCache = getInstance { IconCache(app) }.value

  var waiting by mutableIntStateOf(0)
    private set

  @OptIn(ExperimentalCoroutinesApi::class)
  fun bindPreferencesFlow(flow: Flow<Preferences>) {
    flow
      .mapLatest { Pair(it.get(Pref.MODE), it.get(Pref.ICON_PACK)) }
      .distinctUntilChanged()
      .onEach { (mode, pack) ->
        waiting++
        withContext(Dispatchers.IO) {
          when (mode) {
            MODE_PROVIDER -> {
              KeepAliveService.startForeground(context)
              // Enable boot receiver
              context.packageManager.setComponentEnabledSetting(
                ComponentName(context, BootReceiver::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
              )
              iconPackDB.onIconPackChange(pack)
            }
            else -> {
              KeepAliveService.stopForeground(context)
              context.packageManager.setComponentEnabledSetting(
                ComponentName(context, BootReceiver::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
              )
            }
          }
        }
        waiting--
      }
      .launchIn(viewModelScope)

    // Invalidate icon cache when pref changes
    data class CachePref(
      val iconFallback: Boolean,
      val overrideIconFallback: Boolean,
      val iconPackScale: Float,
      val scaleOnlyForeground: Boolean,
    )
    flow
      .mapLatest {
        CachePref(
          it.get(Pref.ICON_FALLBACK),
          it.get(Pref.OVERRIDE_ICON_FALLBACK),
          it.get(Pref.ICON_PACK_SCALE),
          it.get(Pref.SCALE_ONLY_FOREGROUND),
        )
      }
      .distinctUntilChanged()
      .onEach { iconCache.invalidate() }
      .launchIn(viewModelScope)
  }
}
