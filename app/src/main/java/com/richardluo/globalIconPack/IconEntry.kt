package com.richardluo.globalIconPack

data class IconEntry(
    val name: String,
    val type: IconType,
) {
    fun resolveDynamicCalendar(day: Int): IconEntry {
        check(type == IconType.Calendar) { "type is not calendar" }
        return IconEntry("$name${day + 1}", IconType.Normal)
    }
}

enum class IconType {
    Normal,
    Calendar,
}