package com.richardluo.globalIconPack.iconPack.database

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

data class FallbackSettings(
  val iconBacks: List<String>,
  val iconUpons: List<String>,
  val iconMasks: List<String>,
  val iconScale: Float = 1f,
) : Serializable {

  fun toByteArray(): ByteArray =
    ByteArrayOutputStream().also { ObjectOutputStream(it).writeObject(this) }.toByteArray()

  companion object {
    fun from(data: ByteArray) =
      ObjectInputStream(ByteArrayInputStream(data)).readObject() as FallbackSettings
  }
}
