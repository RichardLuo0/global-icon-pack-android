package com.richardluo.globalIconPack.iconPack.database

import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.iconPack.IconPack
import com.richardluo.globalIconPack.reflect.ClockDrawableWrapper
import com.richardluo.globalIconPack.utils.letAll
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.Calendar

data class IconEntry(
  val name: String,
  val type: IconType,
  val clockMetadata: ClockMetadata? = null,
) : Serializable {

  fun getIcon(ip: IconPack, iconDpi: Int): Drawable? {
    return when (type) {
      IconType.Normal -> ip.getIcon(name, iconDpi)
      IconType.Calendar ->
        ip.getIcon("$name${Calendar.getInstance().get(Calendar.DAY_OF_MONTH)}", iconDpi)
      IconType.Clock ->
        letAll(ip.getIcon(name, iconDpi), clockMetadata) { icon, metadata ->
          ClockDrawableWrapper.from(icon, metadata) ?: icon
        }
    }
  }

  fun toByteArray(): ByteArray =
    ByteArrayOutputStream().also { ObjectOutputStream(it).writeObject(this) }.toByteArray()

  companion object {
    private const val serialVersionUID = 1L

    fun from(data: ByteArray) =
      ObjectInputStream(ByteArrayInputStream(data)).readObject() as IconEntry
  }
}

enum class IconType {
  Normal,
  Calendar,
  Clock,
}
