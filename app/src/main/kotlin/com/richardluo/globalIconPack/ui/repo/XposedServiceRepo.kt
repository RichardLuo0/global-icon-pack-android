package com.richardluo.globalIconPack.ui.repo

import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object XposedServiceRepo {
  private val _service = MutableStateFlow<XposedService?>(null)
  val service: StateFlow<XposedService?> = _service.asStateFlow()

  private var registered = false

  fun register() {
    if (registered) return
    registered = true
    XposedServiceHelper.registerListener(
      object : XposedServiceHelper.OnServiceListener {
        override fun onServiceBind(service: XposedService) {
          _service.value = service
        }

        override fun onServiceDied(service: XposedService) {
          _service.value = null
        }
      }
    )
  }
}
