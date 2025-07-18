package com.richardluo.globalIconPack.ui.viewModel

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.richardluo.globalIconPack.utils.ContextVM

class AutoFillVM(context: Application) : ContextVM(context) {
  val dialog = mutableStateOf(false)

  var basePack: String = ""
    private set

  val packs = mutableStateListOf<String>()

  fun open(basePack: String) {
    this.basePack = basePack
    dialog.value = true
  }

  fun addPack(pack: String) {
    packs.add(pack)
  }

  fun removePack(index: Int) {
    packs.removeAt(index)
  }

  fun movePack(from: Int, to: Int) {
    packs.run { add(to, removeAt(from)) }
  }
}
