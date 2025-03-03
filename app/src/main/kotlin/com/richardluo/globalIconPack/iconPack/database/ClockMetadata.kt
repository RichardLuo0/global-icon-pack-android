package com.richardluo.globalIconPack.iconPack.database

import java.io.Serializable

data class ClockMetadata(
  val hourLayerIndex: Int,
  val minuteLayerIndex: Int,
  val secondLayerIndex: Int,
  val defaultHour: Int,
  val defaultMinute: Int,
  val defaultSecond: Int,
) : Serializable {

  companion object {
    private const val serialVersionUID = 1L
  }
}
