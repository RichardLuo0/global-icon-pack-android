package com.richardluo.globalIconPack.utils

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel

open class ContextVM(app: Application) : AndroidViewModel(app) {
  protected val context: Context
    get() = getApplication()
}
