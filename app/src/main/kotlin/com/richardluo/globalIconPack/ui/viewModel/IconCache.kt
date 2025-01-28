package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.graphics.drawable.Drawable
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.richardluo.globalIconPack.iconPack.CopyableIconPack
import com.richardluo.globalIconPack.iconPack.IconPack
import com.richardluo.globalIconPack.iconPack.database.IconEntry
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
    if (entry != null) loadIcon(entry.entry, entry.pack)
    else
      imageCache.getOrPut("$basePack/fallback/$app") {
        withContext(Dispatchers.Default) {
          getIconPack(basePack)
            .genIconFrom(context.packageManager.getApplicationIcon(app))
            .toSafeBitmap(300, 300)
            .asImageBitmap()
        }
      }

  suspend fun loadIcon(entry: IconEntry, pack: IconPack) =
    imageCache.getOrPut("$pack/${entry.name}") {
      withContext(Dispatchers.IO) {
        pack.getIcon(entry, 0)?.toSafeBitmap(300, 300)?.asImageBitmap() ?: ImageBitmap(1, 1)
      }
    }

  fun invalidate() {
    iconPackCache.clear()
    imageCache.evictAll()
  }
}

private fun Drawable.toSafeBitmap(maxWidth: Int, maxHeight: Int) =
  if (intrinsicWidth >= maxWidth && intrinsicHeight >= maxHeight) {
    toBitmap(maxWidth, maxHeight)
  } else toBitmap()
