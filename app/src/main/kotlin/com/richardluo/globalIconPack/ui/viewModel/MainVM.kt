package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.annotation.Keep
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import me.zhanghai.compose.preference.Preferences

class MainVM(app: Application) : ContextVM(app) {
  private val iconPackDB by getInstance { IconPackDB(app) }

  // Hold a strong reference to icon pack cache so it never gets recycled before MainVM is destroyed
  @Keep @Suppress("unused") private val iconPackCache = getInstance { IconPackCache(app) }.value

  var waiting by mutableIntStateOf(0)
    private set

  @OptIn(ExperimentalCoroutinesApi::class)
  fun bindPrefFlow(flow: Flow<Preferences>) {
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
  }
}
