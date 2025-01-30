package com.richardluo.globalIconPack.iconPack.database

import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.reflect.ClockDrawableWrapper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.Calendar

abstract class IconEntry(val name: String) : Serializable {

  abstract fun getIcon(getIcon: (String) -> Drawable?): Drawable?

  open fun copyTo(
    component: String,
    newName: String,
    xml: StringBuilder,
    copyRes: (String, String) -> Unit,
  ) {}

  fun toByteArray(): ByteArray =
    ByteArrayOutputStream().also { ObjectOutputStream(it).writeObject(this) }.toByteArray()

  companion object {
    private const val serialVersionUID = 1L

    fun from(data: ByteArray) =
      ObjectInputStream(ByteArrayInputStream(data)).readObject() as IconEntry
  }
}

class NormalIconEntry(name: String) : IconEntry(name) {

  override fun getIcon(getIcon: (String) -> Drawable?) = getIcon(name)

  override fun copyTo(
    component: String,
    newName: String,
    xml: StringBuilder,
    copyRes: (String, String) -> Unit,
  ) {
    xml.append("<item component=\"${component}\" drawable=\"${newName}\"/>")
    copyRes(name, newName)
  }

  companion object {
    private const val serialVersionUID = 1L
  }
}

class CalendarIconEntry(name: String) : IconEntry(name) {

  override fun getIcon(getIcon: (String) -> Drawable?) =
    getIcon("$name${Calendar.getInstance().get(Calendar.DAY_OF_MONTH)}")

  override fun copyTo(
    component: String,
    newName: String,
    xml: StringBuilder,
    copyRes: (String, String) -> Unit,
  ) {
    xml.append("<calendar component=\"${component}\" prefix=\"${newName}_\"/>")
    (1..31).mapNotNull { copyRes("$name${it}", "${newName}_$it") }
  }

  companion object {
    private const val serialVersionUID = 1L
  }
}

class ClockIconEntry(name: String, private val metadata: ClockMetadata) : IconEntry(name) {

  override fun getIcon(getIcon: (String) -> Drawable?) =
    getIcon(name)?.let { ClockDrawableWrapper.from(it, metadata) ?: it }

  override fun copyTo(
    component: String,
    newName: String,
    xml: StringBuilder,
    copyRes: (String, String) -> Unit,
  ) {
    xml.append(
      "<dynamic-clock drawable=\"${newName}\" " +
        "hourLayerIndex=\"${metadata.hourLayerIndex}\" " +
        "minuteLayerIndex=\"${metadata.minuteLayerIndex}\" " +
        "secondLayerIndex=\"${metadata.secondLayerIndex}\" " +
        "defaultHour=\"${metadata.defaultHour}\" " +
        "defaultMinute=\"${metadata.defaultMinute}\" " +
        "defaultSecond=\"${metadata.defaultSecond}\" />"
    )
    xml.append("<item component=\"${component}\" drawable=\"${newName}\"/>")
    copyRes(name, newName)
  }

  companion object {
    private const val serialVersionUID = 1L
  }
}
