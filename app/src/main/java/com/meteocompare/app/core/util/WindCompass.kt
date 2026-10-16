package com.meteocompare.app.core.util

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Meteorological direction: the direction the wind COMES FROM (not where it blows).
 * Eight compass points keep labels legible in dense hourly tables and widgets.
 * Null or invalid directions must never be presented as a measured direction.
 */
private val eastWestCompass = arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
private val englishCompass = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

fun windCardinal(directionDegrees: Int?, locale: Locale): String? {
    val bearing = directionDegrees?.takeIf { it in 0..360 } ?: return null
    val index = ((bearing % 360 + 22.5) / 45.0).toInt() % 8
    return when (locale.language) {
        "fr", "es", "it" -> eastWestCompass[index]  // Ouest / Oeste / Ovest
        else -> englishCompass[index]                 // West / Westen
    }
}

/** Circular rather than arithmetic average: 359° + 1° => north, NOT south.
 * Opposing directions cancel out and have no representative orientation.
 */
fun meanWindDirection(degrees: Iterable<Int?>): Int? {
    val values = degrees.filterNotNull().filter { it in 0..360 }.toList()
    if (values.isEmpty()) return null
    val east = values.sumOf { cos(Math.toRadians(it.toDouble())) }
    val north = values.sumOf { sin(Math.toRadians(it.toDouble())) }
    if (east * east + north * north < 1e-8) return null
    return ((Math.toDegrees(atan2(north, east)).roundToInt() % 360) + 360) % 360
}
