package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import androidx.collection.LruCache
import com.richardluo.globalIconPack.ui.model.IconPack
import com.richardluo.globalIconPack.utils.getOrPut

class IconPackCache(private val context: Application) {
  private val iconPackCache = LruCache<String, IconPack>(8)

  fun getIconPack(pack: String) =
    iconPackCache.getOrPut(pack) {
      IconPack(pack, context.packageManager.getResourcesForApplication(pack))
    }
}
