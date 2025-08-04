package com.richardluo.globalIconPack.utils

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

open class ContextVM(app: Application) : AndroidViewModel(app) {
  protected val context: Context
    get() = getApplication()
}

interface ILoadable {
  val loading: Int

  suspend fun <T> runLoading(block: suspend () -> T)

  fun <T> launchLoading(scope: CoroutineScope, block: suspend () -> T)
}

class Loadable : ILoadable {
  override var loading by mutableIntStateOf(0)

  override suspend fun <T> runLoading(block: suspend () -> T) {
    loading++
    runCatching { block() }
    loading--
  }

  override fun <T> launchLoading(scope: CoroutineScope, block: suspend () -> T) {
    scope.launch { runLoading(block) }
  }
}
