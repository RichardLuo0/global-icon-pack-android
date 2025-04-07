package com.richardluo.globalIconPack.iconPack.database

import com.richardluo.globalIconPack.iconPack.IconPackInfo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

data class FallbackSettings(
  val iconBacks: List<String>,
  val iconUpons: List<String>,
  val iconMasks: List<String>,
  val iconScale: Float = 1f,
) {
  constructor(
    info: IconPackInfo
  ) : this(info.iconBacks, info.iconUpons, info.iconMasks, info.iconScale)

  fun toByteArray(): ByteArray =
    ByteArrayOutputStream()
      .also {
        DataOutputStream(it).apply {
          writeList(iconBacks) { writeUTF(it) }
          writeList(iconUpons) { writeUTF(it) }
          writeList(iconMasks) { writeUTF(it) }
          writeFloat(iconScale)
        }
      }
      .toByteArray()

  companion object {
    fun from(data: ByteArray) =
      DataInputStream(ByteArrayInputStream(data)).use {
        FallbackSettings(
          it.readList { readUTF() },
          it.readList { readUTF() },
          it.readList { readUTF() },
          it.readFloat(),
        )
      }
  }
}

private fun <T> DataOutputStream.writeList(
  value: List<T>,
  writeSingle: DataOutputStream.(T) -> Unit,
) {
  writeInt(value.size)
  value.forEach { writeSingle(it) }
}

private fun <T> DataInputStream.readList(readSingle: DataInputStream.() -> T): List<T> =
  mutableListOf<T>().apply { repeat(readInt()) { add(readSingle()) } }
