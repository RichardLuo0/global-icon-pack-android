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
import com.richardluo.globalIconPack.utils.ContextVM
import com.richardluo.globalIconPack.utils.getInstance
import com.richardluo.globalIconPack.utils.runCatchingToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import me.zhanghai.compose.preference.Preferences

class MainVM(context: Application) : ContextVM(context) {
  private val iconPackDB by getInstance { IconPackDB(context) }
  // Hold a strong reference to icon pack cache so it never gets recycled before MainVM is destroyed
  private val iconPackCache = getInstance { IconPackCache(context) }.value

  var waiting by mutableIntStateOf(0)
    private set

  @OptIn(ExperimentalCoroutinesApi::class)
  fun bindPrefFlow(flow: Flow<Preferences>) {
    flow
      .mapLatest { Pair(it.get(Pref.MODE), it.get(Pref.ICON_PACK)) }
      .distinctUntilChanged()
      .onEach { (mode, pack) ->
        waiting++
        when (mode) {
          MODE_PROVIDER -> {
            KeepAliveService.startForeground(context)
            // Enable boot receiver
            context.packageManager.setComponentEnabledSetting(
              ComponentName(context, BootReceiver::class.java),
              PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
              PackageManager.DONT_KILL_APP,
            )
            runCatchingToast(context) {
              // Reload pack from icon pack
              iconPackCache.delete(pack)
              iconPackDB.onIconPackChange(iconPackCache.get(pack))
            }
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
        waiting--
      }
      .flowOn(Dispatchers.IO)
      .launchIn(viewModelScope)
  }
}
