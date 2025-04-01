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
import com.richardluo.globalIconPack.iconPack.IconPackConfig
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.ui.model.AppIconInfo
import com.richardluo.globalIconPack.ui.model.IconEntryWithPack
import com.richardluo.globalIconPack.ui.model.IconPack
import com.richardluo.globalIconPack.ui.model.ShortcutIconInfo
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.getOrPut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IconCache(private val context: Application, private val getIconPack: (String) -> IconPack) {
  private val imageCache =
    object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
      override fun sizeOf(key: String, value: ImageBitmap) =
        value.asAndroidBitmap().allocationByteCount / 1024
    }

  suspend fun loadIcon(
    appIconInfo: AppIconInfo,
    entry: IconEntryWithPack?,
    basePack: String,
    config: IconPackConfig,
  ): ImageBitmap {
    return if (entry != null) loadIcon(entry.entry, entry.pack)
    else if (appIconInfo is ShortcutIconInfo) loadIcon(appIconInfo, basePack, config)
    else loadIcon(appIconInfo, basePack, config)
  }

  suspend fun loadIcon(info: AppIconInfo, basePack: String, config: IconPackConfig) =
    imageCache.getOrPut("$basePack/fallback/${info.componentName}") {
      withContext(Dispatchers.Default) {
        getIconPack(basePack)
          .genIconFrom(
            context.packageManager.getApplicationIcon(info.componentName.packageName),
            config,
          )
          .toSafeBitmap(300, 300)
          .asImageBitmap()
      }
    }

  suspend fun loadIcon(info: ShortcutIconInfo, basePack: String, config: IconPackConfig) =
    imageCache.getOrPut("$basePack/shortcut/${info.componentName}") {
      withContext(Dispatchers.IO) {
        getIconPack(basePack)
          .genIconFrom(
            context
              .getSystemService(Context.LAUNCHER_APPS_SERVICE)
              .asType<LauncherApps>()
              .getShortcutIconDrawable(info.shortcut, 0),
            config,
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

  fun clearIcons() {
    imageCache.evictAll()
  }
}

private fun Drawable.toSafeBitmap(maxWidth: Int, maxHeight: Int) =
  if (intrinsicWidth >= maxWidth && intrinsicHeight >= maxHeight) {
    toBitmap(maxWidth, maxHeight)
  } else toBitmap()
