package com.richardluo.globalIconPack.ui.viewModel

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.richardluo.globalIconPack.iconPack.IconFallback
import com.richardluo.globalIconPack.iconPack.IconPackConfig
import com.richardluo.globalIconPack.iconPack.database.IconEntry
import com.richardluo.globalIconPack.ui.model.AppIconInfo
import com.richardluo.globalIconPack.ui.model.IconEntryWithPack
import com.richardluo.globalIconPack.ui.model.IconInfo
import com.richardluo.globalIconPack.ui.model.IconPack
import com.richardluo.globalIconPack.ui.model.ShortcutIconInfo
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.getOrPut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class BitmapLruCache<K : Any>(bytes: Long) :
  LruCache<K, ImageBitmap>((bytes / 1024).toInt()) {
  override fun sizeOf(key: K, value: ImageBitmap): Int =
    value.asAndroidBitmap().allocationByteCount / 1024
}

class IconCache(private val context: Context, factor: Double = 1.0 / 8) {
  private val bitmapCache =
    BitmapLruCache<String>((Runtime.getRuntime().maxMemory() * factor).toLong())

  suspend fun loadIcon(
    info: IconInfo,
    entry: IconEntryWithPack?,
    basePack: IconPack,
    config: IconPackConfig,
  ): ImageBitmap {
    return if (entry != null) loadIcon(entry.entry, entry.pack)
    else
      bitmapCache.getOrPut("${basePack.pack}/fallback/${info.componentName}") {
        getBaseIcon(info)?.let { basePack.genIconFrom(it, config).toSafeBitmap().asImageBitmap() }
          ?: emptyImageBitmap
      }
  }

  suspend fun loadIcon(
    info: IconInfo,
    iconFallback: IconFallback?,
    basePack: IconPack,
    config: IconPackConfig,
  ) =
    bitmapCache.getOrPut("${basePack.pack}/fallback/${info.componentName}") {
      getBaseIcon(info)?.let {
        IconPack.genIconFrom(basePack.res, it, iconFallback, config).toSafeBitmap().asImageBitmap()
      } ?: emptyImageBitmap
    }

  suspend fun loadIcon(entry: IconEntry, pack: IconPack) =
    bitmapCache.getOrPut("${pack.pack}/icon/${entry.name}") {
      withContext(Dispatchers.IO) {
        pack.getIcon(entry, 0)?.toSafeBitmap()?.asImageBitmap() ?: emptyImageBitmap
      }
    }

  private suspend fun getBaseIcon(info: IconInfo) =
    withContext(Dispatchers.Default) {
      when (info) {
        is AppIconInfo -> context.packageManager.getApplicationIcon(info.info)
        is ShortcutIconInfo ->
          context
            .getSystemService(Context.LAUNCHER_APPS_SERVICE)
            .asType<LauncherApps>()
            ?.getShortcutIconDrawable(info.info, 0)
        else -> null
      }
    }

  fun clear() {
    bitmapCache.evictAll()
  }
}

private fun Drawable.toSafeBitmap(maxWidth: Int = 192, maxHeight: Int = 192) =
  if (intrinsicWidth !in 1..maxWidth || intrinsicHeight !in 1..maxHeight) {
    toBitmap(maxWidth, maxHeight)
  } else toBitmap()

val emptyImageBitmap by lazy { ImageBitmap(1, 1) }
