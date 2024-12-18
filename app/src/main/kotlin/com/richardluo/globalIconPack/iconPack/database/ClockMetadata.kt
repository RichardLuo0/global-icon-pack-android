package com.richardluo.globalIconPack.iconPack.database

data class ClockMetadata(
  val hourLayerIndex: Int,
  val minuteLayerIndex: Int,
  val secondLayerIndex: Int,
  val defaultHour: Int,
  val defaultMinute: Int,
  val defaultSecond: Int,
)
