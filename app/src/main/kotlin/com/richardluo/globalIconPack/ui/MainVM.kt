package com.richardluo.globalIconPack.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.richardluo.globalIconPack.MODE_PROVIDER
import com.richardluo.globalIconPack.PrefKey
import com.richardluo.globalIconPack.iconPack.BootReceiver
import com.richardluo.globalIconPack.iconPack.KeepAliveService
import com.richardluo.globalIconPack.iconPack.database.IconPackDB
import com.richardluo.globalIconPack.utils.WorldPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainVM(app: Application) : AndroidViewModel(app) {
  private val context: Context
    get() = getApplication()

  private val iconPackChangeListener by lazy { IconPackDB(app) }
  private val pref by lazy { WorldPreference.getPrefInApp(app) }

  var waiting by mutableIntStateOf(0)
    private set

  suspend fun onModeChange(value: String) {
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

  suspend fun onIconPackChange(pack: String) {
    if (pref.getString(PrefKey.MODE, MODE_PROVIDER) == MODE_PROVIDER) {
      waiting++
      withContext(Dispatchers.Default) { iconPackChangeListener.onIconPackChange(pack) }
      waiting--
    }
  }
}
