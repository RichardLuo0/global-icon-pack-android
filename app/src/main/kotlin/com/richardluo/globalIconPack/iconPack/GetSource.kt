package com.richardluo.globalIconPack.iconPack

import android.content.ComponentCallbacks
import android.content.res.Configuration
import com.richardluo.globalIconPack.MODE_PROVIDER
import com.richardluo.globalIconPack.MODE_SHARE
import com.richardluo.globalIconPack.Pref
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.iconPack.model.IconPackConfig
import com.richardluo.globalIconPack.iconPack.source.BootNotCompleted
import com.richardluo.globalIconPack.iconPack.source.EmptySource
import com.richardluo.globalIconPack.iconPack.source.LocalSource
import com.richardluo.globalIconPack.iconPack.source.RemoteSource
import com.richardluo.globalIconPack.iconPack.source.ShareSource
import com.richardluo.globalIconPack.iconPack.source.Source
import com.richardluo.globalIconPack.reflect.ActivityThread
import com.richardluo.globalIconPack.utils.Logger.log
import com.richardluo.globalIconPack.utils.Logger.logE
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.isInBinderCall
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

@Volatile private var sc: Source? = null
private val initializingAsync = AtomicBoolean(false)
// `Source::class` creates a new KClass wrapper on every use, so it can't serve as a lock.
private val initLock = Any()

context(xposed: XposedInterface)
fun getSC(): Source? {
  sc?.let {
    return it
  }
  // Creating a source may wait for IconPackProvider's process to start. A binder thread must not
  // wait for it (or for the lock below): if it is handling a oneway call to ApplicationThread, the
  // system's notifyContentProviderPublishStatus() is queued behind that call, so the wait never
  // ends and the process freezes for 20s (a SystemUI ANR on every boot). Initialize in the
  // background instead and skip this call.
  // The same applies inside ActivityThread.acquireProvider(): it holds the provider's lock while
  // unparceling ProviderInfo, and creating a source reads Settings (via SQLiteDatabase), which
  // can deadlock with a thread that holds a Settings lock and waits for the same provider.
  if (isInBinderCall() || isAcquiringProvider()) {
    if (initializingAsync.compareAndSet(false, true))
      thread(name = "GIP-InitSource") {
        try {
          synchronized(initLock) { if (sc == null) initSC() }
        } finally {
          initializingAsync.set(false)
        }
      }
    return null
  }
  synchronized(initLock) {
    if (sc == null) {
      initSC()
    }
  }
  return sc
}

private fun isAcquiringProvider() =
  Thread.currentThread().stackTrace.any {
    it.methodName == "acquireProvider" && it.className == "android.app.ActivityThread"
  }

context(xposed: XposedInterface)
private fun initSC() {
  val context = ActivityThread.currentApplication() ?: return
  runCatching {
    val pref = WorldPreference.get()
    val pack = pref.get(Pref.ICON_PACK)
    val config = IconPackConfig(pref)
    sc =
      if (pack.isEmpty()) {
        log("No icon pack is set")
        EmptySource()
      } else
        when (pref.get(Pref.MODE)) {
          MODE_SHARE -> ShareSource(context, xposed, pack, config)
          MODE_PROVIDER ->
            try {
              RemoteSource(context, xposed, pack, config)
            } catch (e: BootNotCompleted) {
              log(e.message.orEmpty())
              return
            }
          else -> LocalSource(context, xposed, pack, config)
        }
    context.registerComponentCallbacks(
      object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
          sc?.updateResources()
        }

        @Deprecated("Deprecated in Java") override fun onLowMemory() {}
      }
    )
  }
    .onFailure { logE(it) }
}
