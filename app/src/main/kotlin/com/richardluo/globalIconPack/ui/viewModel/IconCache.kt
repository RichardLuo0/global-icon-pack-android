package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.richardluo.globalIconPack.iconPack.CopyableIconPack
import com.richardluo.globalIconPack.utils.IconPackCreator.IconEntryWithPack
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.getOrPut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IconCache(private val context: Application) {
  private val iconPackCache = mutableMapOf<String, CopyableIconPack>()

  fun getIconPack(pack: String) =
    iconPackCache.getOrPut(pack) {
      CopyableIconPack(
        WorldPreference.getPrefInApp(context),
        pack,
        context.packageManager.getResourcesForApplication(pack),
      )
    }

  private val imageCache = LruCache<String, ImageBitmap>(4 * 1024 * 1024)

  suspend fun loadIcon(entry: IconEntryWithPack?, app: String, basePack: String) =
    if (entry != null)
      imageCache.getOrPut("${entry.pack}/${entry.entry.name}") {
        withContext(Dispatchers.Default) {
          (getIconPack(entry.pack).getIcon(entry.entry, 0)
              ?: context.packageManager.getApplicationIcon(app))
            .toBitmap()
            .asImageBitmap()
        }
      }
    else
      imageCache.getOrPut("$basePack/fallback/$app") {
        withContext(Dispatchers.Default) {
          getIconPack(basePack)
            .genIconFrom(context.packageManager.getApplicationIcon(app))
            .toBitmap()
            .asImageBitmap()
        }
      }

  suspend fun loadIcon(drawableName: String, pack: String) =
    imageCache.getOrPut("$pack/$drawableName") {
      withContext(Dispatchers.Default) {
        getIconPack(pack).getIcon(drawableName, 0)?.toBitmap()?.asImageBitmap() ?: ImageBitmap(1, 1)
      }
    }

  fun invalidate() {
    iconPackCache.clear()
    imageCache.evictAll()
  }
}
