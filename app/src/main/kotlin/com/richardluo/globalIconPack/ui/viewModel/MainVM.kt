package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import me.zhanghai.compose.preference.Preferences

class MainVM(app: Application) : AndroidViewModel(app) {
  private val context: Context
    get() = getApplication()

  private val iconPackDB by getInstance { IconPackDB(app) }
  private val pref by lazy { WorldPreference.getPrefInApp(app) }

  // Hold a strong reference to icon cache so it never gets recycled before MainVM is destroyed
  private val iconCache = getInstance { IconCache(app) }.value

  var waiting by mutableIntStateOf(0)
    private set

  fun bindPreferencesFlow(flow: Flow<Preferences>) {
    flow
      .map { it.get(Pref.MODE) }
      .distinctUntilChanged()
      .onEach(::onModeChange)
      .launchIn(viewModelScope)
    flow
      .map { it.get(Pref.ICON_PACK) }
      .distinctUntilChanged()
      .onEach(::onIconPackChange)
      .launchIn(viewModelScope)

    data class CachePref(
      val iconFallback: Boolean,
      val overrideIconFallback: Boolean,
      val iconPackScale: Float,
    )
    flow
      .map {
        CachePref(
          it.get(Pref.ICON_FALLBACK),
          it.get(Pref.OVERRIDE_ICON_FALLBACK),
          it.get(Pref.ICON_PACK_SCALE),
        )
      }
      .distinctUntilChanged()
      .onEach { iconCache.invalidate() }
      .launchIn(viewModelScope)
  }

  private suspend fun onModeChange(value: String) {
    waiting++
    withContext(Dispatchers.Default) { changeComponent(value) }
    waiting--
  }

  private fun changeComponent(value: String) {
    when (value) {
      MODE_PROVIDER -> {
        KeepAliveService.startForeground(context)
        // Enable boot receiver
        context.packageManager.setComponentEnabledSetting(
          ComponentName(context, BootReceiver::class.java),
          PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
          PackageManager.DONT_KILL_APP,
        )
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

  private suspend fun onIconPackChange(pack: String) {
    if (pref.get(Pref.MODE) == MODE_PROVIDER) {
      waiting++
      withContext(Dispatchers.IO) { iconPackDB.onIconPackChange(pack) }
      waiting--
    }
  }
}
