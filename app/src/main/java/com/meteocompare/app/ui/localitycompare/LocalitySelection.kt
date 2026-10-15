package com.meteocompare.app.ui.localitycompare

/** Keep two cities by default and never retain deleted favorites. */
internal fun restoreComparedCities(availableIds: List<String>, selectedIds: List<String>): List<String> {
    val valid = selectedIds.distinct().filter { it in availableIds }.take(3)
    return if (valid.size < 2) {
        valid + availableIds.distinct().filterNot { it in valid }.take(2 - valid.size)
    } else valid
}

/** Users can add a third city and then replace one of the first two. */
internal fun toggleComparedCity(current: List<String>, id: String): List<String> = when {
    id in current && current.size > 2 -> current - id
    id in current -> current
    current.size < 3 -> current + id
    else -> current
}
