package com.meteocompare.app.ui.localitycompare

import kotlin.math.roundToInt

/** Select a local wall-clock hour on the 24-slot plot; clamp touch and drag. */
internal fun chartHourFromX(x: Float, left: Float, right: Float): Int {
    if (right <= left) return 0
    return (((x - left) / (right - left)).coerceIn(0f, 1f) * 23f).roundToInt()
}
