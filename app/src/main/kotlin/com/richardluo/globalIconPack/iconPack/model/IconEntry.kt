package com.richardluo.globalIconPack.iconPack.model

import android.graphics.drawable.Drawable
import android.os.Parcel
import com.richardluo.globalIconPack.iconPack.model.IconEntry.Type
import com.richardluo.globalIconPack.reflect.ClockDrawableWrapper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Calendar
import kotlinx.parcelize.Parceler

interface IconEntry {
  enum class Type {
    Normal,
    Calendar,
    Clock,
  }

  val name: String
  val type: Type

  fun getIcon(getIcon: (String) -> Drawable?): Drawable?

  fun copyTo(
    component: String,
    newName: String,
    addAppfilter: (String) -> Unit,
    copyRes: (String, String) -> Unit,
  ) {}

  fun toByteArray(): ByteArray

  companion object {
    fun from(data: ByteArray): IconEntry =
      when (data[0]) {
        Type.Normal.ordinal.toByte() -> NormalIconEntry.from(data)
        Type.Calendar.ordinal.toByte() -> CalendarIconEntry.from(data)
        Type.Clock.ordinal.toByte() -> ClockIconEntry.from(data)
        else -> throw Exception("Broken data")
      }
  }
}

class NormalIconEntry(override val name: String) : IconEntry {
  override val type = Type.Normal

  override fun getIcon(getIcon: (String) -> Drawable?) = getIcon(name)

  override fun copyTo(
    component: String,
    newName: String,
    addAppfilter: (String) -> Unit,
    copyRes: (String, String) -> Unit,
  ) {
    addAppfilter("<item component=\"${component}\" drawable=\"${newName}\"/>")
    copyRes(name, newName)
  }

  override fun toByteArray(): ByteArray =
    ByteArrayOutputStream()
      .also {
        DataOutputStream(it).use {
          it.writeByte(type.ordinal)
          it.writeUTF(name)
        }
      }
      .toByteArray()

  companion object {
    fun from(data: ByteArray) =
      DataInputStream(ByteArrayInputStream(data)).use {
        it.readByte()
        NormalIconEntry(it.readUTF())
      }
  }
}

class CalendarIconEntry(override val name: String) : IconEntry {
  override val type = Type.Calendar

  override fun getIcon(getIcon: (String) -> Drawable?) =
    getIcon("$name${Calendar.getInstance().get(Calendar.DAY_OF_MONTH)}")

  override fun copyTo(
    component: String,
    newName: String,
    addAppfilter: (String) -> Unit,
    copyRes: (String, String) -> Unit,
  ) {
    addAppfilter("<calendar component=\"${component}\" prefix=\"${newName}_\"/>")
    (1..31).mapNotNull { copyRes("$name${it}", "${newName}_$it") }
  }

  override fun toByteArray(): ByteArray =
    ByteArrayOutputStream()
      .also {
        DataOutputStream(it).use {
          it.writeByte(type.ordinal)
          it.writeUTF(name)
        }
      }
      .toByteArray()

  companion object {
    fun from(data: ByteArray) =
      DataInputStream(ByteArrayInputStream(data)).use {
        it.readByte()
        CalendarIconEntry(it.readUTF())
      }
  }
}

data class ClockMetadata(
  val hourLayerIndex: Int,
  val minuteLayerIndex: Int,
  val secondLayerIndex: Int,
  val defaultHour: Int,
  val defaultMinute: Int,
  val defaultSecond: Int,
)

class ClockIconEntry(override val name: String, private val metadata: ClockMetadata) : IconEntry {
  override val type = Type.Clock

  override fun getIcon(getIcon: (String) -> Drawable?) =
    getIcon(name)?.let { ClockDrawableWrapper.from(it, metadata) ?: it }

  override fun copyTo(
    component: String,
    newName: String,
    addAppfilter: (String) -> Unit,
    copyRes: (String, String) -> Unit,
  ) {
    addAppfilter(
      "<dynamic-clock drawable=\"${newName}\" " +
        "hourLayerIndex=\"${metadata.hourLayerIndex}\" " +
        "minuteLayerIndex=\"${metadata.minuteLayerIndex}\" " +
        "secondLayerIndex=\"${metadata.secondLayerIndex}\" " +
        "defaultHour=\"${metadata.defaultHour}\" " +
        "defaultMinute=\"${metadata.defaultMinute}\" " +
        "defaultSecond=\"${metadata.defaultSecond}\" />"
    )
    addAppfilter("<item component=\"${component}\" drawable=\"${newName}\"/>")
    copyRes(name, newName)
  }

  override fun toByteArray(): ByteArray =
    ByteArrayOutputStream()
      .also {
        DataOutputStream(it).use {
          it.writeByte(type.ordinal)
          it.writeUTF(name)
          it.writeInt(metadata.hourLayerIndex)
          it.writeInt(metadata.minuteLayerIndex)
          it.writeInt(metadata.secondLayerIndex)
          it.writeInt(metadata.defaultHour)
          it.writeInt(metadata.defaultMinute)
          it.writeInt(metadata.defaultSecond)
        }
      }
      .toByteArray()

  companion object {
    fun from(data: ByteArray) =
      DataInputStream(ByteArrayInputStream(data)).use {
        it.readByte()
        ClockIconEntry(
          it.readUTF(),
          ClockMetadata(
            it.readInt(),
            it.readInt(),
            it.readInt(),
            it.readInt(),
            it.readInt(),
            it.readInt(),
          ),
        )
      }
  }
}

object IconEntryParceler : Parceler<IconEntry> {
  override fun IconEntry.write(parcel: Parcel, flags: Int) =
    toByteArray().let {
      parcel.writeInt(it.size)
      parcel.writeByteArray(it)
    }

  override fun create(parcel: Parcel) =
    ByteArray(parcel.readInt()).let {
      parcel.readByteArray(it)
      IconEntry.from(it)
    }
}
