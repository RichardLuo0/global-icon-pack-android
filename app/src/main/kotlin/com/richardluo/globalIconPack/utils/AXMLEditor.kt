package com.richardluo.globalIconPack.utils

import android.os.Build
import java.io.DataInputStream
import java.io.InputStream

private const val WORD_START_DOCUMENT: Int = 0x00080003
private const val WORD_STRING_TABLE: Int = 0x001C0001
private const val WORD_RES_TABLE: Int = 0x00080180
private const val WORD_START_NS = 0x00100100
private const val WORD_END_NS = 0x00100101
private const val WORD_START_TAG = 0x00100102
private const val WORD_END_TAG = 0x00100103
private const val WORD_TEXT = 0x00100104
private const val WORD_EOS = 0xFFFFFFFF.toInt()
private const val WORD_SIZE: Int = 4

private const val TYPE_ID_REF = 0x01000008

// https://github.com/xgouchet/AXML/blob/master/library/src/main/java/fr/xgouchet/axml/CompressedXmlParser.java
class AXMLEditor(input: InputStream) {
  private val data: ByteArray

  init {
    input.use {
      data =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) it.readAllBytes()
        else {
          ByteArray(it.available().coerceAtMost(8192)).also { bytes ->
            val dataInputStream = DataInputStream(it)
            dataInputStream.readFully(bytes)
          }
        }
    }
  }

  companion object {
    fun isAXML(input: InputStream): Boolean {
      val data = ByteArray(WORD_SIZE)
      input.mark(WORD_SIZE)
      input.read(data)
      input.reset()
      val word =
        (((data[3].toInt() shl 24) and -0x1000000) or
          ((data[2].toInt() shl 16) and 0x00ff0000) or
          ((data[1].toInt() shl 8) and 0x0000ff00) or
          ((data[0].toInt() shl 0) and 0x000000ff))
      return word == WORD_START_DOCUMENT
    }
  }

  fun replaceResourceId(replace: (Int) -> Int?) {
    var parserOffset = 2 * WORD_SIZE
    while (parserOffset + 3 < data.size) {
      val word = getLEWord(parserOffset)
      when (word) {
        WORD_STRING_TABLE -> parserOffset += getLEWord(parserOffset + 1 * WORD_SIZE)
        WORD_RES_TABLE -> parserOffset += getLEWord(parserOffset + 1 * WORD_SIZE)
        WORD_START_NS,
        WORD_END_NS -> parserOffset += 6 * WORD_SIZE
        WORD_START_TAG -> {
          val attrCount = getLEShort(parserOffset + 7 * WORD_SIZE)
          parserOffset += 9 * WORD_SIZE
          for (i in 0 until attrCount) {
            val attrType = getLEWord(parserOffset + 3 * WORD_SIZE)
            val attrDataOffset = parserOffset + 4 * WORD_SIZE
            if (attrType == TYPE_ID_REF)
              replace(getLEWord(attrDataOffset))?.let { setLEWord(attrDataOffset, it) }
            parserOffset += 5 * WORD_SIZE
          }
        }
        WORD_END_TAG -> parserOffset += 6 * WORD_SIZE
        WORD_TEXT -> parserOffset += 7 * WORD_SIZE
        WORD_EOS -> break
        else -> parserOffset += WORD_SIZE
      }
    }
  }

  fun toStream() = data.inputStream()

  private fun getLEShort(off: Int): Int {
    return (((data[off + 1].toInt() shl 8) and 0xff00) or
      ((data[off + 0].toInt() shl 0) and 0x00ff))
  }

  private fun getLEWord(off: Int) =
    (((data[off + 3].toInt() shl 24) and -0x1000000) or
      ((data[off + 2].toInt() shl 16) and 0x00ff0000) or
      ((data[off + 1].toInt() shl 8) and 0x0000ff00) or
      ((data[off + 0].toInt() shl 0) and 0x000000ff))

  private fun setLEWord(off: Int, value: Int) {
    data[off + 0] = (value and 0x000000ff).toByte()
    data[off + 1] = ((value shr 8) and 0x000000ff).toByte()
    data[off + 2] = ((value shr 16) and 0x000000ff).toByte()
    data[off + 3] = ((value shr 24) and 0x000000ff).toByte()
  }
}
