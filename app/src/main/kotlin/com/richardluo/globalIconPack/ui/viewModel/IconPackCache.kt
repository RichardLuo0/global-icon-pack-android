package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import androidx.collection.lruCache
import com.richardluo.globalIconPack.ui.model.IconPack

class IconPackCache(private val context: Application) {
  private val iconPackCache =
    lruCache<String, IconPack>(
      8,
      create = { IconPack(it, context.packageManager.getResourcesForApplication(it)) },
    )

  operator fun get(pack: String) = iconPackCache[pack]!!

  fun delete(pack: String) {
    iconPackCache.remove(pack)
  }
}
