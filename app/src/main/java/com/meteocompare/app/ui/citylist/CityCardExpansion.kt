package com.meteocompare.app.ui.citylist

import androidx.compose.ui.semantics.SemanticsPropertyKey

/**
 * A single city is expanded on Home. Favorites are stored in insertion order;
 * this reducer only reacts to ID changes, never to forecast refreshes.
 */
internal fun resolveExpandedCityId(
    expandedCityId: String?,
    previousCityIds: List<String>,
    cityIds: List<String>
): String? {
    if (cityIds.isEmpty()) return null
    // On initial load (including after process recreation), prefer the last favorite.
    // When several favorites arrive in one emission, the last appended one wins.
    val previous = previousCityIds.toSet()
    return cityIds.lastOrNull { it !in previous }
        ?: expandedCityId?.takeIf { it in cityIds }
        ?: cityIds.last()
}

internal enum class CityCardTapAction { EXPAND, OPEN_DETAILS }

internal fun cityCardTapAction(cityId: String, expandedCityId: String?): CityCardTapAction =
    if (cityId == expandedCityId) CityCardTapAction.OPEN_DETAILS else CityCardTapAction.EXPAND

internal val CityCardExpandedKey = SemanticsPropertyKey<Boolean>("CityCardExpanded")
internal const val TAG_CITY_CARD_COMPACT_METRICS = "city_card_compact_metrics_"
internal const val TAG_CITY_CARD_COMPACT_AGREEMENT = "city_card_compact_agreement_"
internal const val TAG_CITY_CARD_COMPACT_CONDITION = "city_card_compact_condition_"
internal const val TAG_CITY_CARD_COMPACT_TEMPERATURES = "city_card_compact_temperatures_"
internal const val TAG_CITY_CARD_COMPACT_CONDITION_LABEL = "city_card_compact_condition_label_"
internal const val TAG_CITY_CARD_COMPACT_RAIN = "city_card_compact_rain_"
internal const val TAG_CITY_CARD_COMPACT_WIND = "city_card_compact_wind_"
internal const val TAG_CITY_CARD_COMPACT_CLOUD = "city_card_compact_cloud_"
