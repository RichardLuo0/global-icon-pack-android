package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.viewModelScope
import com.richardluo.globalIconPack.AppPref
import com.richardluo.globalIconPack.MODE_PROVIDER
import com.richardluo.globalIconPack.MODE_SHARE
import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.iconPack.BootReceiver
import com.richardluo.globalIconPack.iconPack.IconPackDB
import com.richardluo.globalIconPack.iconPack.KeepAliveService
import com.richardluo.globalIconPack.iconPack.source.ShareSource
import com.richardluo.globalIconPack.utils.AppPreference
import com.richardluo.globalIconPack.utils.ContextVM
import com.richardluo.globalIconPack.utils.InstanceManager.get
import com.richardluo.globalIconPack.utils.InstanceManager.update
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.getPreferenceFlow
import com.richardluo.globalIconPack.utils.log
import com.richardluo.globalIconPack.utils.runCatchingToast
import com.topjohnwu.superuser.Shell
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class MainVM(context: Application) : ContextVM(context) {
  private var iconPackDBLazy = get { IconPackDB(context) }
  // Hold a strong reference to icon pack cache so it never gets recycled before MainVM is destroyed
  private val iconPackCache = get { IconPackCache(context) }.value

  var waiting by mutableIntStateOf(0)
    private set

  val prefFlow =
    runCatching { WorldPreference.getPrefInApp(context) }
      .getOrNull()
      ?.getPreferenceFlow()
      ?.apply {
        @OptIn(ExperimentalCoroutinesApi::class)
        mapLatest { Pair(it.get(Pref.MODE), it.get(Pref.ICON_PACK)) }
          .distinctUntilChanged()
          .onEach { (mode, pack) ->
            waiting++
            when (mode) {
              MODE_SHARE -> {
                KeepAliveService.stopForeground(context)
                startOnBoot(false)
                enableShareMode(pack)
              }
              MODE_PROVIDER -> {
                KeepAliveService.startForeground(context)
                startOnBoot(true)
                updateDB(pack)
              }
              else -> {
                KeepAliveService.stopForeground(context)
                startOnBoot(false)
              }
            }
            waiting--
          }
          .flowOn(Dispatchers.IO)
          .launchIn(viewModelScope)
      }

  private suspend fun enableShareMode(pack: String) {
    try {
      val shareDB = ShareSource.DATABASE_PATH
      val shareDBFile = File(shareDB)
      val parent = shareDBFile.parent
      val uid = android.os.Process.myUid()
      if (!shareDBFile.exists()) {
        if (iconPackDBLazy.isInitialized()) {
          iconPackDBLazy.value.close()
          iconPackDBLazy = update { IconPackDB(context) }
        }
        val oldDB =
          context.createDeviceProtectedStorageContext().getDatabasePath(AppPref.PATH.def).path
        Shell.cmd(
            "set -e",
            "mkdir -p $parent",
            "chown $uid:$uid $parent && chmod 0775 $parent && chcon u:object_r:magisk_file:s0 $parent",
            "if [ -f $oldDB ]; then cp $oldDB $shareDB; fi",
            "if ! [ -f $shareDB ]; then touch $shareDB; fi",
            "if [ -f $oldDB ]; then rm $oldDB; fi",
          )
          .exec()
          .run {
            if (!isSuccess)
              throw Exception(
                "Shared database creation failed: code: $code err: ${err.joinToString("\n")} out: ${out.joinToString("\n")}"
              )
          }
      }
      Shell.cmd(
          "set -e",
          "chown $uid:$uid $parent && chmod 0775 $parent && chcon u:object_r:magisk_file:s0 $parent",
          "chown $uid:$uid $shareDB && chmod 0666 $shareDB && chcon u:object_r:magisk_file:s0 $shareDB",
        )
        .exec()
        .run {
          if (!isSuccess)
            throw Exception(
              "Database permission setting failed: code: $code err: ${err.joinToString("\n")} out: ${out.joinToString("\n")}"
            )
        }
      AppPreference.get(context).edit { putString(AppPref.PATH.key, shareDB) }
      updateDB(pack)
    } catch (t: Throwable) {
      log(t)
      withContext(Dispatchers.Main) {
        Toast.makeText(context, R.string.errorOnShareMode, Toast.LENGTH_LONG).show()
      }
      prefFlow?.update { it.toMutablePreferences().apply { set(Pref.MODE.key, MODE_PROVIDER) } }
    }
  }

  private fun startOnBoot(enable: Boolean = true) {
    context.packageManager.setComponentEnabledSetting(
      ComponentName(context, BootReceiver::class.java),
      if (enable) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP,
    )
  }

  private suspend fun updateDB(pack: String) {
    runCatchingToast(context) {
      if (pack.isEmpty()) return
      iconPackCache.delete(pack)
      iconPackDBLazy.value.onIconPackChange(iconPackCache[pack], IconPackApps.get().keys)
    }
  }
}
