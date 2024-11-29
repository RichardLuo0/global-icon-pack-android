package com.richardluo.globalIconPack

import android.graphics.drawable.Drawable
import com.richardluo.globalIconPack.reflect.ClockDrawableWrapper
import java.util.Calendar

data class IconEntry(
  val name: String,
  val type: IconType,
  private val clockMetadata: ClockMetadata? = null,
) {

  fun getIcon(cip: CustomIconPack, iconDpi: Int): Drawable? {
    return when (type) {
      IconType.Normal -> cip.getIcon(name, iconDpi)
      IconType.Calendar ->
        cip.getIcon("$name${Calendar.getInstance().get(Calendar.DAY_OF_MONTH)}", iconDpi)
      IconType.Clock ->
        letAll(cip.getIcon(name, iconDpi), clockMetadata) { icon, metadata ->
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
