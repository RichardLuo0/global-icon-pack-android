package com.richardluo.globalIconPack.utils

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras

class ViewModelFactoryWithExtras : ViewModelProvider.Factory {

  object ApplicationKey : CreationExtras.Key<Application>

  override fun <T : ViewModel> create(modelClass: Class<T>): T =
    modelClass.getConstructor().newInstance()

  override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
    modelClass.getConstructor(CreationExtras::class.java).newInstance(extras)
}

inline fun <reified VM : ViewModel> ComponentActivity.viewModels(extras: MutableCreationExtras) =
  viewModels<VM>(
    factoryProducer = { ViewModelFactoryWithExtras() },
    extrasProducer = {
      extras[ViewModelFactoryWithExtras.ApplicationKey] = application
      extras
    },
  )

class Block<T>(private val data: T) {
  var refCount = 0

  fun get() = data
}

private val viewModelStore = mutableMapOf<Class<*>, Block<*>>()

@Suppress("UNCHECKED_CAST")
class ViewModelRef<T : ViewModel>(extras: CreationExtras) : ViewModel() {
  object ClassKey : CreationExtras.Key<Class<*>>

  private val clazz = extras[ClassKey] as Class<T>
  private var block: Block<T> =
    viewModelStore.getOrPut(clazz) { Block<T>(clazz.getConstructor().newInstance()) } as Block<T>

  init {
    block.refCount++
  }

  fun get() = block.get()

  override fun onCleared() {
    block.refCount--
    if (block.refCount == 0) viewModelStore.remove(clazz)
  }
}

inline fun <reified VM : ViewModel> ComponentActivity.shareViewModels() =
  viewModels<ViewModelRef<VM>>(
      MutableCreationExtras().apply { set(ViewModelRef.ClassKey, VM::class.java) }
    )
    .let {
      object : Lazy<VM> {
        override val value: VM
          get() = it.value.get()

        override fun isInitialized() = it.isInitialized()
      }
    }
