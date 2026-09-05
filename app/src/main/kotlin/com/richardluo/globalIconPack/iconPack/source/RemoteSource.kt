package com.richardluo.globalIconPack.iconPack.source

import android.app.Application
import android.content.ComponentName
import android.graphics.drawable.Drawable
import androidx.core.database.getIntOrNull
import com.richardluo.globalIconPack.iconPack.IconPackDB.GetIconCol
import com.richardluo.globalIconPack.iconPack.IconPackProvider
import com.richardluo.globalIconPack.iconPack.model.FallbackSettings
import com.richardluo.globalIconPack.iconPack.model.IconEntry
import com.richardluo.globalIconPack.iconPack.model.IconFallback
import com.richardluo.globalIconPack.iconPack.model.IconPackConfig
import com.richardluo.globalIconPack.iconPack.model.IconResolver
import com.richardluo.globalIconPack.iconPack.model.ResourceOwner
import com.richardluo.globalIconPack.iconPack.model.defaultIconPackConfig
import com.richardluo.globalIconPack.iconPack.useFirstRow
import com.richardluo.globalIconPack.iconPack.useMapToArray
import com.richardluo.globalIconPack.utils.call
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.getOrPut
import com.richardluo.globalIconPack.utils.getOrPutNullable
import com.richardluo.globalIconPack.utils.method
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.Collections

private var waitingForBootCompleted = true

private var getSystemPropertyM: Method? = null

class RemoteSource(
  val context: Application,
  xposed: XposedInterface,
  pack: String,
  config: IconPackConfig = defaultIconPackConfig,
) : Source, ResourceOwner(context, pack) {
  private val iconPackAsFallback = config.iconPackAsFallback
  private val iconFallback: IconFallback?

  private val indexMap = mutableMapOf<ComponentName, Int?>()
  private val iconEntryList = Collections.synchronizedList(mutableListOf<IconResolver>())

  private val contentResolver = context.contentResolver
  private val resourcesMap = mutableMapOf<String, ResourceOwner>()

  init {
    context(xposed) {
      if (getSystemPropertyM == null)
        getSystemPropertyM =
          classOf("android.os.SystemProperties")?.method("get", String::class.java)
      if (
        waitingForBootCompleted &&
          getSystemPropertyM?.call<String?>(null, "sys.boot_completed") != "1"
      )
        throw Exception("Boot is not completed. The remote service isn't ready.")
      waitingForBootCompleted = false

      iconFallback =
        if (config.iconFallback)
          contentResolver
            .query(IconPackProvider.FALLBACK, null, null, arrayOf(pack), null)
            ?.useFirstRow {
              IconFallback(FallbackSettings.from(it.getBlob(0)), { getIcon(it) }, config)
                .orNullIfEmpty()
            }
        else null
    }
  }

  override fun getId(cn: ComponentName) =
    synchronized(indexMap) {
      indexMap.getOrPutNullable(cn) {
        contentResolver
          .query(
            IconPackProvider.ICON,
            null,
            null,
            arrayOf(pack, iconPackAsFallback.toString(), cn.flattenToString()),
            null,
          )
          ?.useFirstRow { c ->
            val entry = IconResolver.from(c)
            val fallback = c.getIntOrNull(GetIconCol.Fallback.ordinal) == 1
            iconEntryList.add(entry)
            (iconEntryList.size - 1).also {
              if (fallback) indexMap[getComponentName(cn.packageName)] = it
            }
          }
      }
    }

  override fun getId(cnList: List<ComponentName>) =
    synchronized(indexMap) {
      indexMap.getOrPut(cnList) { misses ->
        contentResolver
          .query(
            IconPackProvider.ICON,
            null,
            null,
            arrayOf(
              pack,
              iconPackAsFallback.toString(),
              *misses.map { it.flattenToString() }.toTypedArray(),
            ),
            null,
          )
          ?.useMapToArray(misses.size) { i, c ->
            val cn = misses[i]
            if (c.getIntOrNull(GetIconCol.Fallback.ordinal) == 1 || cn.className.isEmpty()) {
              // Is fallback
              val cn = getComponentName(cn.packageName)
              if (indexMap.contains(cn)) indexMap[cn]
              else {
                iconEntryList.add(IconResolver.from(c))
                (iconEntryList.size - 1).also { indexMap[cn] = it }
              }
            } else {
              iconEntryList.add(IconResolver.from(c))
              iconEntryList.size - 1
            }
          } ?: arrayOfNulls(misses.size)
      }
    }

  override fun getIconEntry(id: Int): IconEntry? = iconEntryList.getOrNull(id)

  context(xposed: XposedInterface)
  override fun getIconNotAdaptive(entry: IconEntry, iconDpi: Int) =
    if (entry is IconResolver) entry.getIcon(::getResourceOwner, iconDpi)
    else entry.getIcon { getIcon(it, iconDpi) }

  context(xposed: XposedInterface)
  override fun getIcon(name: String, iconDpi: Int) = getIconByName(name, iconDpi)

  private fun getResourceOwner(pack: String) =
    if (pack.isEmpty()) this else resourcesMap.getOrPut(pack) { ResourceOwner(context, pack) }

  override fun genIconFrom(baseIcon: Drawable) = genIconFrom(res, baseIcon, iconFallback)

  override fun maskIconFrom(baseIcon: Drawable) =
    maskIconFrom(res, baseIcon, iconFallback?.iconMasks)
}
