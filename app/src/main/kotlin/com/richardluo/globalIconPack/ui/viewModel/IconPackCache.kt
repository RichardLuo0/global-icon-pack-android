package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.content.ComponentCallbacks
import android.content.res.Configuration
import androidx.collection.lruCache
import com.richardluo.globalIconPack.ui.model.IconPack

class IconPackCache(private val context: Application) {
  private val iconPackCache =
    lruCache<String, IconPack>(
      8,
      create = { IconPack(context, it) },
    )

  operator fun get(pack: String) = iconPackCache[pack]!!

  fun delete(pack: String) {
    iconPackCache.remove(pack)
  }

  fun updateResource() {
    for (pack in iconPackCache.snapshot().values) pack.updateResources()
  }
}

fun createIconPackCacheWithThemeAware(context: Application) =
  IconPackCache(context).apply {
    context.registerComponentCallbacks(
      object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
          this@apply.updateResource()
        }

        @Deprecated("Deprecated in Java") override fun onLowMemory() {}
      }
    )
  }
