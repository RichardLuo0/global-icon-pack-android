package com.richardluo.globalIconPack.iconPack

import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.reflect.ClockDrawableWrapper
import com.richardluo.globalIconPack.utils.letAll
import java.util.Calendar

data class IconEntry(
  val name: String,
  val type: IconType,
  private val clockMetadata: ClockMetadata? = null,
) {

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
}

enum class IconType {
  Normal,
  Calendar,
  Clock,
}
