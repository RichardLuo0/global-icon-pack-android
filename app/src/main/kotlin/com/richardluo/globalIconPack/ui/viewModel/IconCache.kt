package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.richardluo.globalIconPack.iconPack.CopyableIconPack
import com.richardluo.globalIconPack.iconPack.IconPack
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.utils.IconPackCreator
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.asType
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

  private val imageCache =
    object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
      override fun sizeOf(key: String, value: ImageBitmap) =
        value.asAndroidBitmap().allocationByteCount / 1024
    }

  suspend fun loadIcon(
    appIconInfo: AppIconInfo,
    entry: IconPackCreator.IconEntryWithPack?,
    basePack: String,
  ): ImageBitmap {
    return if (entry != null) loadIcon(entry.entry, entry.pack)
    else if (appIconInfo is ShortcutIconInfo) loadIcon(appIconInfo, basePack)
    else loadIcon(appIconInfo, basePack)
  }

  suspend fun loadIcon(info: AppIconInfo, basePack: String) =
    imageCache.getOrPut("$basePack/fallback/${info.componentName}") {
      withContext(Dispatchers.Default) {
        getIconPack(basePack)
          .genIconFrom(context.packageManager.getApplicationIcon(info.componentName.packageName))
          .toSafeBitmap(300, 300)
          .asImageBitmap()
      }
    }

  suspend fun loadIcon(info: ShortcutIconInfo, basePack: String) =
    imageCache.getOrPut("$basePack/shortcut/${info.componentName}") {
      withContext(Dispatchers.IO) {
        getIconPack(basePack)
          .genIconFrom(
            context
              .getSystemService(Context.LAUNCHER_APPS_SERVICE)
              .asType<LauncherApps>()
              .getShortcutIconDrawable(info.shortcut, 0)
          )
          .toSafeBitmap(300, 300)
          .asImageBitmap()
      }
    }

  suspend fun loadIcon(entry: IconEntry, pack: IconPack) =
    imageCache.getOrPut("$pack/icon/${entry.name}") {
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
