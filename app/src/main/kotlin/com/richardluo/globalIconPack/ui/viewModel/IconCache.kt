package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import android.content.ComponentName
import android.graphics.drawable.Drawable
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.iconPack.model.IconEntry
import com.richardluo.globalIconPack.iconPack.model.IconFallback
import com.richardluo.globalIconPack.iconPack.model.IconPackConfig
import com.richardluo.globalIconPack.ui.MyApplication
import com.richardluo.globalIconPack.ui.model.ActivityCompInfo
import com.richardluo.globalIconPack.ui.model.AppCompInfo
import com.richardluo.globalIconPack.ui.model.CompInfo
import com.richardluo.globalIconPack.ui.model.IconEntryWithPack
import com.richardluo.globalIconPack.ui.model.IconPack
import com.richardluo.globalIconPack.utils.getOrPut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class BitmapLruCache<K : Any>(bytes: Long) :
  LruCache<K, ImageBitmap>((bytes / 1024).toInt()) {
  override fun sizeOf(key: K, value: ImageBitmap): Int =
    value.asAndroidBitmap().allocationByteCount / 1024
}

class IconCache(private val context: Application, factor: Int = 4) {
  private val bitmapCache = BitmapLruCache<String>(Runtime.getRuntime().maxMemory() / factor)

  fun getIcon(info: CompInfo, entry: IconEntryWithPack?, basePack: IconPack): ImageBitmap? {
    return if (entry != null) getPackIcon(entry.entry, entry.pack)
    else getFallbackIcon(info, basePack)
  }

  fun getFallbackIcon(info: CompInfo, basePack: IconPack) =
    bitmapCache["${basePack.pack}/fallback/${info.componentName}"]

  fun getPackIcon(entry: IconEntry, iconPack: IconPack) =
    bitmapCache["${iconPack.pack}/icon/${entry.name}"]

  suspend fun loadIcon(
    info: CompInfo,
    entry: IconEntryWithPack?,
    basePack: IconPack,
    config: IconPackConfig,
  ): ImageBitmap {
    return if (entry != null) loadPackIcon(entry.entry, entry.pack)
    else loadFallbackIcon(info, basePack.iconFallback, basePack, config)
  }

  suspend fun loadFallbackIcon(
    info: CompInfo,
    iconFallback: IconFallback?,
    basePack: IconPack,
    config: IconPackConfig,
  ): ImageBitmap =
    bitmapCache.getOrPut("${basePack.pack}/fallback/${info.componentName}") {
      // If ActivityIconInfo does not have an icon, return application icon directly
      if (info is ActivityCompInfo && info.info.icon == 0)
        loadFallbackIcon(
          AppCompInfo(
            ComponentName(info.componentName.packageName, ""),
            "",
            info.info.applicationInfo,
          ),
          iconFallback,
          basePack,
          config,
        )
      else
        getBaseIcon(info)?.let {
          IconPack.genIconFrom(basePack.res, it, iconFallback, config)
            .toSafeBitmap()
            .asImageBitmap()
        } ?: emptyImageBitmap
    }

  suspend fun loadPackIcon(entry: IconEntry, iconPack: IconPack) =
    bitmapCache.getOrPut("${iconPack.pack}/icon/${entry.name}") {
      withContext(Dispatchers.IO) {
        iconPack.getIcon(entry, 0)?.toSafeBitmap()?.asImageBitmap() ?: emptyImageBitmap
      }
    }

  private suspend fun getBaseIcon(info: CompInfo) =
    withContext(Dispatchers.IO) { info.getIcon(context) }

  fun clear() {
    bitmapCache.evictAll()
  }
}

private fun Drawable.toSafeBitmap(maxWidth: Int = 192, maxHeight: Int = 192) =
  if (intrinsicWidth !in 1..maxWidth || intrinsicHeight !in 1..maxHeight) {
    toBitmap(maxWidth, maxHeight)
  } else toBitmap()

val emptyImageBitmap by lazy {
  MyApplication.context.getDrawable(R.drawable.broken_image)?.toSafeBitmap()?.asImageBitmap()
    ?: ImageBitmap(1, 1)
}
