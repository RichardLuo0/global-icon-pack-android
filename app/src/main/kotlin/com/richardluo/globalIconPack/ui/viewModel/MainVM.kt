package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
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
import com.richardluo.globalIconPack.ui.repo.IconPackApps
import com.richardluo.globalIconPack.utils.AppPreference
import com.richardluo.globalIconPack.utils.ContextVM
import com.richardluo.globalIconPack.utils.ILoadable
import com.richardluo.globalIconPack.utils.Loadable
import com.richardluo.globalIconPack.utils.SingletonManager.get
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.getPreferenceFlow
import com.richardluo.globalIconPack.utils.runCatchingToast
import com.richardluo.globalIconPack.utils.throwOnFail
import com.topjohnwu.superuser.Shell
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

class MainVM(context: Application) : ContextVM(context), ILoadable by Loadable() {
  private val iconPackDB by get { IconPackDB(context) }
  // Hold a strong reference to icon pack cache so it never gets recycled before MainVM is destroyed
  private val iconPackCache = get { IconPackCache(context) }.value

  val prefFlow = runCatching { WorldPreference.get() }.getOrNull()?.getPreferenceFlow()

  init {
    prefFlow?.run {
      map { Pair(it.get(Pref.MODE), it.get(Pref.ICON_PACK)) }
        .distinctUntilChanged()
        .onEach { (mode, pack) ->
          runLoading {
            when (mode) {
              MODE_SHARE -> {
                KeepAliveService.stopForeground(context)
                startOnBoot(false)
                runCatchingToast(
                  context,
                  { context.getString(R.string.general_error_onShareMode) },
                  {
                    update { it.toMutablePreferences().apply { set(Pref.MODE.key, MODE_PROVIDER) } }
                  },
                ) {
                  val shareDB = createShareDB()
                  resetDBPermission(shareDB)
                  updateDB(pack)
                  AppPreference.get().edit { putString(AppPref.PATH.key, shareDB) }
                }
              }
              MODE_PROVIDER -> {
                KeepAliveService.startForeground(context)
                startOnBoot(true)
                runCatchingToast(
                  context,
                  onFailure = {
                    // Revert to default database
                    iconPackDB.migrate(AppPref.PATH.def) {}
                    AppPreference.get().edit { remove(AppPref.PATH.key) }
                  },
                ) {
                  resetDBPermission(AppPreference.get().get(AppPref.PATH))
                }
                updateDB(pack)
              }
              else -> {
                KeepAliveService.stopForeground(context)
                startOnBoot(false)
              }
            }
          }
        }
        .flowOn(Dispatchers.IO)
        .launchIn(viewModelScope)
    }
  }

  private fun createShareDB(): String {
    val shareDB =
      AppPreference.get().get(AppPref.PATH).takeIf { it.isShareDB() } ?: ShareSource.DATABASE_PATH
    val shareDBFile = File(shareDB)
    val parent = shareDBFile.parent
    if (!shareDBFile.exists()) {
      iconPackDB.migrate(shareDB) { oldDBFile ->
        val oldDB = oldDBFile!!.path
        Shell.cmd(
            "set -e",
            "mkdir -p $parent",
            "if [ -f $oldDB ]; then cp $oldDB $shareDB; fi",
            "if ! [ -f $shareDB ]; then touch $shareDB; fi",
            "if [ -f $oldDB ]; then rm $oldDB; fi",
          )
          .exec()
          .throwOnFail("Error when creating share DB")
      }
    }
    return shareDB
  }

  private fun resetDBPermission(db: String) {
    if (!db.isShareDB()) return
    val parent = File(db).parent!!
    if (isAllFilesUsable(parent)) return

    // Make sure the file exists and try to get its selinux context
    AppPreference.get().edit(commit = true) {}
    val prefPath = AppPreference.getFile()?.takeIf { it.exists() }?.path ?: ""

    val uid = android.os.Process.myUid()
    Shell.cmd(
        "set -e",
        "if ! [ -f $db ]; then touch $db; fi",
        "[ -n \"$prefPath\" ] && context=$(ls -Z $prefPath | cut -d: -f1-4) || context=\"u:object_r:magisk_file:s0\"",
        "chown $uid:$uid $parent && chmod 0777 $parent && chcon \$context $parent",
        "for file in $parent/*; do chown $uid:$uid \$file && chmod 0666 \$file && chcon \$context \$file; done",
      )
      .exec()
      .throwOnFail("Database permission setting failed")
    // Check again
    if (!isAllFilesUsable(parent))
      throw Exception("Unable to read and write after resetting permission!")
  }

  private fun isAllFilesUsable(folder: String) =
    File(folder).listFiles()?.all { it.canRead() && it.canWrite() } == true

  private fun String.isShareDB() = startsWith(File.separatorChar)

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
      iconPackDB.onIconPackChange(iconPackCache[pack], IconPackApps.get().keys)
    }
  }
}
