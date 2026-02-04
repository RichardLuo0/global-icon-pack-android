package com.richardluo.globalIconPack.iconPack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.ui.MyApplication
import com.richardluo.globalIconPack.ui.viewModel.IconPackCache
import com.richardluo.globalIconPack.utils.SingletonManager.get
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.runCatchingToastOnMain
import kotlin.getValue

class IconPackUpdateReceiver : BroadcastReceiver() {
  private val iconPackDB by get { IconPackDB(MyApplication.context) }
  private val iconPackCache by get { IconPackCache(MyApplication.context) }

  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action ?: return
    val pack = intent.data?.schemeSpecificPart ?: return

    when (action) {
      Intent.ACTION_PACKAGE_REPLACED -> {
        if (pack == WorldPreference.get().get(Pref.ICON_PACK))
          runCatchingToastOnMain(context) { iconPackDB.onIconPackChange(iconPackCache[pack]) }
      }
    }
  }
}
