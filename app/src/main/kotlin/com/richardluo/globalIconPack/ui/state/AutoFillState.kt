package com.richardluo.globalIconPack.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateList

class AutoFillState(open: Boolean = false, basePack: String = "", packs: List<String> = listOf()) {
  val dialog = mutableStateOf(open)

  var basePack: String = basePack
    private set

  val packs = packs.toMutableStateList()

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

@Suppress("UNCHECKED_CAST")
@Composable
fun rememberAutoFillState() =
  rememberSaveable(
    saver =
      listSaver(
        save = { listOf(it.dialog.value, it.basePack, ArrayList(it.packs.toList())) },
        restore = { AutoFillState(it[0] as Boolean, it[1] as String, it[2] as ArrayList<String>) },
      )
  ) {
    AutoFillState()
  }
