package com.richardluo.globalIconPack.iconPack.source

import android.app.Application
import android.content.ComponentName
import android.content.ContentProviderClient
import android.database.Cursor
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.RemoteException
import androidx.core.database.getIntOrNull
import com.richardluo.globalIconPack.iconPack.IconPackDB.GetIconCol
import com.richardluo.globalIconPack.iconPack.IconPackProvider
import com.richardluo.globalIconPack.iconPack.model.FallbackOwner
import com.richardluo.globalIconPack.iconPack.model.FallbackSettings
import com.richardluo.globalIconPack.iconPack.model.IconEntry
import com.richardluo.globalIconPack.iconPack.model.IconPackConfig
import com.richardluo.globalIconPack.iconPack.model.IconResolver
import com.richardluo.globalIconPack.iconPack.model.ResourceOwner
import com.richardluo.globalIconPack.iconPack.model.defaultIconPackConfig
import com.richardluo.globalIconPack.iconPack.useFirstRow
import com.richardluo.globalIconPack.iconPack.useMapToArray
import com.richardluo.globalIconPack.utils.Logger.logD
import com.richardluo.globalIconPack.utils.ThemeColorMonitor
import com.richardluo.globalIconPack.utils.call
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.getOrPut
import com.richardluo.globalIconPack.utils.getOrPutNullable
import com.richardluo.globalIconPack.utils.isInBinderCall
import com.richardluo.globalIconPack.utils.method
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.Collections

private var waitingForBootCompleted = true

private var getSystemPropertyM: Method? = null

class BootNotCompleted : Exception("Boot is not completed. The remote service isn't ready.")

private class ProviderNotReady : Exception()

class RemoteSource(
  context: Application,
  xposed: XposedInterface,
  pack: String,
  config: IconPackConfig = defaultIconPackConfig,
) : Source, ResourceOwner(context, pack) {
  private val iconPackAsFallback = config.iconPackAsFallback

  private val indexMap = mutableMapOf<ComponentName, Int?>()
  private val iconEntryList = Collections.synchronizedList(mutableListOf<IconResolver>())

  private val contentResolver = context.contentResolver
  // Keep the provider acquired, so queries don't wait for its process to start again. See getSC()
  // for why a binder thread must never wait for that.
  @Volatile private var client: ContentProviderClient? = null
  private val clientLock = Any()
  private val resourcesMap = mutableMapOf<String, ResourceOwner>()

  private val fallbackOwner: FallbackOwner

  private val themeColorMonitor = ThemeColorMonitor(context)

  init {
    context(xposed) {
      if (getSystemPropertyM == null)
        getSystemPropertyM =
          classOf("android.os.SystemProperties")?.method("get", String::class.java)
      if (
        waitingForBootCompleted &&
          getSystemPropertyM?.call<String?>(null, "sys.boot_completed") != "1"
      )
        throw BootNotCompleted()
      waitingForBootCompleted = false

      fallbackOwner =
        FallbackOwner(
          config,
          query(acquireClient(), IconPackProvider.FALLBACK, arrayOf(pack))?.useFirstRow {
            FallbackSettings.from(it.getBlob(0))
          },
        ) {
          getIcon(it)
        }
    }
  }

  override fun updateResources() {
    themeColorMonitor.update {
      res = context.packageManager.getResourcesForApplication(pack)
      resourcesMap.clear()
      fallbackOwner.rebuild()
      logD("resource updated")
    }
  }

  // Must not be called while holding indexMap, binder threads can wait on it.
  private fun acquireClient(): ContentProviderClient {
    client?.let {
      return it
    }
    if (isInBinderCall()) throw ProviderNotReady()
    return synchronized(clientLock) {
      client
        ?: contentResolver
          .acquireUnstableContentProviderClient(IconPackProvider.AUTHORITIES)
          ?.also { client = it }
        ?: throw ProviderNotReady()
    }
  }

  private fun query(
    client: ContentProviderClient,
    uri: Uri,
    selectionArgs: Array<String>,
  ): Cursor? =
    try {
      client.query(uri, null, null, selectionArgs, null)
    } catch (_: RemoteException) {
      // The provider's process died. Don't cache this miss, acquire it again on the next query.
      synchronized(clientLock) { if (this.client === client) this.client = null }
      client.close()
      throw ProviderNotReady()
    }

  override fun getId(cn: ComponentName) =
    try {
      getIdOrThrow(acquireClient(), cn)
    } catch (_: ProviderNotReady) {
      null
    }

  private fun getIdOrThrow(client: ContentProviderClient, cn: ComponentName) =
    synchronized(indexMap) {
      indexMap.getOrPutNullable(cn) {
        query(
            client,
            IconPackProvider.ICON,
            arrayOf(pack, iconPackAsFallback.toString(), cn.flattenToString()),
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
    try {
      getIdOrThrow(acquireClient(), cnList)
    } catch (_: ProviderNotReady) {
      List(cnList.size) { null }
    }

  private fun getIdOrThrow(client: ContentProviderClient, cnList: List<ComponentName>) =
    synchronized(indexMap) {
      indexMap.getOrPut(cnList) { misses ->
        query(
            client,
            IconPackProvider.ICON,
            arrayOf(
              pack,
              iconPackAsFallback.toString(),
              *misses.map { it.flattenToString() }.toTypedArray(),
            ),
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

  context(xposed: XposedInterface)
  override fun genIconFrom(baseIcon: Drawable) =
    genIconFrom(res, baseIcon, fallbackOwner.iconFallback)

  context(xposed: XposedInterface)
  override fun maskIconFrom(baseIcon: Drawable) =
    maskIconFrom(res, baseIcon, fallbackOwner.iconFallback?.iconMasks)
}
